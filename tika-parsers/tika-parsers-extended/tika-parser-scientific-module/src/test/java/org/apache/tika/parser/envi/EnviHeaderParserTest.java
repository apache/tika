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

package org.apache.tika.parser.envi;

import static org.apache.tika.TikaTest.assertContains;
import static org.junit.Assert.assertNotNull;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import org.apache.commons.io.IOUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.sax.ToXMLContentHandler;

/**
 * Test cases to exercise the {@link EnviHeaderParser}.
 */
public class EnviHeaderParserTest {

    private Parser parser;
    private ToXMLContentHandler handler;
    private Metadata metadata;

    @Before
    public void setUp() {
        setParser(new EnviHeaderParser());
        setHandler(new ToXMLContentHandler());
        setMetadata(new Metadata());
    }

    @After
    public void tearDown() {
        setParser(null);
        setHandler(null);
        setMetadata(null);
    }

    @Test
    public void testParseGlobalMetadata() throws Exception {

        try (InputStream stream = EnviHeaderParser.class
                .getResourceAsStream("/test-documents/envi_test_header.hdr")) {
            assertNotNull("Test ENVI file 'envi_test_header.hdr' not found", stream);
            parser.parse(stream, handler, metadata, new ParseContext());
        }

        // Check content of test file
        String content = handler.toString();
        assertContains("<body><p>ENVI</p>", content);
        assertContains("<p>samples = 2400</p>", content);
        assertContains("<p>lines   = 2400</p>", content);
        assertContains(
                "<p>map info = {Sinusoidal, 1.5000, 1.5000, -10007091.3643, " +
                        "5559289.2856, 4.6331271653e+02, 4.6331271653e+02, , units=Meters}</p>",
                content);
        assertContains("content=\"application/envi.hdr\"", content);
        assertContains(
                "projection info = {16, 6371007.2, 0.000000, 0.0, 0.0, Sinusoidal, units=Meters}",
                content);
    }

    @Test
    public void testParseGlobalMetadataMultiLineMetadataValues() throws Exception {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        IOUtils.copy(EnviHeaderParserTest.class.getResourceAsStream("/ground-truth" +
                "/EnviHeaderGroundTruth.txt"), bos);
        String expected = new String(bos.toByteArray(), StandardCharsets.UTF_8).trim();
        Parser parser = new EnviHeaderParser();
        ToXMLContentHandler handler = new ToXMLContentHandler();
        Metadata metadata = new Metadata();

        try (InputStream stream = EnviHeaderParser.class
                .getResourceAsStream("/test-documents/ang20150420t182050_corr_v1e_img.hdr")) {
            assertNotNull("Test ENVI file 'ang20150420t182050_corr_v1e_img.hdr' not found", stream);
            parser.parse(stream, handler, metadata, new ParseContext());
        }

        // Check content of test file
        String content = handler.toString();
        assertContains("<body><p>ENVI</p>", content);
        assertContains(
                "<p>description = {  Georeferenced Image built from input GLT. " +
                        "[Wed Jun 10 04:37:54 2015] [Wed  Jun 10 04:48:52 2015]}</p>",
                content);
        assertContains("<p>samples = 739</p>", content);
        assertContains("<p>lat/lon = { 36.79077627261556, -108.48370867914815 }</p>", content);
        assertContains(
                "<p>map info = { UTM , 1.000 , 1.000 , 724522.127 , 4074620.759 , " +
                        "1.1000000000e+00 , 1.1000000000e+00 , 12 , North , " +
                        "WGS-84 , units=Meters , " +
                        "rotation=75.00000000 }</p>",
                content);
        assertContains( expected,
                content);
    }

    /**
     * @return the parser
     */
    public Parser getParser() {
        return parser;
    }

    /**
     * @param parser the parser to set
     */
    public void setParser(Parser parser) {
        this.parser = parser;
    }

    /**
     * @return the handler
     */
    public ToXMLContentHandler getHandler() {
        return handler;
    }

    /**
     * @param handler the handler to set
     */
    public void setHandler(ToXMLContentHandler handler) {
        this.handler = handler;
    }

    /**
     * @return the metadata
     */
    public Metadata getMetadata() {
        return metadata;
    }

    /**
     * @param metadata the metadata to set
     */
    public void setMetadata(Metadata metadata) {
        this.metadata = metadata;
    }
}
