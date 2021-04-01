/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.tika.pipes.async;

import java.io.Closeable;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.tika.metadata.serialization.JsonFetchEmitTuple;
import org.apache.tika.pipes.fetchiterator.FetchEmitTuple;
import org.apache.tika.pipes.fetchiterator.FetchIterator;

public class AsyncProcessor implements Closeable {

    private static final Logger LOG = LoggerFactory.getLogger(AsyncProcessor.class);
    protected static String TIKA_ASYNC_JDBC_KEY = "TIKA_ASYC_JDBC_KEY";
    protected static String TIKA_ASYNC_CONFIG_FILE_KEY = "TIKA_ASYNC_CONFIG_FILE_KEY";
    private final Path tikaConfigPath;
    private final ArrayBlockingQueue<FetchEmitTuple> queue;
    private final Connection connection;
    private final int totalThreads;
    private final AsyncConfig asyncConfig;
    private PreparedStatement emittersCanShutdown;
    private volatile boolean isShuttingDown = false;
    private AssignmentManager assignmentManager;
    private int finishedThreads = 0;
    private ExecutorService executorService;
    private ExecutorCompletionService<Integer> executorCompletionService;

    private AsyncProcessor(Path tikaConfigPath) throws SQLException, IOException {
        this.tikaConfigPath = tikaConfigPath;
        this.asyncConfig = AsyncConfig.load(tikaConfigPath);
        this.queue = new ArrayBlockingQueue<>(asyncConfig.getQueueSize());
        this.connection = DriverManager.getConnection(asyncConfig.getJdbcString());
        this.totalThreads = asyncConfig.getNumWorkers() + asyncConfig.getNumEmitters() +
                2;//assignment manager and enqueuer threads
    }

    public static AsyncProcessor build(Path tikaConfigPath) throws AsyncRuntimeException {
        try {
            AsyncProcessor processor = new AsyncProcessor(tikaConfigPath);
            processor.init();
            return processor;
        } catch (SQLException | IOException e) {
            throw new AsyncRuntimeException(e);
        }
    }

    private static List<Integer> getActiveWorkers(Connection connection) throws SQLException {
        PreparedStatement findActiveWorkers =
                connection.prepareStatement("select worker_id from workers");
        List<Integer> workers = new ArrayList<>();
        try (ResultSet rs = findActiveWorkers.executeQuery()) {
            while (rs.next()) {
                workers.add(rs.getInt(1));
            }
        }
        return workers;
    }

    public synchronized boolean offer(List<FetchEmitTuple> fetchEmitTuples, long offerMs)
            throws AsyncRuntimeException, InterruptedException {
        if (queue == null) {
            throw new IllegalStateException("queue hasn't been initialized yet.");
        } else if (isShuttingDown) {
            throw new IllegalStateException(
                    "Can't call offer after calling close() or " + "shutdownNow()");
        }
        long start = System.currentTimeMillis();
        long elapsed = System.currentTimeMillis() - start;
        while (elapsed < offerMs) {
            checkActive();
            if (queue.remainingCapacity() > fetchEmitTuples.size()) {
                try {
                    queue.addAll(fetchEmitTuples);
                    return true;
                } catch (IllegalStateException e) {
                    //swallow
                }
            }
            Thread.sleep(100);
            elapsed = System.currentTimeMillis() - start;
        }
        return false;
    }

    public synchronized boolean offer(FetchEmitTuple t, long offerMs)
            throws AsyncRuntimeException, InterruptedException {
        if (queue == null) {
            throw new IllegalStateException("queue hasn't been initialized yet.");
        } else if (isShuttingDown) {
            throw new IllegalStateException(
                    "Can't call offer after calling close() or " + "shutdownNow()");
        }
        checkActive();
        return queue.offer(t, offerMs, TimeUnit.MILLISECONDS);
    }

    /**
     * This polls the executorcompletionservice to check for execution exceptions
     * and to make sure that some threads are still active.
     *
     * @return
     * @throws AsyncRuntimeException
     * @throws InterruptedException
     */
    public synchronized boolean checkActive() throws AsyncRuntimeException, InterruptedException {
        Future<Integer> future = executorCompletionService.poll();
        if (future != null) {
            try {
                future.get();
            } catch (ExecutionException e) {
                throw new AsyncRuntimeException(e);
            }
            finishedThreads++;
        }
        return finishedThreads != totalThreads;
    }

    private void init() throws SQLException {

        setupTables();
        String sql = "update emitters set status=" +
                AsyncWorkerProcess.WORKER_STATUS_CODES.CAN_SHUTDOWN.ordinal();
        this.emittersCanShutdown = connection.prepareStatement(sql);
        executorService = Executors.newFixedThreadPool(totalThreads);
        executorCompletionService = new ExecutorCompletionService<>(executorService);

        AsyncTaskEnqueuer taskEnqueuer = new AsyncTaskEnqueuer(queue, connection);

        executorCompletionService.submit(taskEnqueuer);

        List<AsyncWorker> workers = buildWorkers(connection, asyncConfig, tikaConfigPath);
        int maxRetries = 0;
        for (AsyncWorker worker : workers) {
            if (worker.getMaxRetries() > maxRetries) {
                maxRetries = worker.getMaxRetries();
            }
            executorCompletionService.submit(worker);
        }
        assignmentManager = new AssignmentManager(connection, taskEnqueuer, maxRetries);
        executorCompletionService.submit(assignmentManager);
        for (int i = 0; i < asyncConfig.getNumEmitters(); i++) {
            executorCompletionService
                    .submit(new AsyncEmitter(connection, asyncConfig.getJdbcString(),
                            asyncConfig.getNumWorkers() + i, tikaConfigPath));
        }
    }

    private List<AsyncWorker> buildWorkers(Connection connection, AsyncConfig asyncConfig,
                                           Path tikaConfigPath) throws SQLException {
        //TODO -- make these workers configurable via the tika config, e.g. max retries
        //and jvm args, etc.
        List<AsyncWorker> workers = new ArrayList<>();
        for (int i = 0; i < asyncConfig.getNumWorkers(); i++) {
            workers.add(
                    new AsyncWorker(connection, asyncConfig.getJdbcString(), i, tikaConfigPath));
        }
        return workers;
    }

    private void setupTables() throws SQLException {

        String sql = "create table task_queue " + "(id bigint auto_increment primary key," +
                "status tinyint," + //byte
                "worker_id integer," + "retry smallint," + //short
                "time_stamp timestamp," +
                "json varchar(64000))";//this is the AsyncTask ... not the emit data!
        try (Statement st = connection.createStatement()) {
            st.execute(sql);
        }
        sql = "create table workers (worker_id int primary key, status tinyint)";
        try (Statement st = connection.createStatement()) {
            st.execute(sql);
        }

        sql = "create table emitters (emitter_id int primary key, status tinyint)";
        try (Statement st = connection.createStatement()) {
            st.execute(sql);
        }

        sql = "create table error_log (task_id bigint, " + "fetch_key varchar(10000)," +
                "time_stamp timestamp," + "retry integer," + "error_code tinyint)";

        try (Statement st = connection.createStatement()) {
            st.execute(sql);
        }

        sql = "create table emits (" + "id bigint primary key, " + "time_stamp timestamp, " +
                "uncompressed_size bigint, " + "bytes blob)";
        try (Statement st = connection.createStatement()) {
            st.execute(sql);
        }
    }

    public void shutdownNow() throws IOException, AsyncRuntimeException {
        isShuttingDown = true;
        try {
            executorService.shutdownNow();
        } finally {
            //close down processes and db
            if (asyncConfig.getTempDBDir() != null) {
                FileUtils.deleteDirectory(asyncConfig.getTempDBDir().toFile());
            }
        }
    }

    /**
     * This is a blocking close.  It will wait for all tasks successfully submitted before this
     * call to close() to complete before closing.  If you need to shutdown immediately, try
     * {@link #shutdownNow()}.
     *
     * @throws IOException
     */
    @Override
    public void close() throws IOException {
        isShuttingDown = true;
        try {
            completeAndShutdown();
        } catch (SQLException | InterruptedException e) {
            throw new IOException(e);
        } finally {
            executorService.shutdownNow();
            SQLException ex = null;
            try {
                connection.close();
            } catch (SQLException e) {
                ex = e;
            }
            //close down processes and db
            if (asyncConfig.getTempDBDir() != null) {
                FileUtils.deleteDirectory(asyncConfig.getTempDBDir().toFile());
            }
            if (ex != null) {
                throw new IOException(ex);
            }
        }
    }

    //this will block until everything finishes
    private void completeAndShutdown() throws SQLException, InterruptedException {

        //blocking...notify taskEnqueuer
        queue.put(FetchIterator.COMPLETED_SEMAPHORE);

        //wait for assignmentManager to finish
        //it will only complete after the task enqueuer has completed
        //and there are no more parse tasks available, selected or in process
        while (!assignmentManager.hasCompletedTasks()) {
            Thread.sleep(100);
        }

        emittersCanShutdown.executeUpdate();

        //wait for emitters to finish
        long start = System.currentTimeMillis();
        long elapsed = System.currentTimeMillis() - start;
        try {
            boolean isActive = checkActive();
            while (isActive) {
                isActive = checkActive();
                elapsed = System.currentTimeMillis();
            }
        } catch (InterruptedException e) {
            return;
        }
    }

    //this reads fetchemittuples from the queue and inserts them in the db
    //for the workers to read
    private static class AsyncTaskEnqueuer implements Callable<Integer> {
        private final PreparedStatement insert;

        private final ArrayBlockingQueue<FetchEmitTuple> queue;
        private final Connection connection;
        private final Random random = new Random();

        private volatile boolean isComplete = false;

        AsyncTaskEnqueuer(ArrayBlockingQueue<FetchEmitTuple> queue, Connection connection)
                throws SQLException {
            this.queue = queue;
            this.connection = connection;
            String sql = "insert into task_queue (status, time_stamp, worker_id, retry, json) " +
                    "values (?,CURRENT_TIMESTAMP(),?,?,?)";
            insert = connection.prepareStatement(sql);
        }

        @Override
        public Integer call() throws Exception {
            List<Integer> workers = new ArrayList<>();
            while (true) {
                FetchEmitTuple t = queue.poll(1, TimeUnit.SECONDS);
                LOG.debug("enqueing to db " + t);
                if (t == null) {
                    //log.trace?
                } else if (t == FetchIterator.COMPLETED_SEMAPHORE) {
                    isComplete = true;
                    return 1;
                } else {
                    long start = System.currentTimeMillis();
                    long elapsed = System.currentTimeMillis() - start;
                    //TODO -- fix this -- this loop waits for workers to register
                    while (workers.size() == 0 && elapsed < 600000) {
                        workers = getActiveWorkers(connection);
                        Thread.sleep(100);
                        elapsed = System.currentTimeMillis() - start;
                    }
                    insert(t, workers);
                }
            }
        }

        boolean isComplete() {
            return isComplete;
        }

        private void insert(FetchEmitTuple t, List<Integer> workers)
                throws IOException, SQLException {
            int workerId = workers.size() == 1 ? workers.get(0) :
                    workers.get(random.nextInt(workers.size()));
            insert.clearParameters();
            insert.setByte(1, (byte) AsyncWorkerProcess.TASK_STATUS_CODES.AVAILABLE.ordinal());
            insert.setInt(2, workerId);
            insert.setShort(3, (short) 0);
            insert.setString(4, JsonFetchEmitTuple.toJson(t));
            insert.execute();
        }
    }

    private static class AssignmentManager implements Callable {

        private final Connection connection;
        private final AsyncTaskEnqueuer enqueuer;
        private final PreparedStatement getQueueDistribution;
        private final PreparedStatement findMissingWorkers;
        private final PreparedStatement allocateNonworkersToWorkers;
        private final PreparedStatement reallocate;
        private final PreparedStatement countAvailableTasks;
        private final PreparedStatement shutdownWorker;
        private final PreparedStatement findMaxRetrieds;
        private final PreparedStatement logMaxRetrieds;
        private final PreparedStatement removeMaxRetrieds;
        private final Random random = new Random();
        private final int maxRetries;
        private volatile boolean hasCompleted = false;


        public AssignmentManager(Connection connection, AsyncTaskEnqueuer enqueuer, int maxRetries)
                throws SQLException {
            this.connection = connection;
            this.enqueuer = enqueuer;
            this.maxRetries = maxRetries;
            //this gets workers and # of tasks in desc order of number of tasks
            String sql = "select w.worker_id, p.cnt " + "from workers w " +
                    "left join (select worker_id, count(1) as cnt from task_queue " +
                    "where status=" + AsyncWorkerProcess.TASK_STATUS_CODES.AVAILABLE.ordinal() +
                    " group by worker_id)" + " p on p.worker_id=w.worker_id order by p.cnt desc";
            getQueueDistribution = connection.prepareStatement(sql);
            //find workers that have assigned tasks but are not in the
            //workers table
            sql = "select p.worker_id, count(1) as cnt from task_queue p " +
                    "left join workers w on p.worker_id=w.worker_id " +
                    "where w.worker_id is null group by p.worker_id";
            findMissingWorkers = connection.prepareStatement(sql);

            sql = "update task_queue set worker_id=? where worker_id=?";
            allocateNonworkersToWorkers = connection.prepareStatement(sql);

            //current strategy reallocate tasks from longest queue to shortest
            //TODO: might consider randomly shuffling or other algorithms
            sql = "update task_queue set worker_id= ? where id in " +
                    "(select id from task_queue where " + "worker_id = ? and " + "rand() < 0.8 " +
                    "and status=0 for update)";
            reallocate = connection.prepareStatement(sql);

            //get those tasks that are in the parse phase
            //if they are selected or in process, it is possible that
            //they'll need to be retried.  So, include all statuses
            //meaning that the parse has not completed.
            sql = "select count(1) from task_queue where status in (" +
                    AsyncWorkerProcess.TASK_STATUS_CODES.AVAILABLE.ordinal() + ", " +
                    AsyncWorkerProcess.TASK_STATUS_CODES.SELECTED.ordinal() + ", " +
                    AsyncWorkerProcess.TASK_STATUS_CODES.IN_PROCESS.ordinal() + ")";
            countAvailableTasks = connection.prepareStatement(sql);

            sql = "update workers set status=" +
                    AsyncWorkerProcess.WORKER_STATUS_CODES.SHOULD_SHUTDOWN.ordinal() +
                    " where worker_id = ?";
            shutdownWorker = connection.prepareStatement(sql);

            sql = "select id, retry, json from task_queue where retry >=" + maxRetries;
            findMaxRetrieds = connection.prepareStatement(sql);

            sql = "insert into error_log (task_id, fetch_key, time_stamp, retry, error_code)" +
                    "values (?,?,CURRENT_TIMESTAMP(), ?," +
                    AsyncWorkerProcess.ERROR_CODES.MAX_RETRIES.ordinal() + ")";
            logMaxRetrieds = connection.prepareStatement(sql);

            sql = "delete from task_queue where id=?";
            removeMaxRetrieds = connection.prepareStatement(sql);
        }

        protected boolean hasCompletedTasks() {
            return hasCompleted;
        }

        @Override
        public Integer call() throws Exception {

            while (true) {
                removeMaxRetrieds();
                List<Integer> missingWorkers = getMissingWorkers();
                reallocateFromMissingWorkers(missingWorkers);
                redistribute();
                if (isComplete()) {
                    notifyWorkers();
                    return 1;
                }
                Thread.sleep(200);
            }
        }

        private void removeMaxRetrieds() throws SQLException {
            Set<Long> toRemove = new HashSet<>();
            try (ResultSet rs = findMaxRetrieds.executeQuery()) {
                while (rs.next()) {
                    long id = rs.getLong(1);
                    String json = rs.getString(2);
                    int retries = rs.getInt(3);
                    toRemove.add(id);
                    FetchEmitTuple t;
                    try (Reader reader = new StringReader(json)) {
                        t = JsonFetchEmitTuple.fromJson(reader);
                    } catch (Exception e) {
                        e.printStackTrace();
                        //need to log this in the error_logs table
                        continue;
                    }
                    logMaxRetrieds.clearParameters();
                    logMaxRetrieds.setLong(1, id);
                    logMaxRetrieds.setString(2, t.getFetchKey().getFetchKey());
                    logMaxRetrieds.setInt(3, retries);
                    logMaxRetrieds.addBatch();
                }
            }
            logMaxRetrieds.executeBatch();

            for (Long id : toRemove) {
                removeMaxRetrieds.clearParameters();
                removeMaxRetrieds.setLong(1, id);
                removeMaxRetrieds.addBatch();
            }
            removeMaxRetrieds.executeBatch();
        }

        private void notifyWorkers() throws SQLException {
            for (int workerId : getActiveWorkers(connection)) {
                shutdownWorker.clearParameters();
                shutdownWorker.setInt(1, workerId);
                shutdownWorker.execute();
            }
        }

        private boolean isComplete() throws SQLException {
            if (hasCompleted) {
                return hasCompleted;
            }
            if (!enqueuer.isComplete) {
                return false;
            }
            try (ResultSet rs = countAvailableTasks.executeQuery()) {
                while (rs.next()) {
                    int availTasks = rs.getInt(1);
                    if (availTasks == 0) {
                        hasCompleted = true;
                        return true;
                    }
                }
            }
            return false;
        }

        private void redistribute() throws SQLException {
            //parallel lists of workerid = task queue size
            List<Integer> workerIds = new ArrayList<>();
            List<Integer> queueSize = new ArrayList<>();
            int totalTasks = 0;

            try (ResultSet rs = getQueueDistribution.executeQuery()) {
                while (rs.next()) {
                    int workerId = rs.getInt(1);
                    int numTasks = rs.getInt(2);
                    workerIds.add(workerId);
                    queueSize.add(numTasks);
                    LOG.debug("workerId: ({}) numTasks: ({})", workerId, numTasks);
                    totalTasks += numTasks;
                }
            }
            if (workerIds.size() == 0) {
                return;
            }
            int averagePerWorker = Math.round((float) totalTasks / (float) workerIds.size());
            int midPoint = Math.round((float) queueSize.size() / 2) + 1;
            for (int i = queueSize.size() - 1, j = 0; i > midPoint && j < midPoint; i--, j++) {
                int shortestQueue = queueSize.get(i);
                int longestQueue = queueSize.get(j);
                if ((shortestQueue < 5 && longestQueue > 5) ||
                        longestQueue > 5 && longestQueue > (int) (1.5 * averagePerWorker)) {
                    int shortestQueueWorker = workerIds.get(i);
                    int longestQueueWorker = workerIds.get(j);
                    reallocate.clearParameters();
                    reallocate.setLong(1, shortestQueueWorker);
                    reallocate.setLong(2, longestQueueWorker);
                    reallocate.execute();
                }
            }

        }

        private void reallocateFromMissingWorkers(List<Integer> missingWorkers)
                throws SQLException {

            if (missingWorkers.size() == 0) {
                return;
            }

            List<Integer> activeWorkers = getActiveWorkers(connection);
            if (activeWorkers.size() == 0) {
                return;
            }

            for (int missing : missingWorkers) {
                int active = activeWorkers.get(random.nextInt(activeWorkers.size()));
                allocateNonworkersToWorkers.clearParameters();
                allocateNonworkersToWorkers.setInt(1, active);
                allocateNonworkersToWorkers.setInt(2, missing);
                allocateNonworkersToWorkers.execute();
                LOG.debug("allocating missing working ({}) to ({})", missing, active);
            }
        }

        private List<Integer> getMissingWorkers() throws SQLException {
            List<Integer> missingWorkers = new ArrayList<>();
            try (ResultSet rs = findMissingWorkers.executeQuery()) {
                while (rs.next()) {
                    int workerId = rs.getInt(1);
                    missingWorkers.add(workerId);
                    LOG.debug("Worker ({}) no longer active", workerId);
                }
            }
            return missingWorkers;
        }
    }
}
