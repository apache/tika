package org.apache.tika.pipes.async;

import org.apache.commons.io.FileUtils;
import org.apache.tika.metadata.serialization.JsonFetchEmitTuple;
import org.apache.tika.pipes.fetchiterator.FetchEmitTuple;
import org.apache.tika.pipes.fetchiterator.FetchIterator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.io.IOException;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

public class AsyncProcessor implements Closeable {

    private static final Logger LOG = LoggerFactory.getLogger(AsyncProcessor.class);
    protected static String TIKA_ASYNC_JDBC_KEY = "TIKA_ASYC_JDBC_KEY";
    protected static String TIKA_ASYNC_CONFIG_FILE_KEY = "TIKA_ASYNC_CONFIG_FILE_KEY";
    private final Path tikaConfigPath;
    private AsyncConfig asyncConfig;
    private final ArrayBlockingQueue<FetchEmitTuple> queue;
    private final Connection connection;
    private int finishedThreads = 0;
    private final int totalThreads;
    private ExecutorService executorService;
    private ExecutorCompletionService<Integer> executorCompletionService;

    public static AsyncProcessor build(Path tikaConfigPath) throws AsyncRuntimeException {
        try {
            AsyncProcessor processor = new AsyncProcessor(tikaConfigPath);

            processor.init();
            return processor;
        } catch (SQLException|IOException e) {
            throw new AsyncRuntimeException(e);
        }
    }

    private AsyncProcessor(Path tikaConfigPath) throws SQLException, IOException {
        this.tikaConfigPath = tikaConfigPath;
        this.asyncConfig = AsyncConfig.load(tikaConfigPath);
        this.queue = new ArrayBlockingQueue<>(asyncConfig.getQueueSize());
        this.connection = DriverManager.getConnection(asyncConfig.getJdbcString());
        this.totalThreads = asyncConfig.getMaxConsumers() + 2;
    }

    public synchronized boolean offer (
            List<FetchEmitTuple> fetchEmitTuples, long offerMs) throws
            AsyncRuntimeException, InterruptedException {
        if (queue == null) {
            throw new IllegalStateException("queue hasn't been initialized yet.");
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
    public synchronized boolean checkActive()
            throws AsyncRuntimeException, InterruptedException {
        Future<Integer> future = executorCompletionService.poll();
        if (future != null) {
            try {
                future.get();
            } catch (ExecutionException e) {
                throw new AsyncRuntimeException(e);
            }
            finishedThreads++;
        }
        if (finishedThreads == totalThreads) {
            return false;
        }
        return true;
    }

    private void init() throws SQLException {

        setupTables();

        executorService = Executors.newFixedThreadPool(
                totalThreads);
        executorCompletionService =
                new ExecutorCompletionService<>(executorService);

        AsyncTaskEnqueuer enqueuer = new AsyncTaskEnqueuer(queue, connection);

        executorCompletionService.submit(enqueuer);
        executorCompletionService.submit(new AssignmentManager(connection, enqueuer));
        //executorCompletionService.submit(new )
        for (int i = 0; i < asyncConfig.getMaxConsumers(); i++) {
            executorCompletionService.submit(new AsyncWorker(connection,
                    asyncConfig.getJdbcString(), i, tikaConfigPath));
        }
    }

    private void setupTables() throws SQLException {

        String sql = "create table parse_queue " +
                "(id bigint auto_increment primary key," +
                "status tinyint," +//byte
                "worker_id integer," +
                "retry smallint," + //short
                "time_stamp timestamp," +
                "json varchar(64000))";
        try (Statement st = connection.createStatement()) {
            st.execute(sql);
        }
        //no clear benefit to creating an index on timestamp
//        sql = "CREATE INDEX IF NOT EXISTS status_timestamp on status (time_stamp)";
        sql = "create table workers (worker_id int primary key, status tinyint)";
        try (Statement st = connection.createStatement()) {
            st.execute(sql);
        }

        sql = "create table error_log (task_id bigint, " +
                "fetch_key varchar(10000)," +
                "time_stamp timestamp," +
                "retry integer," +
                "error_code tinyint)";
        try (Statement st = connection.createStatement()) {
            st.execute(sql);
        }

        sql = "create table emits (" +
                "emit_id bigint auto_increment primary key, " +
                "status tinyint, " +
                "worker_id integer, " +
                "time_stamp timestamp, " +
                "uncompressed_size bigint, " +
                "bytes blob)";
        try (Statement st = connection.createStatement()) {
            st.execute(sql);
        }
    }

    public void shutdownNow() throws IOException, AsyncRuntimeException {
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
     * This is a blocking close.  If you need to shutdown immediately,
     * try {@link #shutdownNow()}.
     * @throws IOException
     */
    @Override
    public void close() throws IOException {
        try {
            for (int i = 0; i < asyncConfig.getMaxConsumers(); i++) {
                try {
                    //blocking
                    queue.put(FetchIterator.COMPLETED_SEMAPHORE);
                } catch (InterruptedException e) {
                    //swallow
                }
            }
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
        } finally {
            executorService.shutdownNow();
            //close down processes and db
            if (asyncConfig.getTempDBDir() != null) {
                FileUtils.deleteDirectory(asyncConfig.getTempDBDir().toFile());
            }
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

        AsyncTaskEnqueuer(ArrayBlockingQueue<FetchEmitTuple> queue,
                          Connection connection) throws SQLException {
            this.queue = queue;
            this.connection = connection;
            String sql = "insert into parse_queue (status, time_stamp, worker_id, retry, json) " +
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
                    //TODO -- fix this
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

        private void insert(FetchEmitTuple t, List<Integer> workers) throws IOException, SQLException {
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
        private final Random random = new Random();


        public AssignmentManager(Connection connection, AsyncTaskEnqueuer enqueuer) throws SQLException {
            this.connection = connection;
            this.enqueuer = enqueuer;
            //this gets workers and # of tasks in desc order of number of tasks
            String sql = "select w.worker_id, p.cnt " +
                    "from workers w " +
                    "left join (select worker_id, count(1) as cnt from parse_queue " +
                    "where status=0 group by worker_id)" +
                    " p on p.worker_id=w.worker_id order by p.cnt desc";
            getQueueDistribution = connection.prepareStatement(sql);
            //find workers that have assigned tasks but are not in the
            //workers table
            sql = "select p.worker_id, count(1) as cnt from parse_queue p " +
                    "left join workers w on p.worker_id=w.worker_id " +
                    "where w.worker_id is null group by p.worker_id";
            findMissingWorkers = connection.prepareStatement(sql);

            sql = "update parse_queue set worker_id=? where worker_id=?";
            allocateNonworkersToWorkers = connection.prepareStatement(sql);

            //current strategy reallocate tasks from longest queue to shortest
            //TODO: might consider randomly shuffling or other algorithms
            sql = "update parse_queue set worker_id= ? where id in " +
                    "(select id from parse_queue where " +
                    "worker_id = ? and " +
                    "rand() < 0.8 " +
                    "and status=0 for update)";
            reallocate = connection.prepareStatement(sql);

            sql = "select count(1) from parse_queue where status="
                    + AsyncWorkerProcess.TASK_STATUS_CODES.AVAILABLE.ordinal();
            countAvailableTasks = connection.prepareStatement(sql);

            sql = "update workers set status="+
                    AsyncWorkerProcess.WORKER_STATUS_CODES.SHOULD_SHUTDOWN.ordinal() +
                " where worker_id = ?";
            shutdownWorker = connection.prepareStatement(sql);
        }

        @Override
        public Integer call() throws Exception {

            while (true) {
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

        private void notifyWorkers() throws SQLException {
            for (int workerId : getActiveWorkers(connection)) {
                shutdownWorker.clearParameters();
                shutdownWorker.setInt(1, workerId);
                shutdownWorker.execute();
            }
        }

        private boolean isComplete() throws SQLException {
            if (!enqueuer.isComplete) {
                return false;
            }
            try (ResultSet rs = countAvailableTasks.executeQuery()) {
                while (rs.next()) {
                    return rs.getInt(1) == 0;
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

        private void reallocateFromMissingWorkers(List<Integer> missingWorkers) throws SQLException {

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
                LOG.debug("allocating missing working ({}) to ({})",
                        missing, active);
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

    private static List<Integer> getActiveWorkers(Connection connection) throws SQLException {
        PreparedStatement findActiveWorkers = connection.prepareStatement(
                "select worker_id from workers");
        List<Integer> workers = new ArrayList<>();
        try (ResultSet rs = findActiveWorkers.executeQuery()) {
            while (rs.next()) {
                workers.add(rs.getInt(1));
            }
        }
        return workers;
    }
}
