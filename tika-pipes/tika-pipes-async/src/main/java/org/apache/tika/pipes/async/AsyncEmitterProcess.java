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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Paths;
import java.sql.Blob;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.FutureTask;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import net.jpountz.lz4.LZ4Factory;
import net.jpountz.lz4.LZ4FastDecompressor;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.tika.config.TikaConfig;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.serialization.JsonMetadataDeserializer;
import org.apache.tika.pipes.emitter.AbstractEmitter;
import org.apache.tika.pipes.emitter.Emitter;
import org.apache.tika.pipes.emitter.EmitterManager;
import org.apache.tika.pipes.emitter.TikaEmitterException;
import org.apache.tika.utils.ExceptionUtils;

public class AsyncEmitterProcess {

    private static final Logger LOG = LoggerFactory.getLogger(AsyncEmitterProcess.class);
    private final LZ4FastDecompressor decompressor =
            LZ4Factory.fastestInstance().fastDecompressor();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final FutureTask<Integer> stdinWatcher;
    int recordsPerPulse = 10;
    //TODO -- parameterize these
    private final long emitWithinMs = 10000;
    private final long emitMaxBytes = 10_000_000;
    private PreparedStatement markForSelecting;
    private PreparedStatement selectForProcessing;
    private PreparedStatement emitStatusUpdate;
    private PreparedStatement checkForCanShutdown;
    private PreparedStatement checkForShouldShutdown;
    private PreparedStatement updateEmitterStatus;

    private AsyncEmitterProcess(InputStream stdin) {
        SimpleModule module = new SimpleModule();
        module.addDeserializer(Metadata.class, new JsonMetadataDeserializer());
        objectMapper.registerModule(module);
        stdinWatcher = new FutureTask<>(new ForkWatcher(stdin));
        new Thread(stdinWatcher).start();
    }

    public static void main(String[] args) throws Exception {
        String db = System.getenv(AsyncProcessor.TIKA_ASYNC_JDBC_KEY);
        TikaConfig tikaConfig =
                new TikaConfig(Paths.get(System.getenv(AsyncProcessor.TIKA_ASYNC_CONFIG_FILE_KEY)));
        int workerId = Integer.parseInt(args[0]);
        LOG.debug("trying to get connection {} >{}<", workerId, db);
        try (Connection connection = DriverManager.getConnection(db)) {
            AsyncEmitterProcess asyncEmitter = new AsyncEmitterProcess(System.in);
            asyncEmitter.execute(connection, workerId, tikaConfig);
        }
        System.exit(0);
    }

    private static void reportEmitStatus(List<Long> ids,
                                         AsyncWorkerProcess.TASK_STATUS_CODES emitted,
                                         PreparedStatement emitStatusUpdate) throws SQLException {
        for (long id : ids) {
            emitStatusUpdate.clearParameters();
            emitStatusUpdate.setByte(1, (byte) emitted.ordinal());
            emitStatusUpdate.setLong(2, id);
            emitStatusUpdate.addBatch();
        }
        emitStatusUpdate.executeBatch();
    }

    private void execute(Connection connection, int workerId, TikaConfig tikaConfig)
            throws SQLException, InterruptedException {
        prepareStatements(connection, workerId);
        updateStatus((byte) AsyncWorkerProcess.WORKER_STATUS_CODES.ACTIVE.ordinal());
        EmitterManager emitterManager = tikaConfig.getEmitterManager();
        EmitDataCache emitDataCache =
                new EmitDataCache(emitterManager, emitMaxBytes, emitStatusUpdate);
        try {
            mainLoop(emitDataCache);
        } finally {
            emitDataCache.emitAll();
            updateStatus((byte) AsyncWorkerProcess.WORKER_STATUS_CODES.HAS_SHUTDOWN.ordinal());
        }
    }

    private void mainLoop(EmitDataCache emitDataCache) throws InterruptedException, SQLException {

        while (true) {
            if (shouldShutdown()) {
                LOG.debug("received should shutdown signal");
                return;
            }
            int toEmit = markForSelecting.executeUpdate();
            if (toEmit == 0 && canShutdown()) {
                //avoid race condition; double check there's nothing
                //left to emit
                toEmit = markForSelecting.executeUpdate();
                if (toEmit == 0) {
                    LOG.debug("received can shutdown and didn't update any for selecting");
                    return;
                }
            }
            if (toEmit > 0) {
                try {
                    tryToEmitNextBatch(emitDataCache);
                } catch (IOException e) {
                    LOG.warn("IOException trying to emit", e);
                }
            }
            if (emitDataCache.exceedsEmitWithin(emitWithinMs)) {
                emitDataCache.emitAll();
            }
            Thread.sleep(500);
        }
    }

    private void tryToEmitNextBatch(EmitDataCache emitDataCache) throws IOException, SQLException {
        List<AsyncEmitTuple> toEmitList = new ArrayList<>();
        try (ResultSet rs = selectForProcessing.executeQuery()) {
            while (rs.next()) {
                long id = rs.getLong(1);
                Timestamp ts = rs.getTimestamp(2);
                int uncompressedSize = rs.getInt(3);
                Blob blob = rs.getBlob(4);
                ByteArrayOutputStream bos = new ByteArrayOutputStream();
                IOUtils.copyLarge(blob.getBinaryStream(), bos);
                byte[] bytes = bos.toByteArray();
                toEmitList.add(new AsyncEmitTuple(id, ts, uncompressedSize, bytes));
            }
        }
        List<Long> successes = new ArrayList<>();
        List<Long> exceptions = new ArrayList<>();
        for (AsyncEmitTuple tuple : toEmitList) {
            try {
                tryToEmit(tuple, emitDataCache);
                successes.add(tuple.id);
            } catch (IOException | SQLException e) {
                exceptions.add(tuple.id);
            }
        }
        reportEmitStatus(successes, AsyncWorkerProcess.TASK_STATUS_CODES.IN_PROCESS_EMIT,
                emitStatusUpdate);
        reportEmitStatus(exceptions, AsyncWorkerProcess.TASK_STATUS_CODES.FAILED_EMIT,
                emitStatusUpdate);

    }

    private void updateStatus(byte status) throws SQLException {
        updateEmitterStatus.clearParameters();
        updateEmitterStatus.setByte(1, status);
        updateEmitterStatus.executeUpdate();
    }

    private void tryToEmit(AsyncEmitTuple asyncEmitTuple, EmitDataCache emitDataCache)
            throws SQLException, IOException {
        AsyncData asyncData = deserialize(asyncEmitTuple.bytes, asyncEmitTuple.uncompressedSize);
        emitDataCache.add(asyncData);
    }

    boolean shouldShutdown() throws SQLException {
        if (stdinWatcher.isDone()) {
            LOG.info("parent inputstream closed; shutting down now");
        }
        try (ResultSet rs = checkForShouldShutdown.executeQuery()) {
            if (rs.next()) {
                int val = rs.getInt(1);
                return val > 0;
            }
        }
        return false;
    }

    boolean canShutdown() throws SQLException {
        try (ResultSet rs = checkForCanShutdown.executeQuery()) {
            if (rs.next()) {
                int val = rs.getInt(1);
                return val > 0;
            }
        }
        return false;
    }

    private void prepareStatements(Connection connection, int workerId) throws SQLException {
        String sql = "update task_queue set status=" +
                AsyncWorkerProcess.TASK_STATUS_CODES.SELECTED_EMIT.ordinal() + ", worker_id=" +
                workerId + ", time_stamp=CURRENT_TIMESTAMP()" + " where id in " +
                " (select id from task_queue " + //where worker_id = " + workerId +
                " where status=" + AsyncWorkerProcess.TASK_STATUS_CODES.AVAILABLE_EMIT.ordinal() +
                " order by time_stamp asc limit " + recordsPerPulse + " for update)";
        markForSelecting = connection.prepareStatement(sql);

        sql = "select q.id, q.time_stamp, uncompressed_size, bytes " + "from emits e " +
                "join task_queue q on e.id=q.id " + "where q.status=" +
                AsyncWorkerProcess.TASK_STATUS_CODES.SELECTED_EMIT.ordinal() + " and worker_id=" +
                workerId + " order by time_stamp asc";
        selectForProcessing = connection.prepareStatement(sql);

        //only update the status if it is not already emitted or failed emit
        sql = "update task_queue set status=?" + ", time_stamp=CURRENT_TIMESTAMP()" +
                " where id=? and status not in (" +
                AsyncWorkerProcess.TASK_STATUS_CODES.EMITTED.ordinal() + ", " +
                AsyncWorkerProcess.TASK_STATUS_CODES.FAILED_EMIT.ordinal() + ")";

        emitStatusUpdate = connection.prepareStatement(sql);

        sql = "select count(1) from emitters where emitter_id=" + workerId + " and status=" +
                AsyncWorkerProcess.WORKER_STATUS_CODES.CAN_SHUTDOWN.ordinal();
        checkForCanShutdown = connection.prepareStatement(sql);

        sql = "select count(1) from emitters where emitter_id=" + workerId + " and status=" +
                AsyncWorkerProcess.WORKER_STATUS_CODES.SHOULD_SHUTDOWN.ordinal();
        checkForShouldShutdown = connection.prepareStatement(sql);

        sql = "merge into emitters key (emitter_id) " + "values (" + workerId + ", ? )";
        updateEmitterStatus = connection.prepareStatement(sql);
    }

    private AsyncData deserialize(byte[] compressed, int decompressedLength) throws IOException {
        byte[] restored = new byte[decompressedLength];
        int compressedLength2 =
                decompressor.decompress(compressed, 0, restored, 0, decompressedLength);
        return objectMapper.readerFor(AsyncData.class).readValue(restored);
    }

    private static class EmitDataCache {
        private final EmitterManager emitterManager;
        private final long maxBytes;
        private final PreparedStatement emitStatusUpdate;
        long estimatedSize = 0;
        int size = 0;
        Map<String, List<AsyncData>> map = new HashMap<>();
        private Instant lastAdded = Instant.now();

        public EmitDataCache(EmitterManager emitterManager, long maxBytes,
                             PreparedStatement emitStatusUpdate) {
            this.emitterManager = emitterManager;
            this.maxBytes = maxBytes;
            this.emitStatusUpdate = emitStatusUpdate;
        }

        void updateEstimatedSize(long newBytes) {
            estimatedSize += newBytes;
        }

        void add(AsyncData data) {

            size++;
            long sz = AbstractEmitter
                    .estimateSizeInBytes(data.getEmitKey().getEmitKey(), data.getMetadataList());
            if (estimatedSize + sz > maxBytes) {
                LOG.debug("estimated size ({}) > maxBytes({}), going to emitAll",
                        (estimatedSize + sz), maxBytes);
                emitAll();
            }
            List<AsyncData> cached = map.get(data.getEmitKey().getEmitterName());
            if (cached == null) {
                cached = new ArrayList<>();
                map.put(data.getEmitKey().getEmitterName(), cached);
            }
            updateEstimatedSize(sz);
            cached.add(data);
            lastAdded = Instant.now();
        }

        private void emitAll() {
            int emitted = 0;
            LOG.debug("about to emit {}", size);
            for (Map.Entry<String, List<AsyncData>> e : map.entrySet()) {
                Emitter emitter = emitterManager.getEmitter(e.getKey());

                try {
                    tryToEmit(emitter, e.getValue());
                } catch (SQLException ex) {
                    ex.printStackTrace();
                }
                emitted += e.getValue().size();
            }
            LOG.debug("emitted: {}", emitted);
            estimatedSize = 0;
            size = 0;
            map.clear();
        }

        private long tryToEmit(Emitter emitter, List<AsyncData> cachedEmitData)
                throws SQLException {
            List<Long> ids = new ArrayList<>();
            for (AsyncData d : cachedEmitData) {
                ids.add(d.getTaskId());
            }
            try {
                emitter.emit(cachedEmitData);
            } catch (IOException | TikaEmitterException e) {
                LOG.warn("emitter class ({}): {}", emitter.getClass(),
                        ExceptionUtils.getStackTrace(e));
                reportEmitStatus(ids, AsyncWorkerProcess.TASK_STATUS_CODES.FAILED_EMIT,
                        emitStatusUpdate);
            }
            reportEmitStatus(ids, AsyncWorkerProcess.TASK_STATUS_CODES.EMITTED, emitStatusUpdate);
            return 1;
        }


        public boolean exceedsEmitWithin(long emitWithinMs) {
            return ChronoUnit.MILLIS.between(lastAdded, Instant.now()) > emitWithinMs;
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
            try {
                int i = in.read();
            } finally {
                LOG.warn("forking process shutdown; exiting now");
                System.exit(0);
            }
            return 1;
        }
    }

    private static class AsyncEmitTuple {
        final long id;
        final Timestamp timestamp;
        final int uncompressedSize;
        final byte[] bytes;

        public AsyncEmitTuple(long id, Timestamp timestamp, int uncompressedSize, byte[] bytes) {
            this.id = id;
            this.timestamp = timestamp;
            this.uncompressedSize = uncompressedSize;
            this.bytes = bytes;
        }
    }
}
