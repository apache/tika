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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import org.apache.tika.detect.DefaultDetector;
import org.apache.tika.detect.Detector;
import org.apache.tika.exception.TikaException;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.HttpHeaders;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;
import org.apache.tika.mime.MediaTypeRegistry;
import org.apache.tika.mime.MimeTypes;
import org.apache.tika.parser.DefaultParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;

/**
 * OSGi registers a DefaultParser and DefaultDetector as dynamic services, and every dynamic
 * DefaultParser/DefaultDetector merges dynamic services in. Neither may end up delegating
 * to itself, or to another dynamic default, and real providers must still be used.
 */
public class DynamicDefaultProviderTest {

    private static final MediaType TYPE = MediaType.application("x-tika-osgi-test");
    private final List<Object> refs = new ArrayList<>();

    private static class MarkerParser implements Parser {
        @Override
        public Set<MediaType> getSupportedTypes(ParseContext context) {
            return Collections.singleton(TYPE);
        }

        @Override
        public void parse(TikaInputStream stream, ContentHandler handler, Metadata metadata,
                          ParseContext context) throws IOException, SAXException, TikaException {
            metadata.set("marker", "parsed");
        }
    }

    private void register(Object service) {
        Object ref = new Object();
        refs.add(ref);
        ServiceLoader.addService(ref, service, 0);
    }

    @AfterEach
    public void unregister() {
        for (Object ref : refs) {
            ServiceLoader.removeService(ref);
        }
        refs.clear();
    }

    private static Metadata parse(Parser parser) throws Exception {
        Metadata metadata = new Metadata();
        metadata.set(HttpHeaders.CONTENT_TYPE, TYPE.toString());
        try (TikaInputStream tis = TikaInputStream.get("x".getBytes(StandardCharsets.UTF_8))) {
            parser.parse(tis, new DefaultHandler(), metadata, new ParseContext());
        }
        return metadata;
    }

    private static MediaType detect(Detector detector) throws Exception {
        try (TikaInputStream tis = TikaInputStream.get("<html><body>hi</body></html>"
                .getBytes(StandardCharsets.UTF_8))) {
            return detector.detect(tis, new Metadata(), new ParseContext());
        }
    }

    // the shape the OSGi bundle registered before this fix: a dynamic DefaultParser
    @Test
    @Timeout(30)
    public void testRegisteredDynamicDefaultParserDoesNotRecurse() throws Exception {
        register(new MarkerParser());
        DefaultParser registered = new DefaultParser();
        register(registered);
        assertEquals("parsed", parse(registered).get("marker"));
        assertEquals("parsed", parse(new DefaultParser()).get("marker"));
    }

    @Test
    @Timeout(30)
    public void testTwoDynamicDefaultParsersDoNotRecurse() throws Exception {
        register(new MarkerParser());
        register(new DefaultParser());
        register(new DefaultParser());
        assertEquals("parsed", parse(new DefaultParser()).get("marker"));
    }

    // what a bundle should register: its own parsers, no dynamic lookup of its own
    @Test
    @Timeout(30)
    public void testStaticDefaultParserStillContributes() throws Exception {
        DefaultParser bundleParser = new DefaultParser(MediaTypeRegistry.getDefaultRegistry(),
                new ServiceLoader(DynamicDefaultProviderTest.class.getClassLoader(), false));
        register(bundleParser);
        assertTrue(new DefaultParser().getAllComponentParsers().contains(bundleParser));
    }

    @Test
    @Timeout(30)
    public void testRegisteredDynamicDefaultDetectorDoesNotRecurse() throws Exception {
        DefaultDetector registered = new DefaultDetector();
        register(registered);
        assertEquals(MediaType.TEXT_HTML, detect(registered));
        assertEquals(MediaType.TEXT_HTML, detect(new DefaultDetector()));
    }

    @Test
    @Timeout(30)
    public void testStaticDefaultDetectorStillContributes() throws Exception {
        DefaultDetector bundleDetector = new DefaultDetector(MimeTypes.getDefaultMimeTypes(),
                new ServiceLoader(DynamicDefaultProviderTest.class.getClassLoader(), false));
        register(bundleDetector);
        assertTrue(new DefaultDetector().getDetectors().contains(bundleDetector));
    }
}
