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
package org.apache.tika.cli;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import org.apache.tika.config.loader.TikaLoader;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.CompositeParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.parser.html.JSoupParser;
import org.apache.tika.parser.pdf.PDFParser;

/**
 * Tests for XmlToJsonConfigConverter.
 * These tests verify that XML configurations are correctly converted to JSON
 * and can be loaded by TikaLoader to produce properly configured parsers.
 */
public class XmlToJsonConfigConverterTest {

    @Test
    public void testSimpleParserConfig(@TempDir Path tempDir) throws Exception {
        Path xmlPath = Paths.get(getClass().getResource("/xml-configs/tika-config-simple.xml").toURI());
        Path jsonPath = tempDir.resolve("simple-config.json");

        // Convert XML to JSON
        XmlToJsonConfigConverter.convert(xmlPath, jsonPath);

        // Verify JSON file was created
        assertTrue(Files.exists(jsonPath));

        // Load the JSON config with TikaLoader
        TikaLoader loader = TikaLoader.load(jsonPath);
        Parser parser = loader.loadParsers();

        assertNotNull(parser);
        assertTrue(parser instanceof CompositeParser);

        // Verify PDF parser is configured
        CompositeParser compositeParser = (CompositeParser) parser;
        ParseContext context = new ParseContext();
        Map<MediaType, Parser> parsers = compositeParser.getParsers(context);

        // Check that PDF parser is present
        MediaType pdfType = MediaType.parse("application/pdf");
        assertTrue(parsers.containsKey(pdfType), "PDF parser should be configured");

        Parser pdfParser = parsers.get(pdfType);
        assertTrue(pdfParser instanceof PDFParser, "Parser for PDF should be PDFParser");

        // The actual parser configuration (sortByPosition, extractInlineImages, etc.)
        // is tested by the parser's behavior, not directly accessible here
    }

    @Test
    public void testParserWithExcludes(@TempDir Path tempDir) throws Exception {
        Path xmlPath = Paths.get(getClass().getResource("/xml-configs/tika-config-with-excludes.xml").toURI());
        Path jsonPath = tempDir.resolve("excludes-config.json");

        // Convert XML to JSON
        XmlToJsonConfigConverter.convert(xmlPath, jsonPath);

        String json = new String(Files.readAllBytes(jsonPath), StandardCharsets.UTF_8);

        // Verify exclude is at the correct level (with underscore prefix)
        assertTrue(json.contains("\"_exclude\""), "Should have _exclude array");
        assertFalse(json.contains("\"_decorate\""), "_decorate should not be used for parser excludes");
        assertTrue(json.contains("\"jsoup-parser\""), "Should exclude jsoup-parser");
        assertTrue(json.contains("\"pdf-parser\""), "Should exclude pdf-parser");

        // Load the JSON config with TikaLoader
        TikaLoader loader = TikaLoader.load(jsonPath);
        Parser parser = loader.loadParsers();

        assertNotNull(parser);
        assertTrue(parser instanceof CompositeParser);

        // Verify parsers are configured
        CompositeParser compositeParser = (CompositeParser) parser;
        for (Parser p : ((CompositeParser) parser).getAllComponentParsers()) {
            if (p instanceof PDFParser) {
                fail("pdf parser should have been excluded");
            }
        }
        ParseContext context = new ParseContext();
        Map<MediaType, Parser> parsers = compositeParser.getParsers(context);

        // Check that HTML parser is present (JSoupParser should be configured)
        MediaType htmlType = MediaType.parse("text/html");
        assertTrue(parsers.containsKey(htmlType), "HTML parser should be configured");

        Parser htmlParser = parsers.get(htmlType);
        // JSoupParser extends HtmlParser, so this checks for the correct family
        assertTrue(htmlParser instanceof JSoupParser, "Parser for HTML should be HtmlParser or JSoupParser");
    }

    @Test
    public void testNumericTypes(@TempDir Path tempDir) throws Exception {
        Path xmlPath = Paths.get(getClass().getResource("/xml-configs/tika-config-numeric-types.xml").toURI());
        Path jsonPath = tempDir.resolve("numeric-config.json");

        // Convert XML to JSON
        XmlToJsonConfigConverter.convert(xmlPath, jsonPath);

        // Verify JSON file was created and contains proper numeric types
        assertTrue(Files.exists(jsonPath));

        // Read the JSON to verify numeric types are preserved
        String json = new String(Files.readAllBytes(jsonPath), StandardCharsets.UTF_8);

        // Verify numbers are not quoted (they should appear as: "density": 300, not "density": "300")
        assertTrue(json.contains("\"density\" : 300"), "density should be numeric, not string");
        assertFalse(json.contains("\"timeout\" : \"300\""), "timeout should not be a quoted string");

        // Load the JSON config with TikaLoader to verify it's valid
        TikaLoader loader = TikaLoader.load(jsonPath);
        Parser parser = loader.loadParsers();
        assertNotNull(parser);
    }

    @Test
    public void testFileConversion(@TempDir Path tempDir) throws Exception {
        Path xmlPath = Paths.get(getClass().getResource("/xml-configs/tika-config-simple.xml").toURI());
        Path jsonPath = tempDir.resolve("output.json");

        // Test the Path-based conversion method
        XmlToJsonConfigConverter.convert(xmlPath, jsonPath);

        // Verify file exists
        assertTrue(Files.exists(jsonPath));

        // Verify it can be loaded by TikaLoader
        TikaLoader loader = TikaLoader.load(jsonPath);
        Parser parser = loader.loadParsers();
        assertNotNull(parser);
        assertTrue(parser instanceof CompositeParser);
    }

    @Test
    public void testClassNameConversion(@TempDir Path tempDir) throws Exception {
        Path xmlPath = Paths.get(getClass().getResource("/xml-configs/tika-config-simple.xml").toURI());
        Path jsonPath = tempDir.resolve("classname-config.json");

        // Convert XML to JSON
        XmlToJsonConfigConverter.convert(xmlPath, jsonPath);

        // Read JSON and verify component name conversion
        String json = new String(Files.readAllBytes(jsonPath), StandardCharsets.UTF_8);

        // Verify that PDFParser was converted to pdf-parser (kebab-case)
        assertTrue(json.contains("\"pdf-parser\""), "PDFParser should be converted to pdf-parser");

        // Verify the config loads successfully
        TikaLoader loader = TikaLoader.load(jsonPath);
        Parser parser = loader.loadParsers();
        assertNotNull(parser);
    }

    @Test
    public void testAutoDetectParserLoading(@TempDir Path tempDir) throws Exception {
        Path xmlPath = Paths.get(getClass().getResource("/xml-configs/tika-config-simple.xml").toURI());
        Path jsonPath = tempDir.resolve("autodetect-config.json");

        // Convert XML to JSON
        XmlToJsonConfigConverter.convert(xmlPath, jsonPath);

        // Load via TikaLoader and get AutoDetectParser
        TikaLoader loader = TikaLoader.load(jsonPath);
        Parser autoDetectParser = loader.loadAutoDetectParser();

        assertNotNull(autoDetectParser);

        // Verify it supports PDF type
        ParseContext context = new ParseContext();
        MediaType pdfType = MediaType.parse("application/pdf");
        assertTrue(autoDetectParser.getSupportedTypes(context).contains(pdfType),
                "AutoDetectParser should support PDF");
    }

    @Test
    public void testRedundantExclusionWarning(@TempDir Path tempDir) throws Exception {
        // This test demonstrates the old pattern where users excluded parsers from default-parser
        // and then configured those same parsers separately. The converter will log an INFO message
        // informing users that the exclusion is redundant.
        Path xmlPath = Paths.get(getClass().getResource("/xml-configs/tika-config-redundant-exclusion.xml").toURI());
        Path jsonPath = tempDir.resolve("redundant-config.json");

        // Convert XML to JSON (this will log the INFO message about redundant exclusions)
        XmlToJsonConfigConverter.convert(xmlPath, jsonPath);

        String json = new String(Files.readAllBytes(jsonPath), StandardCharsets.UTF_8);

        // Verify the JSON still contains the exclusions (we don't remove them, just inform)
        assertTrue(json.contains("\"_exclude\""), "Should still have _exclude array");
        assertTrue(json.contains("\"pdf-parser\""), "Should have pdf-parser configured");
        assertTrue(json.contains("\"jsoup-parser\""), "Should have jsoup-parser configured");

        // Verify it loads correctly via TikaLoader
        TikaLoader loader = TikaLoader.load(jsonPath);
        Parser parser = loader.loadParsers();
        assertNotNull(parser);
        assertTrue(parser instanceof CompositeParser);

        // Verify both parsers are configured and working
        CompositeParser compositeParser = (CompositeParser) parser;
        ParseContext context = new ParseContext();
        Map<MediaType, Parser> parsers = compositeParser.getParsers(context);

        MediaType pdfType = MediaType.parse("application/pdf");
        assertTrue(parsers.containsKey(pdfType), "PDF parser should be configured");

        MediaType htmlType = MediaType.parse("text/html");
        assertTrue(parsers.containsKey(htmlType), "HTML parser should be configured");
    }

    @Test
    public void testTesseractArbitrarySettings(@TempDir Path tempDir) throws Exception {
        // Test the special case conversion of TesseractOCR's otherTesseractSettings
        String xmlConfig = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<properties>\n" +
                "    <parsers>\n" +
                "        <parser class=\"org.apache.tika.parser.ocr.TesseractOCRParser\">\n" +
                "            <params>\n" +
                "                <param name=\"otherTesseractSettings\" type=\"list\">\n" +
                "                    <string>textord_initialx_ile 0.75</string>\n" +
                "                    <string>textord_noise_hfract 0.15625</string>\n" +
                "                </param>\n" +
                "            </params>\n" +
                "        </parser>\n" +
                "    </parsers>\n" +
                "</properties>";

        Path xmlPath = tempDir.resolve("tesseract-arbitrary.xml");
        Path jsonPath = tempDir.resolve("tesseract-arbitrary.json");
        Files.write(xmlPath, xmlConfig.getBytes(StandardCharsets.UTF_8));

        // Convert
        XmlToJsonConfigConverter.convert(xmlPath, jsonPath);

        String json = new String(Files.readAllBytes(jsonPath), StandardCharsets.UTF_8);

        // Verify conversion: list of space-delimited pairs -> map
        assertTrue(json.contains("\"otherTesseractConfig\""),
                "Should convert to otherTesseractConfig");
        assertFalse(json.contains("\"otherTesseractSettings\""),
                "Should not keep old parameter name");
        assertTrue(json.contains("\"textord_initialx_ile\" : \"0.75\""),
                "Should parse key-value pairs correctly");
        assertTrue(json.contains("\"textord_noise_hfract\" : \"0.15625\""),
                "Should parse second pair");

        // Verify it loads via TikaLoader without errors
        TikaLoader loader = TikaLoader.load(jsonPath);
        Parser parser = loader.loadParsers();
        assertNotNull(parser);
    }

    @Test
    public void testListAndMapParameterTypes(@TempDir Path tempDir) throws Exception {
        Path xmlPath = Paths.get(getClass().getResource("/xml-configs/tika-config-list-map-types.xml").toURI());
        Path jsonPath = tempDir.resolve("list-map-config.json");

        // Convert XML to JSON
        XmlToJsonConfigConverter.convert(xmlPath, jsonPath);

        String json = new String(Files.readAllBytes(jsonPath), StandardCharsets.UTF_8);

        // Verify otherTesseractSettings (list) is converted to otherTesseractConfig (map)
        // This is a special case where space-delimited key-value pairs are parsed
        assertTrue(json.contains("\"otherTesseractConfig\" : {"),
                "Should convert otherTesseractSettings list to otherTesseractConfig map");
        assertFalse(json.contains("\"otherTesseractSettings\""),
                "Should not have old otherTesseractSettings name");
        assertTrue(json.contains("\"textord_initialx_ile\" : \"0.75\""),
                "Should parse first key-value pair");
        assertTrue(json.contains("\"textord_noise_hfract\" : \"0.15625\""),
                "Should parse second key-value pair");
        assertTrue(json.contains("\"preserve_interword_spaces\" : \"1\""),
                "Should parse third key-value pair");

        // Verify regular parameters still work
        assertTrue(json.contains("\"timeoutSeconds\" : 300"), "Should have integer parameter");
        assertTrue(json.contains("\"enableImagePreprocessing\" : true"), "Should have boolean parameter");
        assertTrue(json.contains("\"language\" : \"eng\""), "Should have string parameter");

        // Verify it loads correctly via TikaLoader
        TikaLoader loader = TikaLoader.load(jsonPath);
        Parser parser = loader.loadParsers();
        assertNotNull(parser);
        assertTrue(parser instanceof CompositeParser);
    }
}
