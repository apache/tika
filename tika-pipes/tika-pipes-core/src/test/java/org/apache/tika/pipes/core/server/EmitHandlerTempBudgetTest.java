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
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.OutputStream;
import java.util.List;

import org.junit.jupiter.api.Test;

import org.apache.tika.config.TempLimits;
import org.apache.tika.exception.TempLimitReachedException;
import org.apache.tika.io.TempBytesBudget;
import org.apache.tika.io.TemporaryResources;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.pipes.api.PipesResult;
import org.apache.tika.pipes.api.emitter.EmitData;
import org.apache.tika.pipes.api.emitter.EmitKey;
import org.apache.tika.pipes.api.emitter.Emitter;
import org.apache.tika.plugins.ExtensionConfig;

/** TIKA-5075: a parse that spent its temp budget still emits what it parsed. */
public class EmitHandlerTempBudgetTest {

    private static final int MAX_TEMP_BYTES = 16;

    /** Spools to a budgeted temp file, as S3Emitter does with spoolToTemp. */
    private static final Emitter SPOOLING_EMITTER = new Emitter() {
        @Override
        public void emit(String emitKey, List<Metadata> metadataList, ParseContext parseContext)
                throws IOException {
            spool(parseContext, MAX_TEMP_BYTES);
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
    public void emitRunsAfterParseSpentItsBudget() throws Exception {
        ParseContext parseContext = new ParseContext();
        TempBytesBudget parseBudget = new TempBytesBudget(MAX_TEMP_BYTES);
        parseContext.set(TempBytesBudget.class, parseBudget);
        assertThrows(TempLimitReachedException.class,
                () -> spool(parseContext, MAX_TEMP_BYTES + 1));
        assertTrue(parseBudget.isLimitReached());

        TaskLeases leases = new TaskLeases(null, null) {
            @Override
            synchronized Emitter emitter(String id) {
                return SPOOLING_EMITTER;
            }
        };
        EmitHandler emitHandler = new EmitHandler(null, null, null, null, leases, 0);
        PipesResult result = emitHandler.emit("id", new EmitKey("e", "k"), false,
                new MetadataListAndEmbeddedBytes(List.of(new Metadata()), null),
                "TempLimitReachedException", parseContext);

        assertEquals(PipesResult.RESULT_STATUS.EMIT_SUCCESS_PARSE_EXCEPTION, result.status());
        assertSame(parseBudget, parseContext.get(TempBytesBudget.class));
    }
}
