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
package org.apache.tika.parser.geogebra;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.junit.jupiter.api.Test;

import org.apache.tika.TikaTest;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.HttpHeaders;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.PagedText;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.metadata.TikaPagedText;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;

public class GeoGebraParserTest extends TikaTest {

    private static final String XML_HEAD = "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n";

    private static final String PNG = "\u0089PNG";

    @Test
    public void testGGB() throws Exception {
        List<Metadata> metadataList = getRecursiveMetadata("testGeoGebra.ggb");
        Metadata metadata = metadataList.get(0);
        assertEquals("application/vnd.geogebra.file", metadata.get(HttpHeaders.CONTENT_TYPE));
        assertEquals("Pythagorean theorem", metadata.get(TikaCoreProperties.TITLE));
        assertEquals("Ada Lovelace", metadata.get(TikaCoreProperties.CREATOR));
        assertEquals("15 January 2026", metadata.get(GeoGebraParser.DATE));
        assertEquals("classic", metadata.get(GeoGebraParser.APP_NAME));
        assertEquals("5.0.815.0", metadata.get(GeoGebraParser.APP_VERSION));
        assertEquals("5.0", metadata.get(GeoGebraParser.FORMAT_VERSION));
        assertEquals("0c34397e-e3e1-4d1c-9cb6-fe6e54b1e88f", metadata.get(GeoGebraParser.ID));

        String content = metadata.get(TikaCoreProperties.TIKA_CONTENT);
        assertContains("In a right triangle a² + b² = c²", content);
        assertContains("Theorem statement", content);
        assertContains("Drag the vertices to explore.", content);

        //the embedded macro is parsed alongside geogebra.xml
        assertEquals("Midpoint tool", metadata.get(GeoGebraParser.TOOL_NAME));
        assertContains("Select two points to construct their midpoint", content);

        assertEquals(3, metadataList.size());
        Metadata thumbnail = byName(metadataList, "geogebra_thumbnail.png");
        assertEquals("image/png", thumbnail.get(HttpHeaders.CONTENT_TYPE));
        assertEquals(TikaCoreProperties.EmbeddedResourceType.THUMBNAIL.toString(),
                thumbnail.get(TikaCoreProperties.EMBEDDED_RESOURCE_TYPE));
        //the document script is user code
        Metadata script = byName(metadataList, "geogebra_javascript.js");
        assertEquals(TikaCoreProperties.EmbeddedResourceType.MACRO.toString(),
                script.get(TikaCoreProperties.EMBEDDED_RESOURCE_TYPE));
    }

    @Test
    public void testGGS() throws Exception {
        List<Metadata> metadataList = getRecursiveMetadata("testGeoGebraSlides.ggs");
        Metadata metadata = metadataList.get(0);
        assertEquals("application/vnd.geogebra.slides", metadata.get(HttpHeaders.CONTENT_TYPE));
        assertEquals("notes", metadata.get(GeoGebraParser.APP_NAME));
        assertEquals(2, (int) metadata.getInt(PagedText.N_PAGES));

        //structure.json orders _slide1 before _slide0
        String content = metadata.get(TikaCoreProperties.TIKA_CONTENT);
        assertContains("First slide text", content);
        assertContains("Second slide text", content);
        assertTrue(content.indexOf("First slide text") < content.indexOf("Second slide text"),
                "slide order should follow structure.json");
        assertContains("<div class=\"slide\">", content);

        //only the first slide's thumbnail is emitted, marked THUMBNAIL
        assertEquals(5, metadataList.size());
        Metadata thumbnail = byName(metadataList, "_slide1/geogebra_thumbnail.png");
        assertEquals("image/png", thumbnail.get(HttpHeaders.CONTENT_TYPE));
        assertEquals(TikaCoreProperties.EmbeddedResourceType.THUMBNAIL.toString(),
                thumbnail.get(TikaCoreProperties.EMBEDDED_RESOURCE_TYPE));
        assertNull(byName(metadataList, "_slide0/geogebra_thumbnail.png"));

        //the inserted picture is emitted under its full zip entry name, as an
        //inline image anchored to its slide (the second page)
        Metadata picture = byName(metadataList, "_slide0/8c6976e5b541/photo.png");
        assertEquals("_slide0/8c6976e5b541/photo.png",
                picture.get(TikaCoreProperties.INTERNAL_PATH));
        assertEquals("image/png", picture.get(HttpHeaders.CONTENT_TYPE));
        assertEquals(TikaCoreProperties.EmbeddedResourceType.INLINE.toString(),
                picture.get(TikaCoreProperties.EMBEDDED_RESOURCE_TYPE));
        assertEquals("2", picture.get(TikaPagedText.PAGE_NUMBERS));

        Metadata script = byName(metadataList, "_slide0/geogebra_javascript.js");
        assertEquals(TikaCoreProperties.EmbeddedResourceType.MACRO.toString(),
                script.get(TikaCoreProperties.EMBEDDED_RESOURCE_TYPE));
    }

    @Test
    public void testGGT() throws Exception {
        List<Metadata> metadataList = getRecursiveMetadata("testGeoGebraTool.ggt");
        Metadata metadata = metadataList.get(0);
        assertEquals("application/vnd.geogebra.tool", metadata.get(HttpHeaders.CONTENT_TYPE));
        assertEquals("Midpoint tool", metadata.get(GeoGebraParser.TOOL_NAME));
        assertEquals("classic", metadata.get(GeoGebraParser.APP_NAME));

        String content = metadata.get(TikaCoreProperties.TIKA_CONTENT);
        assertContains("Midpoint tool", content);
        assertContains("Select two points to construct their midpoint", content);

        //no thumbnail in this tool file; the macro's own construction carries
        //no document metadata
        assertEquals(1, metadataList.size());
        assertNull(metadata.get(TikaCoreProperties.TITLE));
    }

    @Test
    public void testMacroDoesNotOverrideWorksheetMetadata() throws Exception {
        Map<String, String> entries = new LinkedHashMap<>();
        entries.put("geogebra.xml", geogebra("classic", "5.0.1.0", "doc-id",
                "<construction title=\"Worksheet\" author=\"Ada\" date=\"today\">"
                        + "<expression label=\"t\" exp=\"&quot;Body&quot;\"/></construction>"));
        entries.put("geogebra_macro.xml", geogebra("other", "9.9.9.9", "macro-id",
                "<macro cmdName=\"Mid\" toolName=\"Midpoint\" toolHelp=\"Two points\">"
                        + "<construction title=\"Macro\" author=\"Bob\" date=\"never\"/></macro>"));
        Metadata metadata = parse(entries).get(0);
        assertEquals("Worksheet", metadata.get(TikaCoreProperties.TITLE));
        assertEquals("Ada", metadata.get(TikaCoreProperties.CREATOR));
        assertEquals("today", metadata.get(GeoGebraParser.DATE));
        assertEquals("classic", metadata.get(GeoGebraParser.APP_NAME));
        assertEquals("5.0.1.0", metadata.get(GeoGebraParser.APP_VERSION));
        assertEquals("doc-id", metadata.get(GeoGebraParser.ID));
        assertEquals("Midpoint", metadata.get(GeoGebraParser.TOOL_NAME));
        String content = metadata.get(TikaCoreProperties.TIKA_CONTENT);
        assertContains("Body", content);
        assertContains("Two points", content);
    }

    @Test
    public void testTextExpressions() throws Exception {
        Map<String, String> entries = new LinkedHashMap<>();
        entries.put("geogebra.xml", geogebra("classic", "5.0.1.0", "id", "<construction>"
                + "<expression label=\"t1\" exp=\"&quot;plain text&quot;\"/>"
                //a dynamic text: literals combined with a value
                + "<expression label=\"t2\" exp=\"&quot;Area = &quot; + a\"/>"
                + "<expression label=\"t3\" exp=\"&quot;a&quot;+&quot;b&quot;\"/>"
                //geometry, not text
                + "<expression label=\"f\" exp=\"x^2 + 1\"/>"
                + "<expression label=\"empty\" exp=\"&quot;&quot;\"/>"
                + "</construction>"));
        String content = parse(entries).get(0).get(TikaCoreProperties.TIKA_CONTENT);
        assertContains("<p>plain text</p>", content);
        assertContains("<p>Area =</p>", content);
        assertContains("<p>ab</p>", content);
        assertNotContained("x^2", content);
    }

    @Test
    public void testSlidesWithoutStructureJson() throws Exception {
        Map<String, String> entries = new LinkedHashMap<>();
        entries.put("_slide10/geogebra.xml", slide("Tenth"));
        entries.put("_slide007/geogebra.xml", slide("Seventh"));
        entries.put("_slide2/geogebra.xml", slide("Second"));
        Metadata metadata = parse(entries, new GeoGebraParser()).get(0);
        assertEquals(3, (int) metadata.getInt(PagedText.N_PAGES));
        //numeric order, leading zeros aside
        String content = metadata.get(TikaCoreProperties.TIKA_CONTENT);
        assertTrue(content.indexOf("Second") < content.indexOf("Seventh"), content);
        assertTrue(content.indexOf("Seventh") < content.indexOf("Tenth"), content);
    }

    @Test
    public void testStructureJsonSuppliesOrderOnly() throws Exception {
        Map<String, String> entries = new LinkedHashMap<>();
        //a structure.json that is not JSON at all, and one slide it would not list
        entries.put("structure.json", "not json");
        entries.put("_slide0/geogebra.xml", slide("Zero"));
        entries.put("_slide1/geogebra.xml", slide("One"));
        Metadata metadata = parse(entries).get(0);
        assertEquals(2, (int) metadata.getInt(PagedText.N_PAGES));
        String content = metadata.get(TikaCoreProperties.TIKA_CONTENT);
        assertContains("Zero", content);
        assertContains("One", content);
    }

    @Test
    public void testMalformedSlideDoesNotAbortTheRest() throws Exception {
        Map<String, String> entries = new LinkedHashMap<>();
        entries.put("structure.json", "{\"chapters\":[{\"pages\":[{\"elements\":"
                + "[{\"id\":\"_slide0\"},{\"id\":\"_slide1\"}]}]}]}");
        entries.put("_slide0/geogebra.xml", XML_HEAD + "<geogebra><construction>"
                + "<expression label=\"t\" exp=\"&quot;Broken&quot;\"/><unclosed>");
        entries.put("_slide1/geogebra.xml", slide("Fine"));
        entries.put("_slide1/geogebra_thumbnail.png", PNG);
        List<Metadata> metadataList = parse(entries);
        Metadata metadata = metadataList.get(0);
        String content = metadata.get(TikaCoreProperties.TIKA_CONTENT);
        assertContains("Fine", content);
        //the slide div was closed and the failure recorded
        assertContains("</div>", content);
        assertNotNull(metadata.get(TikaCoreProperties.TIKA_META_EXCEPTION_EMBEDDED_STREAM));
        //the thumbnail that follows is still emitted
        assertNotNull(byName(metadataList, "_slide1/geogebra_thumbnail.png"));
    }

    @Test
    public void testThumbnailFallsBackToNextSlide() throws Exception {
        Map<String, String> entries = new LinkedHashMap<>();
        entries.put("_slide0/geogebra.xml", slide("Zero"));
        entries.put("_slide1/geogebra.xml", slide("One"));
        entries.put("_slide1/geogebra_thumbnail.png", PNG);
        entries.put("_slide2/geogebra.xml", slide("Two"));
        entries.put("_slide2/geogebra_thumbnail.png", PNG);
        List<Metadata> metadataList = parse(entries, new GeoGebraParser());
        assertEquals(2, metadataList.size());
        Metadata thumbnail = byName(metadataList, "_slide1/geogebra_thumbnail.png");
        assertEquals(TikaCoreProperties.EmbeddedResourceType.THUMBNAIL.toString(),
                thumbnail.get(TikaCoreProperties.EMBEDDED_RESOURCE_TYPE));
    }

    @Test
    public void testRootWorksheetAlongsideSlides() throws Exception {
        Map<String, String> entries = new LinkedHashMap<>();
        entries.put("geogebra.xml", geogebra("notes", "5.2.0.0", "root-id",
                "<construction title=\"Root\"><expression label=\"t\" "
                        + "exp=\"&quot;Root text&quot;\"/></construction>"));
        entries.put("_slide0/geogebra.xml", slide("Slide text"));
        Metadata metadata = parse(entries).get(0);
        assertEquals("Root", metadata.get(TikaCoreProperties.TITLE));
        String content = metadata.get(TikaCoreProperties.TIKA_CONTENT);
        assertContains("Root text", content);
        assertContains("Slide text", content);
    }

    @Test
    public void testHousekeepingNamesOnlyMatchAtKnownPlaces() throws Exception {
        Map<String, String> entries = new LinkedHashMap<>();
        entries.put("geogebra.xml", geogebra("classic", "5.0.1.0", "id", "<construction/>"));
        //a script hidden in a subdirectory is not the document script
        entries.put("dir/geogebra_javascript.js", "alert(1)");
        entries.put("dir/geogebra.xml", "<geogebra/>");
        List<Metadata> metadataList = parse(entries);
        assertEquals(3, metadataList.size());
        Metadata script = byName(metadataList, "dir/geogebra_javascript.js");
        assertEquals(TikaCoreProperties.EmbeddedResourceType.ATTACHMENT.toString(),
                script.get(TikaCoreProperties.EMBEDDED_RESOURCE_TYPE));
        assertNotNull(byName(metadataList, "dir/geogebra.xml"));
    }

    /**
     * A crafted slide id can carry more digits than an int holds; sorting the
     * slide ids must not throw a NumberFormatException out of parse().
     */
    @Test
    public void testSlideNumberLargerThanIntParses() throws Exception {
        Map<String, String> entries = new LinkedHashMap<>();
        entries.put("structure.json", "{\"chapters\":[{\"pages\":[{\"elements\":"
                + "[{\"id\":\"_slide0\"},{\"id\":\"_slide99999999999\"}]}]}]}");
        //two slides so that sorting actually compares the ids
        entries.put("_slide0/geogebra.xml", "<geogebra format=\"5.0\"></geogebra>");
        entries.put("_slide99999999999/geogebra.xml", "<geogebra format=\"5.0\"></geogebra>");
        assertEquals("application/vnd.geogebra.slides",
                parse(entries).get(0).get(HttpHeaders.CONTENT_TYPE));
    }

    private static String geogebra(String app, String version, String id, String body) {
        return XML_HEAD + "<geogebra format=\"5.0\" version=\"" + version + "\" app=\"" + app
                + "\" id=\"" + id + "\">" + body + "</geogebra>";
    }

    private static String slide(String text) {
        return geogebra("notes", "5.2.0.0", "slide-" + text, "<construction>"
                + "<element type=\"inlinetext\" label=\"a\"><content val=\"[{&quot;text&quot;:&quot;"
                + text + "\\n&quot;}]\"/></element></construction>");
    }

    private List<Metadata> parse(Map<String, String> entries) throws Exception {
        return parse(entries, null);
    }

    /**
     * Parses an in-memory zip, through detection or, for a container that
     * detection would not attribute to GeoGebra (a slides file without
     * structure.json), with the parser directly.
     */
    private List<Metadata> parse(Map<String, String> entries, Parser parser) throws Exception {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(bos)) {
            for (Map.Entry<String, String> e : entries.entrySet()) {
                zos.putNextEntry(new ZipEntry(e.getKey()));
                zos.write(e.getValue().getBytes(StandardCharsets.ISO_8859_1));
                zos.closeEntry();
            }
        }
        try (TikaInputStream tis = TikaInputStream.get(bos.toByteArray())) {
            if (parser == null) {
                return getRecursiveMetadata(tis, new Metadata(), new ParseContext(), false);
            }
            return getRecursiveMetadata(tis, parser, new Metadata(), new ParseContext(), false);
        }
    }

    private static Metadata byName(List<Metadata> metadataList, String name) {
        for (Metadata m : metadataList) {
            if (name.equals(m.get(TikaCoreProperties.RESOURCE_NAME_KEY))) {
                return m;
            }
        }
        return null;
    }
}
