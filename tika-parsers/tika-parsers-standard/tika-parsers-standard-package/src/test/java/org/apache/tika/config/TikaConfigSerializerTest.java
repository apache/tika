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

import static org.apache.tika.TikaTest.assertContains;
import static org.apache.tika.TikaTest.assertNotContained;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.StringWriter;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.junit.jupiter.api.Test;

public class TikaConfigSerializerTest {

    @Test
    public void testBasicParams() throws Exception {
        TikaConfig tikaConfig = TikaConfig.getDefaultConfig();
        StringWriter writer = new StringWriter();

        TikaConfigSerializer.serialize(tikaConfig, TikaConfigSerializer.Mode.STATIC_FULL,
                writer, StandardCharsets.UTF_8);
        String xml = writer.toString().replaceAll("\\s+", " ");
        String encodingNeedle = "<encodingDetector class=\"org.apache.tika.parser.txt" +
                ".Icu4jEncodingDetector\">" +
                " <params> <param name=\"ignoreCharsets\" type=\"list\"/>";
        assertContains(encodingNeedle, xml);

        String detectorNeedle = "<detector class=\"org.apache.tika.detect.zip.DefaultZipContainerDetector\">" +
                " <params> <param name=\"markLimit\" type=\"int\">16777216</param> </params>";
        assertContains(detectorNeedle, xml);

        String parserNeedle = "<parser class=\"org.apache.tika.parser.pdf.PDFParser\">" +
                " <params> <param name=\"allowExtractionForAccessibility\" " +
                "type=\"bool\">true</param>";

        assertContains(parserNeedle, xml);
        //TODO This is still to be implemented -- we do not want to show the default renderer here
        assertNotContained("<renderer class=\"org.apache.tika.renderer.CompositeRenderer\"/>", xml);

        //For now, make sure that deserialization basically works;
        //add many more unit tests!
        try (InputStream is = new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8))) {
            TikaConfig deserialized = new TikaConfig(is);
        }
    }

    @Test
    public void testTesseractList() throws Exception {
        TikaConfig tikaConfig = new TikaConfig(getPath("tika-config-tesseract-arbitrary.xml"));
        StringWriter writer = new StringWriter();

        TikaConfigSerializer.serialize(tikaConfig, TikaConfigSerializer.Mode.STATIC,
                writer, StandardCharsets.UTF_8);
        String xml = writer.toString().replaceAll("\\s+", " ");
        String needle = "<param name=\"otherTesseractSettings\" type=\"list\"> " +
                "<string>textord_initialx_ile 0.75</string> <string>textord_noise_hfract 0.15625</string> </param>";
        assertContains(needle, xml);
        //For now, make sure that deserialization basically works;
        //add many more unit tests!
        try (InputStream is = new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8))) {
            TikaConfig deserialized = new TikaConfig(is);
        }
    }

    private Path getPath(String config) {
        try {
            return Paths.get(TikaConfigSerializerTest.class.getResource("/configs/" + config)
                    .toURI());
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }
}
