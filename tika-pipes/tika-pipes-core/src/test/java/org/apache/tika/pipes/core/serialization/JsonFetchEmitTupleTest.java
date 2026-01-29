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
package org.apache.tika.pipes.core.serialization;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.Reader;
import java.io.StringReader;
import java.io.StringWriter;

import org.junit.jupiter.api.Test;

import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.pipes.api.FetchEmitTuple;
import org.apache.tika.pipes.api.ParseMode;
import org.apache.tika.pipes.api.emitter.EmitKey;
import org.apache.tika.pipes.api.fetcher.FetchKey;
import org.apache.tika.sax.BasicContentHandlerFactory;
import org.apache.tika.sax.ContentHandlerFactory;

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

        // Set ContentHandlerFactory and ParseMode in ParseContext
        ContentHandlerFactory factory = new BasicContentHandlerFactory(
                BasicContentHandlerFactory.HANDLER_TYPE.XML, 10000);
        parseContext.set(ContentHandlerFactory.class, factory);
        parseContext.set(ParseMode.class, ParseMode.CONCATENATE);

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

        // TODO -- add this to the ParseContext:
        // parseContext.set(ContentHandlerFactory.class, new BasicContentHandlerFactory(
        //     BasicContentHandlerFactory.HANDLER_TYPE.XML, 10000));
        // parseContext.set(ParseMode.class, ParseMode.CONCATENATE);
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
        // TODO -- add these to the ParseContext:
        // UnpackConfig bytesConfig = new UnpackConfig(true);
        // bytesConfig.setEmitter("emitter");
        // parseContext.set(ContentHandlerFactory.class, new BasicContentHandlerFactory(
        //     BasicContentHandlerFactory.HANDLER_TYPE.XML, 10000));
        // parseContext.set(ParseMode.class, ParseMode.CONCATENATE);
        FetchEmitTuple t = new FetchEmitTuple("my_id", new FetchKey("my_fetcher", "fetchKey1", 10, 1000), new EmitKey("my_emitter", "emitKey1"), new Metadata(), new ParseContext(),
                FetchEmitTuple.ON_PARSE_EXCEPTION.SKIP);
        StringWriter writer = new StringWriter();
        JsonFetchEmitTuple.toJson(t, writer);
        Reader reader = new StringReader(writer.toString());
        FetchEmitTuple deserialized = JsonFetchEmitTuple.fromJson(reader);
        assertEquals(t, deserialized);

    }
}
