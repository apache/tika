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
package org.apache.tika.sax;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.xml.sax.EntityResolver;
import org.xml.sax.InputSource;
import org.xml.sax.helpers.DefaultHandler;

import org.apache.tika.parser.ParseContext;
import org.apache.tika.utils.XMLReaderUtils;

/**
 * A resolver that returns null, or a source with no stream, hands the id back to the
 * parser to open (CVE-2025-66516). Every resolver Tika installs must answer every
 * input shape with a source that carries an empty stream.
 */
public class OfflineEntityResolverTest {

    private static final String[][] IDS = {
            {null, null},
            {"-//W3C//DTD XHTML 1.0//EN", null},
            {null, "http://127.0.0.1:9/x.dtd"},
            {null, "file:///etc/passwd"},
            {null, "relative.dtd"},
            {null, ""},
            {"-//X//Y//EN", "https://example.invalid/y.dtd"},
    };

    @Test
    public void testInstalledResolversAlwaysAnswerWithAnEmptyStream() throws Exception {
        List<EntityResolver> resolvers = List.of(
                new OfflineEntityResolver(null),
                new OfflineEntityResolver(new ParseContext()),
                new OfflineContentHandler(new DefaultHandler()),
                new OfflineContentHandler(new DefaultHandler(), new ParseContext()),
                XMLReaderUtils.getXMLReader().getEntityResolver());
        for (EntityResolver resolver : resolvers) {
            for (String[] id : IDS) {
                InputSource source = resolver.resolveEntity(id[0], id[1]);
                String label = resolver.getClass().getSimpleName() + " for " + id[0] + ", " + id[1];
                assertNotNull(source, label + " returned null");
                assertNull(source.getSystemId(), label + " carries a system id");
                assertTrue(source.getByteStream() != null || source.getCharacterStream() != null,
                        label + " carries no stream");
                assertEquals(-1, source.getByteStream() != null ? source.getByteStream().read() :
                        source.getCharacterStream().read(), label + " stream is not empty");
            }
        }
    }

    @Test
    public void testEmptyStreamTwiceOverIsStillEmpty() throws IOException {
        InputSource source = new OfflineEntityResolver(null).resolveEntity(null, "x.dtd");
        assertEquals(-1, source.getByteStream().read());
        assertEquals(-1, source.getByteStream().read(new byte[16]));
    }
}
