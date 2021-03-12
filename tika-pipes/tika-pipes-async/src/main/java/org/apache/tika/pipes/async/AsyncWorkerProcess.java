package org.apache.tika.pipes.async;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import net.jpountz.lz4.LZ4Factory;
import org.apache.tika.config.TikaConfig;
import org.apache.tika.exception.EncryptedDocumentException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.metadata.serialization.JsonFetchEmitTuple;
import org.apache.tika.metadata.serialization.JsonMetadata;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.parser.RecursiveParserWrapper;
import org.apache.tika.pipes.emitter.EmitKey;
import org.apache.tika.pipes.fetcher.FetchKey;
import org.apache.tika.pipes.fetchiterator.FetchEmitTuple;
import org.apache.tika.sax.BasicContentHandlerFactory;
import org.apache.tika.sax.RecursiveParserWrapperHandler;
import org.apache.tika.utils.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.SAXException;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.io.StringReader;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.apache.tika.pipes.async.AsyncTask.SHUTDOWN_SEMAPHORE;
import static org.apache.tika.pipes.async.AsyncWorker.prepareInsertErrorLog;
import static org.apache.tika.pipes.async.AsyncWorker.prepareReset;
import static org.apache.tika.pipes.async.AsyncWorker.reportAndReset;

public class AsyncWorkerProcess {

    enum TASK_STATUS_CODES {
        AVAILABLE,
        SELECTED,
        IN_PROCESS,
        AVAILABLE_EMIT,
        SELECTED_EMIT,
        IN_PROCESS_EMIT,
        FAILED_EMIT,
        EMITTED
    }

    public enum WORKER_STATUS_CODES {
        ACTIVE,
        RESTARTING,
        HIBERNATING,
        SHOULD_SHUTDOWN,
        SHUTDOWN
    }

    enum ERROR_CODES {
        TIMEOUT,
        SECURITY_EXCEPTION,
        OTHER_EXCEPTION,
        OOM,
        OTHER_ERROR,
        UNKNOWN_PARSE,
        EMIT_SERIALIZATION,
        EMIT_SQL_INSERT_EXCEPTION,
        EMIT_SQL_SELECT_EXCEPTION,
        EMIT_DESERIALIZATION,
        EMIT_EXCEPTION
    }

    private static final Logger LOG = LoggerFactory.getLogger(AsyncWorkerProcess.class);

    //make these all configurable
    private static final long SHUTDOWN_AFTER_MS = 120000;
    private static long PULSE_MS = 1000;
    private long parseTimeoutMs = 60000;

    public static void main(String[] args) throws Exception {
        Path tikaConfigPath = Paths.get(System.getenv(AsyncProcessor.TIKA_ASYNC_CONFIG_FILE_KEY));
        String db = System.getenv(AsyncProcessor.TIKA_ASYNC_JDBC_KEY);
        TikaConfig tikaConfig = new TikaConfig(tikaConfigPath);
        int workerId = Integer.parseInt(args[0]);
        LOG.debug("trying to get connection {} >{}<", workerId, db);
        try (Connection connection = DriverManager.getConnection(db)) {
            AsyncWorkerProcess asyncWorker = new AsyncWorkerProcess();
            asyncWorker.execute(connection, workerId, tikaConfig);
        }
        System.exit(0);
    }

    private void execute(Connection connection,
                         int workerId, TikaConfig tikaConfig) throws SQLException {

        ExecutorService service = Executors.newFixedThreadPool(3);
        ExecutorCompletionService<Integer> executorCompletionService =
                new ExecutorCompletionService<>(service);

        executorCompletionService.submit(new Worker(connection, workerId,
                tikaConfig, parseTimeoutMs));
        executorCompletionService.submit(new ForkWatcher(System.in));

        int completed = 0;

        //if either one stops, we need to stop
        try {
            while (completed < 1) {
                Future<Integer> future = executorCompletionService.poll(60, TimeUnit.SECONDS);
                if (future != null) {
                    completed++;
                    future.get();
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            LOG.error("worker " + workerId + " had a mainloop exception", e);
        } finally {
            service.shutdownNow();
        }
        return;
    }

    private static class TaskQueue {
        private final Connection connection;
        private final int workerId;

        private final PreparedStatement markForSelecting;
        private final PreparedStatement selectForProcessing;
        private final PreparedStatement markForProcessing;
        private final PreparedStatement checkForShutdown;


        TaskQueue(Connection connection, int workerId) throws SQLException {
            this.connection = connection;
            this.workerId = workerId;
            //TODO -- need to update timestamp
            String sql = "update task_queue set status=" +
                    TASK_STATUS_CODES.SELECTED.ordinal()+
                    ", time_stamp=CURRENT_TIMESTAMP()"+
                    " where id = " +
                    " (select id from task_queue where worker_id = " + workerId +
                    " and status="+ TASK_STATUS_CODES.AVAILABLE.ordinal()+
                    " order by time_stamp asc limit 1 for update)";
            markForSelecting = connection.prepareStatement(sql);
            sql = "select id, retry, json from task_queue where status=" +
                    TASK_STATUS_CODES.SELECTED.ordinal() +
                    " and " +
                    " worker_id=" + workerId +
                    " order by time_stamp asc limit 1";
            selectForProcessing = connection.prepareStatement(sql);
            sql = "update task_queue set status="+
                    TASK_STATUS_CODES.IN_PROCESS.ordinal()+
                    ", time_stamp=CURRENT_TIMESTAMP()"+
                    " where id=?";
            markForProcessing = connection.prepareStatement(sql);

            sql = "select count(1) from workers where worker_id=" + workerId +
            " and status="+WORKER_STATUS_CODES.SHOULD_SHUTDOWN.ordinal();
            checkForShutdown = connection.prepareStatement(sql);
        }

        AsyncTask poll(long pollMs) throws InterruptedException, IOException, SQLException {
            long start = System.currentTimeMillis();
            long elapsed = System.currentTimeMillis() - start;
            while (elapsed < pollMs) {
                if (shouldShutdown()) {
                    return SHUTDOWN_SEMAPHORE;
                }
                int i = markForSelecting.executeUpdate();
                if (i == 0) {
                    debugQueue();
                    Thread.sleep(PULSE_MS);
                } else {
                    long taskId = -1;
                    short retry = -1;
                    String json = "";
                    try (ResultSet rs = selectForProcessing.executeQuery()) {
                        while (rs.next()) {
                            taskId = rs.getLong(1);
                            retry = rs.getShort(2);
                            json = rs.getString(3);
                        }
                    }
                    markForProcessing.clearParameters();
                    markForProcessing.setLong(1, taskId);
                    markForProcessing.execute();

                    FetchEmitTuple t = null;
                    try (Reader reader = new StringReader(json)) {
                        t = JsonFetchEmitTuple.fromJson(reader);
                    }
                    AsyncTask task = new AsyncTask(taskId, retry, t);
                    return task;
                }
                elapsed = System.currentTimeMillis() - start;
            }
            return null;
        }

        private void debugQueue() throws SQLException {
            try (ResultSet rs = connection.createStatement().executeQuery(
                    "select * from task_queue limit 10")) {
                while (rs.next()) {
                    for (int i = 1; i <= rs.getMetaData().getColumnCount(); i++) {
                        System.out.print(rs.getString(i)+ " ");
                    }
                    System.out.println("");
                }
            }
        }

        boolean shouldShutdown() throws SQLException {
            try (ResultSet rs = checkForShutdown.executeQuery()) {
                if (rs.next()) {
                    int val = rs.getInt(1);
                    return val > 0;
                }
            }
            return false;
        }
    }


    private static class Worker implements Callable<Integer> {

        private final Connection connection;
        private final int workerId;
        private final RecursiveParserWrapper parser;
        private final TikaConfig tikaConfig;
        private final long parseTimeoutMs;
        private ExecutorService executorService;
        private ExecutorCompletionService<AsyncData> executorCompletionService;
        private final PreparedStatement insertErrorLog;
        private final PreparedStatement resetStatus;
        private final PreparedStatement insertEmitData;
        private final PreparedStatement updateStatusForEmit;
        private final ObjectMapper objectMapper = new ObjectMapper();
        LZ4Factory factory = LZ4Factory.fastestInstance();

        public Worker(Connection connection,
                      int workerId,
                      TikaConfig tikaConfig, long parseTimeoutMs) throws SQLException {
            this.connection = connection;
            this.workerId = workerId;
            this.parser = new RecursiveParserWrapper(tikaConfig.getParser());
            this.tikaConfig = tikaConfig;
            this.executorService = Executors.newFixedThreadPool(1);
            this.executorCompletionService = new ExecutorCompletionService<>(executorService);
            this.parseTimeoutMs = parseTimeoutMs;

            SimpleModule module = new SimpleModule();
            module.addSerializer(Metadata.class, new JsonMetadata());
            objectMapper.registerModule(module);
            String sql = "insert into workers (worker_id, status) " +
                    "values (" + workerId + ", "+
                    WORKER_STATUS_CODES.ACTIVE.ordinal()+")";
            connection.createStatement().execute(sql);
            insertErrorLog = prepareInsertErrorLog(connection);
            resetStatus = prepareReset(connection);
            insertEmitData = prepareInsertEmitData(connection);
            sql = "update task_queue set status="+
                    TASK_STATUS_CODES.AVAILABLE_EMIT.ordinal()+
                    ", time_stamp=CURRENT_TIMESTAMP()" +
                    " where id=?";
            updateStatusForEmit = connection.prepareStatement(sql);
        }


        public Integer call() throws Exception {
            AsyncTask task = null;
            try {

                TaskQueue queue = new TaskQueue(connection, workerId);

                long lastProcessed = System.currentTimeMillis();

                while (true) {

                    task = queue.poll(1000);
                    if (task == null) {
                        long elapsed = System.currentTimeMillis() - lastProcessed;
                        if (elapsed > SHUTDOWN_AFTER_MS) {
                            LOG.debug("shutting down after no assignments in {}ms", elapsed);
                            return 1;
                        }
                    } else if (task == SHUTDOWN_SEMAPHORE) {
                        break;
                    } else {
                        processTask(task);
                        lastProcessed = System.currentTimeMillis();
                    }
                }
            } catch (TimeoutException e) {
                LOG.warn(task.getFetchKey().getKey(), e);
                reportAndReset(task, ERROR_CODES.TIMEOUT,
                        insertErrorLog, resetStatus, LOG);
            } catch (SecurityException e) {
                LOG.warn(task.getFetchKey().getKey(), e);
                reportAndReset(task, ERROR_CODES.SECURITY_EXCEPTION,
                        insertErrorLog, resetStatus, LOG);
            } catch (Exception e) {
                e.printStackTrace();
                LOG.warn(task.getFetchKey().getKey(), e);
                reportAndReset(task, ERROR_CODES.OTHER_EXCEPTION,
                        insertErrorLog, resetStatus, LOG);
            } catch (OutOfMemoryError e) {
                LOG.warn(task.getFetchKey().getKey(), e);
                reportAndReset(task, ERROR_CODES.OOM,
                        insertErrorLog, resetStatus, LOG);
            } catch (Error e) {
                LOG.warn(task.getFetchKey().getKey(), e);
                reportAndReset(task, ERROR_CODES.OTHER_ERROR,
                        insertErrorLog, resetStatus, LOG);
            } finally {
                executorService.shutdownNow();
                return 1;
            }
        }

        private void processTask(AsyncTask task) throws Exception {

            if (task == SHUTDOWN_SEMAPHORE) {
                LOG.debug("received shutdown notification");
                return;
            } else {
                executorCompletionService.submit(new TaskProcessor(task, tikaConfig, parser,
                        workerId));
                Future<AsyncData> future = executorCompletionService.poll(parseTimeoutMs, TimeUnit.MILLISECONDS);
                if (future == null) {
                    handleTimeout(task.getTaskId(), task.getFetchKey().getKey());
                } else {
                    AsyncData asyncData = future.get(1000, TimeUnit.MILLISECONDS);
                    if (asyncData == null) {
                        handleTimeout(task.getTaskId(), task.getFetchKey().getKey());
                    }
                    boolean shouldEmit = checkForParseException(asyncData);
                    if (shouldEmit) {
                        try {
                            emit(asyncData);
                        } catch (JsonProcessingException e) {
                            e.printStackTrace();
                            recordBadEmit(task.getTaskId(),
                                    task.getFetchKey().getKey(),
                                    ERROR_CODES.EMIT_SERIALIZATION.ordinal());
                        } catch (SQLException e) {
                            e.printStackTrace();
                            recordBadEmit(task.getTaskId(),
                                    task.getFetchKey().getKey(),
                                    ERROR_CODES.EMIT_SQL_INSERT_EXCEPTION.ordinal());
                        }
                    }
                }
            }
        }

        private void recordBadEmit(long taskId, String key, int ordinal) {
            //stub
        }

        private void emit(AsyncData asyncData) throws SQLException,
                JsonProcessingException {
            insertEmitData.clearParameters();
            insertEmitData.setLong(1, asyncData.getAsyncTask().getTaskId());
            byte[] bytes = objectMapper.writeValueAsBytes(asyncData);
            byte[] compressed = factory.fastCompressor().compress(bytes);
            insertEmitData.setLong(2, bytes.length);
            insertEmitData.setBlob(3, new ByteArrayInputStream(compressed));
            insertEmitData.execute();
            updateStatusForEmit.clearParameters();
            updateStatusForEmit.setLong(1,
                    asyncData.getAsyncTask().getTaskId());
            updateStatusForEmit.execute();
        }

        private void handleTimeout(long taskId, String key) throws TimeoutException {
            LOG.warn("timeout taskid:{} fetchKey:{}", taskId, key);
            throw new TimeoutException(key);
        }


        private boolean checkForParseException(AsyncData asyncData) {
            if (asyncData == null || asyncData.getMetadataList() == null ||
                    asyncData.getMetadataList().size() == 0) {
                LOG.warn("empty or null emit data ({})", asyncData.getAsyncTask()
                        .getFetchKey().getKey());
                return false;
            }
            boolean shouldEmit = true;
            Metadata container = asyncData.getMetadataList().get(0);
            String stack = container.get(TikaCoreProperties.CONTAINER_EXCEPTION);
            if (stack != null) {
                LOG.warn("fetchKey ({}) container parse exception ({})",
                        asyncData.getAsyncTask().getFetchKey().getKey(), stack);
                if (asyncData.getAsyncTask().getOnParseException()
                        == FetchEmitTuple.ON_PARSE_EXCEPTION.SKIP) {
                    shouldEmit = false;
                }
            }

            for (int i = 1; i < asyncData.getMetadataList().size(); i++) {
                Metadata m = asyncData.getMetadataList().get(i);
                String embeddedStack = m.get(TikaCoreProperties.EMBEDDED_EXCEPTION);
                if (embeddedStack != null) {
                    LOG.warn("fetchKey ({}) embedded parse exception ({})",
                            asyncData.getAsyncTask().getFetchKey().getKey(), embeddedStack);
                }
            }
            return shouldEmit;
        }

        static PreparedStatement prepareInsertEmitData(Connection connection) throws SQLException {
            return connection.prepareStatement(
                    "insert into emits (id, time_stamp, uncompressed_size, bytes) " +
                            " values (?,CURRENT_TIMESTAMP(),?,?)"
            );
        }
    }

    private static class TaskProcessor implements Callable<AsyncData> {

        private final AsyncTask task;
        private final Parser parser;
        private final TikaConfig tikaConfig;
        private final int workerId;

        public TaskProcessor(AsyncTask task,
                             TikaConfig tikaConfig,
                             Parser parser, int workerId) {
            this.task = task;
            this.parser = parser;
            this.tikaConfig = tikaConfig;
            this.workerId = workerId;
        }

        public AsyncData call() throws Exception {
            Metadata userMetadata = task.getMetadata();
            Metadata metadata = new Metadata();
            String fetcherName = task.getFetchKey().getFetcherName();
            String fetchKey = task.getFetchKey().getKey();
            List<Metadata> metadataList = null;
            try (InputStream stream = tikaConfig.getFetcherManager()
                    .getFetcher(fetcherName)
                    .fetch(fetchKey, metadata)) {
                metadataList = parseMetadata(task.getFetchKey(),
                        stream,
                        metadata);
            } catch (SecurityException e) {
                throw e;
            }
            injectUserMetadata(userMetadata, metadataList);
            EmitKey emitKey = task.getEmitKey();
            if (StringUtils.isBlank(emitKey.getEmitKey())) {
                emitKey = new EmitKey(emitKey.getEmitterName(), fetchKey);
                task.setEmitKey(emitKey);
            }
            return new AsyncData(task, metadataList);
        }

        private List<Metadata> parseMetadata(FetchKey fetchKey,
                                             InputStream stream, Metadata metadata) {
            //make these configurable
            BasicContentHandlerFactory.HANDLER_TYPE type =
                    BasicContentHandlerFactory.HANDLER_TYPE.TEXT;
            int writeLimit = -1;
            int maxEmbeddedResources = 1000;

            RecursiveParserWrapperHandler handler = new RecursiveParserWrapperHandler(
                    new BasicContentHandlerFactory(type, writeLimit), maxEmbeddedResources,
                    tikaConfig.getMetadataFilter());
            ParseContext parseContext = new ParseContext();
            try {
                parser.parse(stream, handler, metadata, parseContext);
            } catch (SAXException e) {
                LOG.warn("problem:" + fetchKey.getKey(), e);
            } catch (EncryptedDocumentException e) {
                LOG.warn("encrypted:" + fetchKey.getKey(), e);
            } catch (SecurityException e) {
                LOG.warn("security exception: " + fetchKey.getKey());
                throw e;
            } catch (Exception e) {
                LOG.warn("exception: " + fetchKey.getKey());
            } catch (OutOfMemoryError e) {
                LOG.error("oom: " + fetchKey.getKey());
                throw e;
            }
            return handler.getMetadataList();
        }

        private void injectUserMetadata(Metadata userMetadata, List<Metadata> metadataList) {
            for (String n : userMetadata.names()) {
                //overwrite whatever was there
                metadataList.get(0).set(n, null);
                for (String val : userMetadata.getValues(n)) {
                    metadataList.get(0).add(n, val);
                }
            }
        }
    }

    private static class ForkWatcher implements Callable<Integer> {
        private final InputStream in;
        public ForkWatcher(InputStream in) {
            this.in = in;
        }

        @Override
        public Integer call() throws Exception {
            //this should block forever
            //if the forking process dies,
            // this will either throw an IOException or read -1.
            int i = in.read();
            LOG.info("forking process notified forked to shutdown");
            return 1;
        }
    }
}
