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

package org.apache.tika.config;


import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import org.apache.tika.parser.CompositeParser;
import org.apache.tika.parser.Parser;
import org.apache.tika.parser.ParserDecorator;
import org.apache.tika.parser.mock.MockParser;
import org.apache.tika.parser.multiple.FallbackParser;

public class TikaConfigSerializerTest extends TikaConfigTest {

    /**
     * TIKA-1445 It should be possible to exclude DefaultParser from
     * certain types, so another parser explicitly listed will take them
     */
    @Test
    public void defaultParserWithExcludes() throws Exception {
        String xml =
                loadAndSerialize("TIKA-1445-default-except.xml", TikaConfigSerializer.Mode.STATIC);
        assertContains(
                "<parser class=\"org.apache.tika.parser.ErrorParser\">" + " <mime>fail/world" +
                    "</mime> " +
                "</parser>", xml);
    }

    @Test
    public void testEncodingDetectors() throws Exception {
        String xml = loadAndSerialize("TIKA-1762-executors.xml", TikaConfigSerializer.Mode.STATIC);
        assertContains("<encodingDetectors> " +
                "<encodingDetector class=\"org.apache.tika.detect" +
                ".NonDetectingEncodingDetector\"/> " +
                "</encodingDetectors>", xml);
    }

    @Test
    public void testMultipleWithFallback() throws Exception {
        TikaConfig config = getConfig("TIKA-1509-multiple-fallback.xml");
        StringWriter writer = new StringWriter();
        TikaConfigSerializer.serialize(config,
                TikaConfigSerializer.Mode.STATIC_FULL, writer, StandardCharsets.UTF_8);
        try (InputStream is =
                     new ByteArrayInputStream(writer.toString().getBytes(StandardCharsets.UTF_8))) {
            config = new TikaConfig(is);
        }

        CompositeParser parser = (CompositeParser) config.getParser();
        assertEquals(2, parser.getAllComponentParsers().size());
        Parser p;

        p = parser.getAllComponentParsers().get(0);
        assertEquals(MockParser.class, ((ParserDecorator) p).getWrappedParser().getClass());

        p = parser.getAllComponentParsers().get(1);
        assertTrue(p instanceof ParserDecorator, p.toString());
        assertEquals(FallbackParser.class, ((ParserDecorator) p).getWrappedParser().getClass());

        FallbackParser fbp = (FallbackParser) ((ParserDecorator) p).getWrappedParser();
        assertEquals("DISCARD_ALL", fbp.getMetadataPolicy().toString());
    }

    @Test
    @Disabled("TODO: executor-service info needs to be stored in TikaConfig for serialization")
    public void testExecutors() throws Exception {
        String xml = loadAndSerialize("TIKA-1762-executors.xml", TikaConfigSerializer.Mode.STATIC);
        assertContains("<executor-service class=\"org.apache.tika.config.DummyExecutor\">" +
                    " <core-threads>3</core-threads>" + " <max-threads>10</max-threads>" +
                    "</executor-service>", xml);
    }

    String loadAndSerialize(String configFile, TikaConfigSerializer.Mode mode) throws Exception {
        TikaConfig config = getConfig(configFile);
        StringWriter writer = new StringWriter();
        TikaConfigSerializer.serialize(config, mode, writer, StandardCharsets.UTF_8);
        return writer.toString().replaceAll("[\r\n\t ]+", " ");
    }
}
