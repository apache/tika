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
package org.apache.tika.pipes.core.extractor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.pipes.api.FetchEmitTuple;
import org.apache.tika.pipes.api.emitter.EmitData;
import org.apache.tika.pipes.api.emitter.EmitKey;
import org.apache.tika.pipes.api.emitter.StreamEmitter;
import org.apache.tika.pipes.api.fetcher.FetchKey;
import org.apache.tika.plugins.ExtensionConfig;

public class EmittingUnpackHandlerTest {

    //TIKA-5075: an emitter that spools finds the task's temp budget there
    @Test
    public void emitterGetsTheTaskContext() throws Exception {
        List<ParseContext> seen = new ArrayList<>();
        StreamEmitter emitter = new StreamEmitter() {
            @Override
            public void emit(String emitKey, InputStream inputStream, Metadata userMetadata,
                             ParseContext parseContext) {
                seen.add(parseContext);
            }

            @Override
            public void emit(String emitKey, List<Metadata> metadataList,
                             ParseContext parseContext) {
                throw new UnsupportedOperationException();
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
        ParseContext taskContext = new ParseContext();
        taskContext.set(UnpackConfig.class, new UnpackConfig());
        FetchEmitTuple tuple = new FetchEmitTuple("id", new FetchKey("f", "k"),
                new EmitKey("e", "k"));
        try (EmittingUnpackHandler handler =
                     new EmittingUnpackHandler(tuple, emitter, taskContext)) {
            handler.add(1, new Metadata(), new ByteArrayInputStream(new byte[4]));
            assertEquals(0, seen.size(), "staged, not emitted, during the parse");
            assertEquals(1, handler.emitStaged(taskContext));
        }
        assertEquals(1, seen.size());
        assertSame(taskContext, seen.get(0));
    }
}
