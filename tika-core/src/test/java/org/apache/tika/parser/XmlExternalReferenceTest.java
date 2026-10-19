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
package org.apache.tika.parser;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import org.apache.tika.exception.TikaException;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.HttpHeaders;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.mime.MediaType;
import org.apache.tika.mime.MediaTypeRegistry;
import org.apache.tika.utils.XMLReaderUtils;

/**
 * Refused external references land on the metadata of the document whose XML made them,
 * and the container is flagged when an embedded document made any.
 */
public class XmlExternalReferenceTest {

    private static final String DTD = "file:///couldnt_possibly_exist/a.dtd";
    private static final String ENTITY = "http://127.0.0.1:9/e.txt";
    private static final String XML = "<!DOCTYPE r SYSTEM \"" + DTD + "\" [<!ENTITY e SYSTEM \"" +
            ENTITY + "\">]><r>&e;</r>";

    // parses XML through the two chokepoints, and can parse a nested "embedded" document
    private static class XmlParser implements Parser {
        private final boolean dom;
        private Parser embedded;
        private byte[] embeddedBytes;
        private Metadata innerMetadata;

        XmlParser(boolean dom) {
            this.dom = dom;
        }

        @Override
        public Set<MediaType> getSupportedTypes(ParseContext context) {
            return Collections.singleton(MediaType.APPLICATION_XML);
        }

        @Override
        public void parse(TikaInputStream stream, ContentHandler handler, Metadata metadata,
                          ParseContext context) throws IOException, SAXException, TikaException {
            if (dom) {
                XMLReaderUtils.buildDOM(stream, context);
            } else {
                XMLReaderUtils.parseSAX(stream, new DefaultHandler(), context);
            }
            if (embedded != null) {
                innerMetadata = new Metadata();
                innerMetadata.set(TikaCoreProperties.RESOURCE_NAME_KEY, "inner.xml");
                innerMetadata.set(HttpHeaders.CONTENT_TYPE, "application/xml");
                try (TikaInputStream tis = TikaInputStream.get(embeddedBytes)) {
                    embedded.parse(tis, new DefaultHandler(), innerMetadata, context);
                }
            }
        }
    }

    private static Metadata parse(XmlParser xmlParser, String xml) throws Exception {
        CompositeParser composite = new CompositeParser(MediaTypeRegistry.getDefaultRegistry(),
                List.of(xmlParser));
        Metadata metadata = new Metadata();
        metadata.set(TikaCoreProperties.RESOURCE_NAME_KEY, "outer.xml");
        metadata.set(HttpHeaders.CONTENT_TYPE, "application/xml");
        try (TikaInputStream tis = TikaInputStream.get(xml.getBytes(StandardCharsets.UTF_8))) {
            composite.parse(tis, new DefaultHandler(), metadata, new ParseContext());
        }
        return metadata;
    }

    @Test
    public void testSaxRecordsOnDocument() throws Exception {
        Metadata m = parse(new XmlParser(false), XML);
        assertArrayEquals(new String[]{DTD, ENTITY},
                m.getValues(TikaCoreProperties.XML_EXTERNAL_REFERENCE));
        assertEquals(2, m.getInt(TikaCoreProperties.XML_EXTERNAL_REFERENCE_COUNT));
        assertNull(m.get(TikaCoreProperties.XML_EXTERNAL_REFERENCE_EMBEDDED));
    }

    @Test
    public void testDomRecordsOnDocument() throws Exception {
        Metadata m = parse(new XmlParser(true), XML);
        assertTrue(m.getInt(TikaCoreProperties.XML_EXTERNAL_REFERENCE_COUNT) >= 1);
        assertEquals(DTD, m.getValues(TikaCoreProperties.XML_EXTERNAL_REFERENCE)[0]);
    }

    @Test
    public void testCleanDocumentRecordsNothing() throws Exception {
        Metadata m = parse(new XmlParser(false), "<r>plain</r>");
        assertNull(m.get(TikaCoreProperties.XML_EXTERNAL_REFERENCE));
        assertNull(m.get(TikaCoreProperties.XML_EXTERNAL_REFERENCE_COUNT));
    }

    @Test
    public void testEmbeddedRecordsOnInnerAndFlagsOuter() throws Exception {
        XmlParser outer = new XmlParser(false);
        outer.embedded = new CompositeParser(MediaTypeRegistry.getDefaultRegistry(),
                List.of(new XmlParser(false)));
        outer.embeddedBytes = XML.getBytes(StandardCharsets.UTF_8);
        Metadata m = parse(outer, "<r>plain outer</r>");
        assertNull(m.get(TikaCoreProperties.XML_EXTERNAL_REFERENCE));
        assertEquals("true", m.get(TikaCoreProperties.XML_EXTERNAL_REFERENCE_EMBEDDED));
        assertArrayEquals(new String[]{DTD, ENTITY},
                outer.innerMetadata.getValues(TikaCoreProperties.XML_EXTERNAL_REFERENCE));
        assertNull(outer.innerMetadata.get(TikaCoreProperties.XML_EXTERNAL_REFERENCE_EMBEDDED));
    }
}
