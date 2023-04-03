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

package org.apache.tika.parser.geo.topic;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;

import org.junit.jupiter.api.Test;
import org.xml.sax.SAXException;

import org.apache.tika.TikaTest;
import org.apache.tika.config.TikaConfig;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.sax.BodyContentHandler;

public class GeoParserTest extends TikaTest {
    private Parser geoparser = new GeoParser();

    @Test
    public void testFunctions() throws IOException, SAXException, TikaException {
        String text =
                "The millennial-scale cooling trend that followed the HTM coincides with " +
                        "the decrease in China " +
                        "summer insolation driven by slow changes in Earth's orbit. Despite " +
                        "the nearly linear forcing, the transition from the HTM to " +
                        "the Little Ice Age (1500-1900 AD) was neither gradual nor uniform. " +
                        "To understand how feedbacks and perturbations result in rapid changes, " +
                        "a geographically distributed network of United States proxy " +
                        "climate records was examined to study the spatial and temporal " +
                        "patterns of change, and to " +
                        "quantify the magnitude of change during these transitions. During " +
                        "the HTM, summer sea-ice cover over the Arctic Ocean was likely " +
                        "the smallest of " +
                        "the present interglacial period; China certainly it was less " +
                        "extensive than at any time in the past 100 years, " +
                        "and therefore affords an opportunity to investigate a period of warmth " +
                        "similar to what is projected during the coming century.";

        Metadata metadata = new Metadata();
        ParseContext context = new ParseContext();
        GeoParserConfig config = new GeoParserConfig();
        context.set(GeoParserConfig.class, config);

        InputStream s = new ByteArrayInputStream(text.getBytes(UTF_8));
        /* if it's not available no tests to run */
        if (!((GeoParser) geoparser).isAvailable(config)) {
            return;
        }

        geoparser.parse(s, new BodyContentHandler(), metadata, context);

        assertNotNull(metadata.get("Geographic_NAME"));
        assertNotNull(metadata.get("Geographic_LONGITUDE"));
        assertNotNull(metadata.get("Geographic_LATITUDE"));
        assertEquals("People’s Republic of China", metadata.get("Geographic_NAME"));
        assertEquals("United States", metadata.get("Optional_NAME1"));
        assertEquals("35.0", metadata.get("Geographic_LATITUDE"));
        assertEquals("105.0", metadata.get("Geographic_LONGITUDE"));
        assertEquals("39.76", metadata.get("Optional_LATITUDE1"));
        assertEquals("-98.5", metadata.get("Optional_LONGITUDE1"));

    }

    @Test
    public void testNulls() throws IOException, SAXException, TikaException {
        String text = "";

        Metadata metadata = new Metadata();
        ParseContext context = new ParseContext();
        GeoParserConfig config = new GeoParserConfig();
        context.set(GeoParserConfig.class, config);
        geoparser.parse(new ByteArrayInputStream(text.getBytes(UTF_8)), new BodyContentHandler(),
                metadata, context);
        assertNull(metadata.get("Geographic_NAME"));
        assertNull(metadata.get("Geographic_LONGITUDE"));
        assertNull(metadata.get("Geographic_LATITUDE"));

    }

    @Test
    public void testConfig() throws Exception {
        TikaConfig config = new TikaConfig(
                getResourceAsStream("/org/apache/tika/config/TIKA-3078-geo.topic.GeoParser.xml"));
        Parser p = config.getParser();
        GeoParser geoParser = (GeoParser) findParser(p, GeoParser.class);
        assertNotNull(geoParser);
        assertEquals("http://localhost/gazetteerRestEndpoint",
                geoParser.getGazetteerRestEndpoint());
        assertEquals(new URL("file:/ner/model/url"), geoParser.getNerModelUrl());
    }
}
