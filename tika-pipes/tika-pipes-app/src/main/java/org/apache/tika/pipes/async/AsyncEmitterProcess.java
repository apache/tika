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

import com.fasterxml.jackson.databind.ObjectMapper;
import net.jpountz.lz4.LZ4Factory;
import net.jpountz.lz4.LZ4FastDecompressor;
import org.apache.tika.config.TikaConfig;
import org.apache.tika.pipes.emitter.AbstractEmitter;
import org.apache.tika.pipes.emitter.Emitter;
import org.apache.tika.pipes.emitter.EmitterManager;
import org.apache.tika.pipes.emitter.TikaEmitterException;
import org.apache.tika.utils.ExceptionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AsyncEmitterProcess {

    private long emitWithinMs = 1000;
    private long emitMaxBytes = 10_000_000;
    private static final Logger LOG = LoggerFactory.getLogger(AsyncEmitterProcess.class);

    private final LZ4FastDecompressor decompressor = LZ4Factory.fastestInstance().fastDecompressor();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public static void main(String[] args) throws Exception {
        String db = System.getenv(AsyncProcessor.TIKA_ASYNC_JDBC_KEY);
        TikaConfig tikaConfig = new TikaConfig(Paths.get(System.getenv(AsyncProcessor.TIKA_ASYNC_CONFIG_FILE_KEY)));
        int workerId = Integer.parseInt(args[0]);
        LOG.debug("trying to get connection {} >{}<", workerId, db);
        try (Connection connection = DriverManager.getConnection(db)) {
            AsyncEmitterProcess asyncEmitter = new AsyncEmitterProcess();
            asyncEmitter.execute(connection, workerId, tikaConfig);
        }
        System.exit(0);
    }

    private void execute(Connection connection, int workerId,
                         TikaConfig tikaConfig) throws SQLException {
        int recordsPerPulse = 10;
        String sql = "update emits set status=" +
                AsyncWorkerProcess.EMIT_STATUS_CODES.EMITTING.ordinal()+
                ", worker_id="+workerId+
                " where emit_id in " +
                " (select emit_id from emits "+//where worker_id = " + workerId +
                " and status="+ AsyncWorkerProcess.EMIT_STATUS_CODES.READY.ordinal()+
                " order by time_stamp asc limit "+recordsPerPulse+" for update)";
        PreparedStatement markForSelecting = connection.prepareStatement(sql);
        sql = "select emit_id, uncompressed_size, bytes from emits where status=" +
                AsyncWorkerProcess.EMIT_STATUS_CODES.EMITTING.ordinal() +
                " and worker_id=" + workerId +
                " order by time_stamp asc";
        PreparedStatement selectForProcessing = connection.prepareStatement(sql);
        sql = "delete from emits where emit_id=?";
        PreparedStatement deleteFromEmits = connection.prepareStatement(sql);

    }

    private AsyncData deserialize(byte[] compressed, int decompressedLength)
            throws IOException {
        byte[] restored = new byte[decompressedLength];
        int compressedLength2 = decompressor.decompress(compressed, 0, restored,
                0, decompressedLength);

        return objectMapper.readerFor(AsyncTask.class).readValue(restored);
    }

    private static class EmitDataCache {
        private final EmitterManager emitterManager;
        private final long maxBytes;

        long estimatedSize = 0;
        int size = 0;
        Map<String, List<AsyncData>> map = new HashMap<>();

        public EmitDataCache(EmitterManager emitterManager,
                             long maxBytes) {
            this.emitterManager = emitterManager;
            this.maxBytes = maxBytes;
        }

        void updateEstimatedSize(long newBytes) {
            estimatedSize += newBytes;
        }

        void add(AsyncData data) {

            size++;
            long sz = AbstractEmitter.estimateSizeInBytes(data.getEmitKey().getKey(), data.getMetadataList());
            if (estimatedSize + sz > maxBytes) {
                LOG.debug("estimated size ({}) > maxBytes({}), going to emitAll",
                        (estimatedSize+sz), maxBytes);
                emitAll();
            }
            List<AsyncData> cached = map.get(data.getEmitKey().getEmitterName());
            if (cached == null) {
                cached = new ArrayList<>();
                map.put(data.getEmitKey().getEmitterName(), cached);
            }
            updateEstimatedSize(sz);
            cached.add(data);
        }

        private void emitAll() {
            int emitted = 0;
            LOG.debug("about to emit {}", size);
            for (Map.Entry<String, List<AsyncData>> e : map.entrySet()) {
                Emitter emitter = emitterManager.getEmitter(e.getKey());
                tryToEmit(emitter, e.getValue());
                emitted += e.getValue().size();
            }
            LOG.debug("emitted: {}", emitted);
            estimatedSize = 0;
            size = 0;
            map.clear();
            //lastEmitted = Instant.now();
        }

        private long tryToEmit(Emitter emitter, List<AsyncData> cachedEmitData) {

            try {
                emitter.emit(cachedEmitData);
            } catch (IOException | TikaEmitterException e) {
                LOG.warn("emitter class ({}): {}", emitter.getClass(),
                        ExceptionUtils.getStackTrace(e));
            }
            return 1;
        }
    }
}
