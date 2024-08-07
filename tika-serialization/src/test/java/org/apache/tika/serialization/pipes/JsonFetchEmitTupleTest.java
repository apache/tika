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
package org.apache.tika.serialization.pipes;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.Reader;
import java.io.StringReader;
import java.io.StringWriter;

import org.junit.jupiter.api.Test;

import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.pipes.FetchEmitTuple;
import org.apache.tika.pipes.HandlerConfig;
import org.apache.tika.pipes.emitter.EmitKey;
import org.apache.tika.pipes.fetcher.FetchKey;
import org.apache.tika.sax.BasicContentHandlerFactory;

public class JsonFetchEmitTupleTest {

    @Test
    public void testBasic() throws Exception {
        Metadata m = new Metadata();
        m.add("m1", "v1");
        m.add("m1", "v1");
        m.add("m2", "v2");
        m.add("m2", "v3");
        m.add("m3", "v4");

        ParseContext parseContext = new ParseContext();

        HandlerConfig h = new HandlerConfig(BasicContentHandlerFactory.HANDLER_TYPE.XML, HandlerConfig.PARSE_MODE.CONCATENATE, 10000, 10, true);
        parseContext.set(HandlerConfig.class, h);

        FetchEmitTuple t = new FetchEmitTuple("my_id", new FetchKey("my_fetcher", "fetchKey1"), new EmitKey("my_emitter", "emitKey1"), m, parseContext,
                FetchEmitTuple.ON_PARSE_EXCEPTION.SKIP);
        StringWriter writer = new StringWriter();
        JsonFetchEmitTuple.toJson(t, writer);
        Reader reader = new StringReader(writer.toString());
        FetchEmitTuple deserialized = JsonFetchEmitTuple.fromJson(reader);
        assertEquals(t, deserialized);
    }

    @Test
    public void testFetchRange() throws Exception {
        Metadata m = new Metadata();
        m.add("m1", "v1");
        m.add("m1", "v1");
        m.add("m2", "v2");
        m.add("m2", "v3");
        m.add("m3", "v4");

        /**
         *                TODO -- add this to the ParseContext
         *                new HandlerConfig(BasicContentHandlerFactory.HANDLER_TYPE.XML,
         *                         HandlerConfig.PARSE_MODE.CONCATENATE,
         *                         10000,10, true),
         */
        FetchEmitTuple t = new FetchEmitTuple("my_id", new FetchKey("my_fetcher", "fetchKey1", 10, 1000), new EmitKey("my_emitter", "emitKey1"), m, new ParseContext(),
                FetchEmitTuple.ON_PARSE_EXCEPTION.SKIP);
        StringWriter writer = new StringWriter();
        JsonFetchEmitTuple.toJson(t, writer);
        Reader reader = new StringReader(writer.toString());
        FetchEmitTuple deserialized = JsonFetchEmitTuple.fromJson(reader);
        assertEquals(t, deserialized);
    }

    @Test
    public void testBytes() throws Exception {
        /**
         * TODO -- add these to the ParseContext
         EmbeddedDocumentBytesConfig bytesConfig = new EmbeddedDocumentBytesConfig(true);
         bytesConfig.setEmitter("emitter");
         * new HandlerConfig(BasicContentHandlerFactory.HANDLER_TYPE.XML,
         *                         HandlerConfig.PARSE_MODE.CONCATENATE,
         *                         10000,10, true)
         */
        FetchEmitTuple t = new FetchEmitTuple("my_id", new FetchKey("my_fetcher", "fetchKey1", 10, 1000), new EmitKey("my_emitter", "emitKey1"), new Metadata(), new ParseContext(),
                FetchEmitTuple.ON_PARSE_EXCEPTION.SKIP);
        StringWriter writer = new StringWriter();
        JsonFetchEmitTuple.toJson(t, writer);
        Reader reader = new StringReader(writer.toString());
        FetchEmitTuple deserialized = JsonFetchEmitTuple.fromJson(reader);
        assertEquals(t, deserialized);

    }
}
