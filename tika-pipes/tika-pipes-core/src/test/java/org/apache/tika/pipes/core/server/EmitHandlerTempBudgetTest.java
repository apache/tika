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
package org.apache.tika.pipes.core.server;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.io.OutputStream;
import java.util.List;

import org.junit.jupiter.api.Test;

import org.apache.tika.config.TempLimits;
import org.apache.tika.io.TempBytesBudget;
import org.apache.tika.io.TemporaryResources;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.pipes.api.PipesResult;
import org.apache.tika.pipes.api.emitter.EmitData;
import org.apache.tika.pipes.api.emitter.EmitKey;
import org.apache.tika.pipes.api.emitter.Emitter;
import org.apache.tika.plugins.ExtensionConfig;

/** TIKA-5075: the emit draws on the task's temp budget. */
public class EmitHandlerTempBudgetTest {

    private static final int MAX_TEMP_BYTES = 16;

    /** Spools to a budgeted temp file, as S3Emitter does with spoolToTemp. */
    private int spoolBytes = MAX_TEMP_BYTES;

    private final Emitter spoolingEmitter = new Emitter() {
        @Override
        public void emit(String emitKey, List<Metadata> metadataList, ParseContext parseContext)
                throws IOException {
            spool(parseContext, spoolBytes);
        }

        @Override
        public void emit(List<? extends EmitData> emitData) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ExtensionConfig getExtensionConfig() {
            return null;
        }
    };

    private static void spool(ParseContext parseContext, int bytes) throws IOException {
        try (TemporaryResources tmp = new TemporaryResources()) {
            TempLimits.apply(tmp, parseContext);
            try (OutputStream os = tmp.newOutputStream(tmp.createTempFile())) {
                os.write(new byte[bytes]);
            }
        }
    }

    @Test
    public void emitSharesTheTaskBudget() throws Exception {
        ParseContext parseContext = new ParseContext();
        parseContext.set(TempBytesBudget.class, new TempBytesBudget(MAX_TEMP_BYTES));
        TaskLeases leases = new TaskLeases(null, null) {
            @Override
            synchronized Emitter emitter(String id) {
                return spoolingEmitter;
            }
        };
        EmitHandler emitHandler = new EmitHandler(null, null, null, null, leases, 0);
        // still held at emit, as the fetched file's spool is
        try (TemporaryResources held = new TemporaryResources()) {
            TempLimits.apply(held, parseContext);
            try (OutputStream os = held.newOutputStream(held.createTempFile())) {
                os.write(new byte[1]);
            }
            PipesResult result = emitHandler.emit("id", new EmitKey("e", "k"), false,
                    new MetadataListAndEmbeddedBytes(List.of(new Metadata()), null), null, parseContext);
            assertEquals(PipesResult.RESULT_STATUS.TEMP_LIMIT_REACHED, result.status());
        }
    }

    @Test
    public void emitThatReachesTheLimitIsNotAnEmitException() throws Exception {
        spoolBytes = MAX_TEMP_BYTES + 1;
        ParseContext parseContext = new ParseContext();
        parseContext.set(TempBytesBudget.class, new TempBytesBudget(MAX_TEMP_BYTES));
        TaskLeases leases = new TaskLeases(null, null) {
            @Override
            synchronized Emitter emitter(String id) {
                return spoolingEmitter;
            }
        };
        EmitHandler emitHandler = new EmitHandler(null, null, null, null, leases, 0);
        PipesResult result = emitHandler.emit("id", new EmitKey("e", "k"), false,
                new MetadataListAndEmbeddedBytes(List.of(new Metadata()), null), null, parseContext);
        assertEquals(PipesResult.RESULT_STATUS.TEMP_LIMIT_REACHED, result.status());
    }
}
