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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.tika.config.TikaConfig;
import org.apache.tika.metadata.serialization.JsonFetchEmitTuple;
import org.apache.tika.pipes.fetchiterator.EmptyFetchIterator;
import org.apache.tika.pipes.fetchiterator.FetchEmitTuple;
import org.apache.tika.pipes.fetchiterator.FetchIterator;

public class AsyncCli {

    private static final Logger LOG = LoggerFactory.getLogger(AsyncCli.class);

    public static void main(String[] args) throws Exception {
        Path configPath = Paths.get(args[0]);
        int maxConsumers = 20;
        AsyncCli asyncCli = new AsyncCli();
        Path dbDir = Files.createTempDirectory("tika-async-db-");
        try {
            asyncCli.execute(dbDir, configPath, maxConsumers);
        } finally {
            FileUtils.deleteDirectory(dbDir.toFile());
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

    private void execute(Path dbDir, Path configPath, int maxConsumers) throws Exception {
        TikaConfig tikaConfig = new TikaConfig(configPath);

        String connectionString = setupTables(dbDir);

        ExecutorService executorService = Executors.newFixedThreadPool(maxConsumers + 3);
        ExecutorCompletionService<Integer> executorCompletionService =
                new ExecutorCompletionService<>(executorService);

        try (Connection connection = DriverManager.getConnection(connectionString)) {
            FetchIterator fetchIterator = tikaConfig.getFetchIterator();
            if (fetchIterator instanceof EmptyFetchIterator) {
                throw new IllegalArgumentException("can't have empty fetch iterator");
            }
            ArrayBlockingQueue<FetchEmitTuple> q =
                    new ArrayBlockingQueue<>(10000);//fetchIterator.init(maxConsumers);
            AsyncTaskEnqueuer enqueuer = new AsyncTaskEnqueuer(q, connection);
            executorCompletionService.submit(fetchIterator);
            executorCompletionService.submit(enqueuer);
            executorCompletionService.submit(new AssignmentManager(connection, enqueuer));

            for (int i = 0; i < maxConsumers; i++) {
                executorCompletionService
                        .submit(new AsyncWorker(connection, connectionString, i, configPath));
            }
            int completed = 0;
            while (completed < maxConsumers + 3) {
                Future<Integer> future = executorCompletionService.take();
                if (future != null) {
                    int val = future.get();
                    completed++;
                    LOG.debug("finished " + val);
                }
            }
        } finally {
            executorService.shutdownNow();
        }
    }

    private String setupTables(Path dbDir) throws SQLException {
        Path dbFile = dbDir.resolve("tika-async");
        String url = "jdbc:h2:file:" + dbFile.toAbsolutePath().toString() + ";AUTO_SERVER=TRUE";
        Connection connection = DriverManager.getConnection(url);

        String sql = "create table task_queue " + "(id bigint auto_increment primary key," +
                "status tinyint," + //byte
                "worker_id integer," + "retry smallint," + //short
                "time_stamp timestamp," + "json varchar(64000))";
        connection.createStatement().execute(sql);
        //no clear benefit to creating an index on timestamp
//        sql = "CREATE INDEX IF NOT EXISTS status_timestamp on status (time_stamp)";
        sql = "create table workers (worker_id int primary key)";
        connection.createStatement().execute(sql);

        sql = "create table workers_shutdown (worker_id int primary key)";
        connection.createStatement().execute(sql);

        sql = "create table error_log (task_id bigint, " + "fetch_key varchar(10000)," +
                "time_stamp timestamp," + "retry integer," + "error_code tinyint)";
        connection.createStatement().execute(sql);

        return url;
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
        private final PreparedStatement insertWorkersShutdown;
        private final Random random = new Random();


        public AssignmentManager(Connection connection, AsyncTaskEnqueuer enqueuer)
                throws SQLException {
            this.connection = connection;
            this.enqueuer = enqueuer;
            //this gets workers and # of tasks in desc order of number of tasks
            String sql = "select w.worker_id, p.cnt " + "from workers w " +
                    "left join (select worker_id, count(1) as cnt from task_queue " +
                    "where status=0 group by worker_id)" +
                    " p on p.worker_id=w.worker_id order by p.cnt desc";
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

            sql = "select count(1) from task_queue where status=" +
                    AsyncWorkerProcess.TASK_STATUS_CODES.AVAILABLE.ordinal();
            countAvailableTasks = connection.prepareStatement(sql);

            sql = "insert into workers_shutdown (worker_id) values (?)";
            insertWorkersShutdown = connection.prepareStatement(sql);
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
                Thread.sleep(100);
            }
        }

        private void notifyWorkers() throws SQLException {
            for (int workerId : getActiveWorkers(connection)) {
                insertWorkersShutdown.clearParameters();
                insertWorkersShutdown.setInt(1, workerId);
                insertWorkersShutdown.execute();
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
