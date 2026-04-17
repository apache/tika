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
package org.apache.tika.parser.dwg;



import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.xml.sax.ContentHandler;

import org.apache.tika.TikaTest;
import org.apache.tika.config.loader.TikaLoader;
import org.apache.tika.exception.TikaException;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.DWG;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.parser.CompositeParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.BodyContentHandler;
import org.apache.tika.utils.ProcessUtils;
import org.apache.tika.utils.StringUtils;

public class DWGParserTest extends TikaTest {
    public boolean canRun(DWGParser parser)  {
        String resolved = resolveDwgRead(parser.getDefaultConfig().getDwgReadExecutable());
        if (resolved == null) {
            return false;
        }
        // Point the parser config at the resolved executable so tests "just work"
        // on whichever machine has libredwg installed.
        parser.getDefaultConfig().setDwgReadExecutable(resolved);
        String[] checkCmd = {resolved};
        return ProcessUtils.checkCommand(checkCmd);
    }

    /**
     * Look for dwgread in (1) the DWGREAD_EXE env var, (2) the configured path,
     * (3) on PATH. Returns null if none are found.
     */
    private static String resolveDwgRead(String configPath) {
        String env = System.getenv("DWGREAD_EXE");
        if (!StringUtils.isBlank(env) && Files.isRegularFile(Paths.get(env))) {
            return env;
        }
        if (!StringUtils.isBlank(configPath) && Files.isRegularFile(Paths.get(configPath))) {
            return configPath;
        }
        boolean windows = System.getProperty("os.name")
                .toLowerCase(java.util.Locale.ROOT).contains("win");
        String exeName = windows ? "dwgread.exe" : "dwgread";
        String pathEnv = System.getenv("PATH");
        if (pathEnv != null) {
            for (String dir : pathEnv.split(File.pathSeparator)) {
                if (dir.isEmpty()) {
                    continue;
                }
                Path candidate = Paths.get(dir, exeName);
                if (Files.isRegularFile(candidate)) {
                    return candidate.toString();
                }
            }
        }
        return null;
    }
    @Test
    public void testDWG2000Parser() throws Exception {
        TikaInputStream tis = TikaInputStream.get(
                DWGParserTest.class.getResourceAsStream("/test-documents/testDWG2000.dwg"));
        testParserAlt(tis);
    }

    @Test
    public void testDWG2004Parser() throws Exception {
        TikaInputStream tis = TikaInputStream.get(
                DWGParserTest.class.getResourceAsStream("/test-documents/testDWG2004.dwg"));
        testParser(tis);
    }

    @Test
    public void testDWG2004ParserNoHeaderAddress() throws Exception {
        TikaInputStream tis = TikaInputStream.get(DWGParserTest.class
                .getResourceAsStream("/test-documents/testDWG2004_no_header.dwg"));
        testParserNoHeader(tis);
    }

    @Test
    public void testDWG2007Parser() throws Exception {
        TikaInputStream tis = TikaInputStream.get(
                DWGParserTest.class.getResourceAsStream("/test-documents/testDWG2007.dwg"));
        testParser(tis);
    }

    @Test
    public void testDWG2010Parser() throws Exception {
        TikaInputStream tis = TikaInputStream.get(
                DWGParserTest.class.getResourceAsStream("/test-documents/testDWG2010.dwg"));
        testParser(tis);
    }

    @Test
    public void testDWG2010CustomPropertiesParser() throws Exception {
        // Check that standard parsing works
        TikaInputStream testInput = TikaInputStream.get(DWGParserTest.class
                .getResourceAsStream("/test-documents/testDWG2010_custom_props.dwg"));
        testParser(testInput);

        // Check that custom properties with alternate padding work
        try (TikaInputStream tis = TikaInputStream.get(DWGParserTest.class
                .getResourceAsStream("/test-documents/testDWG2010_custom_props.dwg"))) {
            Metadata metadata = new Metadata();
            ContentHandler handler = new BodyContentHandler();
            new DWGParser().parse(tis, handler, metadata,new ParseContext());

            assertEquals("valueforcustomprop1",
                    metadata.get(DWGParser.DWG_CUSTOM_META_PREFIX + "customprop1"));
            assertEquals("valueforcustomprop2",
                    metadata.get(DWGParser.DWG_CUSTOM_META_PREFIX + "customprop2"));
        }
    }

    @Test
    public void testDWGMechParser() throws Exception {
        String[] types =
                new String[]{"6", "2004", "2004DX", "2005", "2006", "2007", "2008", "2009", "2010",
                        "2011"};
        for (String type : types) {
            TikaInputStream tis = TikaInputStream.get(DWGParserTest.class
                    .getResourceAsStream("/test-documents/testDWGmech" + type + ".dwg"));
            testParserAlt(tis);
        }
    }


    private void testParser(TikaInputStream tis) throws Exception {
        try {
            Metadata metadata = new Metadata();
            ContentHandler handler = new BodyContentHandler();
            new DWGParser().parse(tis, handler, metadata,new ParseContext());

            assertEquals("image/vnd.dwg", metadata.get(Metadata.CONTENT_TYPE));

            assertEquals("The quick brown fox jumps over the lazy dog",
                    metadata.get(TikaCoreProperties.TITLE));
            assertEquals("Gym class featuring a brown fox and lazy dog",
                    metadata.get(TikaCoreProperties.DESCRIPTION));
            assertEquals("Nevin Nollop", metadata.get(TikaCoreProperties.CREATOR));
            assertContains("Pangram, fox, dog",
                    Arrays.asList(metadata.getValues(TikaCoreProperties.SUBJECT)));
            assertEquals("Lorem ipsum", metadata.get(TikaCoreProperties.COMMENTS).substring(0, 11));
            assertEquals("http://www.alfresco.com", metadata.get(TikaCoreProperties.RELATION));

            String content = handler.toString();
            assertContains("The quick brown fox jumps over the lazy dog", content);
            assertContains("Gym class", content);
            assertContains("www.alfresco.com", content);
        } finally {
            tis.close();
        }
    }


    private void testParserNoHeader(TikaInputStream tis) throws Exception {
        try {
            Metadata metadata = new Metadata();
            ContentHandler handler = new BodyContentHandler();
            new DWGParser().parse(tis, handler, metadata,new ParseContext());

            assertEquals("image/vnd.dwg", metadata.get(Metadata.CONTENT_TYPE));

            assertNull(metadata.get(TikaCoreProperties.TITLE));
            assertNull(metadata.get(TikaCoreProperties.DESCRIPTION));
            assertNull(metadata.get(TikaCoreProperties.CREATOR));
            assertNull(metadata.get(TikaCoreProperties.SUBJECT));
            assertNull(metadata.get(TikaCoreProperties.COMMENTS));
            assertNull(metadata.get(TikaCoreProperties.RELATION));

            String content = handler.toString();
            assertEquals("", content);
        } finally {
            tis.close();
        }
    }

    private void testParserAlt(TikaInputStream tis) throws Exception {
        try {
            Metadata metadata = new Metadata();
            ContentHandler handler = new BodyContentHandler();
            new DWGParser().parse(tis, handler, metadata, new ParseContext());

            assertEquals("image/vnd.dwg", metadata.get(Metadata.CONTENT_TYPE));

            assertEquals("Test Title", metadata.get(TikaCoreProperties.TITLE));
            assertEquals("Test Subject", metadata.get(TikaCoreProperties.DESCRIPTION));
            assertEquals("My Author", metadata.get(TikaCoreProperties.CREATOR));
            assertEquals("My keyword1, MyKeyword2", metadata.get(TikaCoreProperties.SUBJECT));
            assertEquals("This is a comment", metadata.get(TikaCoreProperties.COMMENTS));
            assertEquals("bejanpol", metadata.get(TikaCoreProperties.MODIFIER));
            assertEquals("http://mycompany/drawings", metadata.get(TikaCoreProperties.RELATION));
            assertEquals("MyCustomPropertyValue",
                    metadata.get(DWGParser.DWG_CUSTOM_META_PREFIX + "MyCustomProperty"));

            String content = handler.toString();
            assertContains("This is a comment", content);
            assertContains("mycompany", content);
        } finally {
            tis.close();
        }
    }

    @Test
    public void testAC1027() throws Exception {
        Metadata metadata = getXML("testDWG-AC1027.dwg").metadata;
        assertEquals("hlu", metadata.get(TikaCoreProperties.MODIFIER));
    }

    @Test
    public void testAC1032() throws Exception {
        Metadata metadata = getXML("testDWG-AC1032.dwg").metadata;
        assertEquals("jlakshvi", metadata.get(TikaCoreProperties.MODIFIER));
        assertEquals("CUSTOMER'S ADDRESS", metadata.get("dwg-custom:CUSTOMER'S ADDRESS"));
    }
    @Test
    public void testDWGReadexe() throws Exception {
        DWGParser parser =
                (DWGParser) ((CompositeParser) TikaLoader.load(
                                getConfigPath(DWGParserTest.class, "tika-config-dwgRead.json"))
                        .loadParsers())
                        .getAllComponentParsers().get(0);
        assumeTrue(canRun(parser), "Can't run DWGRead.exe");
        List<Metadata> metadataList = getRecursiveMetadata(
                "architectural_-_annotation_scaling_and_multileaders.dwg", parser);
        Metadata root = metadataList.get(0);

        String content = root.get(TikaCoreProperties.TIKA_CONTENT);
        assertContains("ELEV. 11'-9\" TOP OF SECOND FLR.", content);
        // MULTILEADER ctx.content.txt.default_text
        assertContains("EPDM ROOF CONSTRUCTION", content);
        assertContains("O.S.B SHEATHING", content);
        // ATTRIB tag / prompt
        assertContains("Enter sheet number", content);

        // AppInfo
        assertEquals("AppInfoDataList", root.get(DWG.APPLICATION_NAME));
        assertEquals("17.1.51.0", root.get(DWG.APPLICATION_VERSION));
        assertNotNull(root.get(DWG.APPLICATION_COMMENT));
        assertContains("AutoCAD", root.get(DWG.PRODUCT_INFO));

        // Thumbnail embedded as INLINE
        Metadata thumb = null;
        for (int i = 1; i < metadataList.size(); i++) {
            String type = metadataList.get(i).get(TikaCoreProperties.EMBEDDED_RESOURCE_TYPE);
            if (TikaCoreProperties.EmbeddedResourceType.INLINE.name().equals(type)) {
                thumb = metadataList.get(i);
                break;
            }
        }
        assertNotNull(thumb, "Expected an INLINE thumbnail attachment");
    }

    @Test
    public void testDWGReadSummaryInfoMapping() throws Exception {
        DWGParser parser =
                (DWGParser) ((CompositeParser) TikaLoader.load(
                                getConfigPath(DWGParserTest.class, "tika-config-dwgRead.json"))
                        .loadParsers())
                        .getAllComponentParsers().get(0);
        assumeTrue(canRun(parser), "Can't run DWGRead.exe");
        Metadata metadata = getXML("testDWGmech2004.dwg", parser).metadata;
        assertEquals("Test Title", metadata.get(TikaCoreProperties.TITLE));
        assertEquals("Test Subject", metadata.get(TikaCoreProperties.DESCRIPTION));
        assertEquals("My Author", metadata.get(TikaCoreProperties.CREATOR));
        assertEquals("My keyword1, MyKeyword2", metadata.get(TikaCoreProperties.SUBJECT));
        assertEquals("This is a comment", metadata.get(TikaCoreProperties.COMMENTS));
        assertEquals("bejanpol", metadata.get(TikaCoreProperties.MODIFIER));
        assertEquals("http://mycompany/drawings", metadata.get(TikaCoreProperties.RELATION));
    }

    @Test
    public void testDWGReadtimeout() throws Exception {
        DWGParser parser = (DWGParser) ((CompositeParser) TikaLoader.load(
                        getConfigPath(DWGParserTest.class, "tika-config-dwgRead-Timeout.json"))
                .loadParsers())
                .getAllComponentParsers().get(0);
        assumeTrue(canRun(parser), "Can't run DWGRead.exe");
        TikaException thrown = assertThrows(
                TikaException.class,
                () -> getText("architectural_-_annotation_scaling_and_multileaders.dwg", parser),
                "Expected getText() to throw TikaException but it failed"
        );
        assertTrue(thrown.getMessage().contains("Timeout setting exceeded current setting of"));
    }

}
