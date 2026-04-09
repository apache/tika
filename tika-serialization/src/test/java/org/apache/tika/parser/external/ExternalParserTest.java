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
package org.apache.tika.parser.external;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.nio.charset.StandardCharsets;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.xml.sax.ContentHandler;
import org.xml.sax.helpers.DefaultHandler;

import org.apache.tika.TikaTest;
import org.apache.tika.config.loader.TikaLoader;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.CompositeParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.parser.ParserDecorator;
import org.apache.tika.parser.RegexCaptureParser;

public class ExternalParserTest extends TikaTest {

    @Test
    public void testConfigRegexCaptureParser() throws Exception {
        assumeTrue(org.apache.tika.utils.ProcessUtils.checkCommand(new String[]{
                "file", "--version"
        }));
        TikaLoader loader = TikaLoader.load(getConfigPath(getClass(), "TIKA-3557.json"));
        CompositeParser p = (CompositeParser) loader.get(Parser.class);
        assertEquals(1, p.getAllComponentParsers().size());
        Parser parser = p.getAllComponentParsers().get(0);
        // When _mime-include is used, the parser is wrapped in a ParserDecorator
        ExternalParser externalParser = (parser instanceof ParserDecorator)
                ? (ExternalParser) ((ParserDecorator) parser).getWrappedParser()
                : (ExternalParser) parser;

        Parser outputParser = externalParser.getOutputParser();
        assertEquals(RegexCaptureParser.class, outputParser.getClass());

        Metadata m = new Metadata();
        ContentHandler contentHandler = new DefaultHandler();
        String output = "Something\n" +
                "Title: the quick brown fox\n" +
                "Author: jumped over\n" +
                "Created: 10/20/2024";
        try (TikaInputStream tis = TikaInputStream.get(output.getBytes(StandardCharsets.UTF_8))) {
            outputParser.parse(tis, contentHandler, m, new ParseContext());
        }
        assertEquals("the quick brown fox", m.get("title"));
    }

    @Test
    public void testConfigBasic() throws Exception {
        assumeTrue(org.apache.tika.utils.ProcessUtils.checkCommand(new String[]{"file", "--version"}));
        TikaLoader loader = TikaLoader.load(getConfigPath(getClass(), "TIKA-3557-no-output-parser.json"));
        CompositeParser p = (CompositeParser) loader.get(Parser.class);
        assertEquals(1, p.getAllComponentParsers().size());
        Parser parser = p.getAllComponentParsers().get(0);
        // When _mime-include is used, the parser is wrapped in a ParserDecorator
        ExternalParser externalParser = (parser instanceof ParserDecorator)
                ? (ExternalParser) ((ParserDecorator) parser).getWrappedParser()
                : (ExternalParser) parser;

        XMLResult xmlResult = getXML("example.xml", externalParser);
        assertContains("<body>text/xml</body>", xmlResult.xml.replaceAll("[\r\n]", ""));
    }

    @Test
    public void testExifTool() throws Exception {
        assumeTrue(org.apache.tika.utils.ProcessUtils.checkCommand(new String[]{"exiftool",
                "-ver"}));
        TikaLoader loader = TikaLoader.load(getConfigPath(getClass(), "TIKA-3557-exiftool-example.json"));
        Parser p = loader.loadAutoDetectParser();
        //this was the smallest pdf we had
        List<Metadata> metadataList = getRecursiveMetadata("testOverlappingText.pdf", p);
        assertEquals(1, metadataList.size());
        Metadata m = metadataList.get(0);
        assertEquals("application/pdf", m.get("mime"));
        assertEquals("1", m.get("pages"));
        assertEquals("1.4", m.get("pdf:version"));
    }

    @Test
    public void testFfmpegConfig() throws Exception {
        TikaLoader loader = TikaLoader.load(
                getConfigPath(getClass(), "external-parser-ffmpeg.json"));
        CompositeParser p = (CompositeParser) loader.get(Parser.class);
        assertEquals(1, p.getAllComponentParsers().size());
        Parser parser = p.getAllComponentParsers().get(0);
        ExternalParser externalParser = (parser instanceof ParserDecorator)
                ? (ExternalParser) ((ParserDecorator) parser).getWrappedParser()
                : (ExternalParser) parser;

        ExternalParserConfig config = externalParser.getConfig();
        assertNotNull(config.getCheckCommandLine());
        assertEquals(List.of("ffmpeg", "-version"), config.getCheckCommandLine());
        assertEquals(List.of(126, 127), config.getCheckErrorCodes());
        assertNotNull(config.getStderrParser());
        assertEquals(RegexCaptureParser.class, config.getStderrParser().getClass());
        assertTrue(config.isReturnStderr());
    }

    @Test
    public void testSoxConfig() throws Exception {
        TikaLoader loader = TikaLoader.load(
                getConfigPath(getClass(), "external-parser-sox.json"));
        CompositeParser p = (CompositeParser) loader.get(Parser.class);
        assertEquals(1, p.getAllComponentParsers().size());
        Parser parser = p.getAllComponentParsers().get(0);
        ExternalParser externalParser = (parser instanceof ParserDecorator)
                ? (ExternalParser) ((ParserDecorator) parser).getWrappedParser()
                : (ExternalParser) parser;

        ExternalParserConfig config = externalParser.getConfig();
        assertNotNull(config.getCheckCommandLine());
        assertEquals(List.of("sox", "--version"), config.getCheckCommandLine());
        assertNotNull(config.getStderrParser());
        assertEquals(RegexCaptureParser.class, config.getStderrParser().getClass());
    }

    @Test
    public void testStderrParserExtractsMetadata() throws Exception {
        // Simulate what would happen with ffmpeg stderr output
        RegexCaptureParser stderrParser = new RegexCaptureParser();
        // Build a config with a captureMap programmatically
        String ffmpegStderr = "  Duration: 00:02:30.50, start: 0.000000, bitrate: 706 kb/s\n" +
                "    Stream #0:0: Video: h264 (High), yuv420p, 1280x720, 25 fps\n" +
                "    Stream #0:1: Audio: aac, 44100 Hz, 2 channels, fltp\n";

        // Use the regex-capture-parser with the same patterns from the ffmpeg config
        java.util.Map<String, String> captureMap = new java.util.LinkedHashMap<>();
        captureMap.put("xmpDM:duration", "\\s*Duration:\\s*([0-9:\\.]+),.*");
        captureMap.put("xmpDM:audioSampleRate",
                "\\s*Stream.*:.+Audio:.*,\\s+(\\d+)\\s+Hz,.*");

        org.apache.tika.parser.RegexCaptureParserConfig regexConfig =
                new org.apache.tika.parser.RegexCaptureParserConfig();
        regexConfig.setCaptureMap(captureMap);
        RegexCaptureParser parser = new RegexCaptureParser(regexConfig);

        Metadata m = new Metadata();
        try (TikaInputStream tis = TikaInputStream.get(
                ffmpegStderr.getBytes(StandardCharsets.UTF_8))) {
            parser.parse(tis, new DefaultHandler(), m, new ParseContext());
        }
        assertEquals("00:02:30.50", m.get("xmpDM:duration"));
        assertEquals("44100", m.get("xmpDM:audioSampleRate"));
    }

    @Test
    public void testMultiExternalParsers() throws Exception {
        assumeTrue(org.apache.tika.utils.ProcessUtils.checkCommand(
                new String[]{"exiftool", "-ver"}));
        assumeTrue(org.apache.tika.utils.ProcessUtils.checkCommand(
                new String[]{"ffmpeg", "-version"}));

        TikaLoader loader = TikaLoader.load(
                getConfigPath(getClass(), "external-parser-multi.json"));
        CompositeParser composite = (CompositeParser) loader.get(Parser.class);
        List<Parser> allParsers = composite.getAllComponentParsers();

        // Should have two separate external parser instances
        assertEquals(2, allParsers.size(), "Expected 2 external parsers but got " +
                allParsers.size() + ": " + allParsers);

        // Test the exiftool parser on a PDF
        Parser autoDetect = loader.loadAutoDetectParser();
        List<Metadata> metadataList = getRecursiveMetadata("testOverlappingText.pdf", autoDetect);
        assertEquals(1, metadataList.size());
        Metadata m = metadataList.get(0);
        assertEquals("application/pdf", m.get("exiftool:MIMEType"));
        assertNotNull(m.get("exiftool:PageCount"));
        assertNotNull(m.get("exiftool:PDFVersion"));
    }
}
