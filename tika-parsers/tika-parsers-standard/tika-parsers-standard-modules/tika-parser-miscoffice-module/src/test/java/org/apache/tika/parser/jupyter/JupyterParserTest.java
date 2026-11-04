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
package org.apache.tika.parser.jupyter;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.xml.sax.helpers.DefaultHandler;

import org.apache.tika.TikaTest;
import org.apache.tika.exception.TikaException;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.HttpHeaders;
import org.apache.tika.metadata.Jupyter;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.parser.ParseContext;

public class JupyterParserTest extends TikaTest {

    private static final String THUMBNAIL =
            TikaCoreProperties.EmbeddedResourceType.THUMBNAIL.toString();
    private static final String INLINE = TikaCoreProperties.EmbeddedResourceType.INLINE.toString();
    private static final String PNG = "\"image/png\": \"iVBORw0KGgo=\"";
    private static final String SVG = "\"image/svg+xml\": \"<svg xmlns='http://www.w3.org/2000/svg'/>\"";

    @Test
    public void testNotebook() throws Exception {
        List<Metadata> metadataList = getRecursiveMetadata("testJupyter.ipynb",
                new JupyterParser(), new Metadata(), new ParseContext(), false);
        Metadata notebook = metadataList.get(0);
        assertEquals("python3", notebook.get(Jupyter.KERNEL));
        assertEquals("python", notebook.get(Jupyter.LANGUAGE));
        assertEquals("4.5", notebook.get(Jupyter.NBFORMAT));
        String content = notebook.get(TikaCoreProperties.TIKA_CONTENT);
        assertContains("Orbit plots", content);
        assertContains("print('loaded 3 bodies')", content);
        assertContains("loaded 3 bodies", content);
        assertContains("42", content);
        assertContains("ZeroDivisionError: division by zero", content);

        assertEquals(3, metadataList.size());
        assertEquals(INLINE, metadataList.get(1).get(TikaCoreProperties.EMBEDDED_RESOURCE_TYPE));
        assertEquals(THUMBNAIL, metadataList.get(2).get(TikaCoreProperties.EMBEDDED_RESOURCE_TYPE));
        assertEquals("image/png", metadataList.get(2).get(HttpHeaders.CONTENT_TYPE));
    }

    @Test
    public void testLastImageWithoutMarker() throws Exception {
        assertEquals(List.of(INLINE, THUMBNAIL, INLINE), imageTypes(notebook(
                code("{}", image(PNG)),
                code("{}", image(PNG), stream()),
                markdown("{}"),
                code("{}", stream(), error()),
                code("{}", "{\"output_type\": \"stream\", \"text\": \"x\"}"),
                raw(image(PNG)))), "the last image of a code cell, outputs read backwards");
    }

    @Test
    public void testTagWins() throws Exception {
        assertEquals(List.of(THUMBNAIL, INLINE), imageTypes(notebook(
                code("{\"tags\": [\"other\", \"nbsphinx-thumbnail\"]}", image(PNG)),
                code("{}", image(PNG)))));
    }

    @Test
    public void testMetadataOutputIndex() throws Exception {
        assertEquals(List.of(THUMBNAIL, INLINE, INLINE), imageTypes(notebook(
                code("{\"nbsphinx-thumbnail\": {\"output-index\": 0, \"tooltip\": \"t\"}}",
                        image(PNG), image(PNG)),
                code("{}", image(PNG)))));
    }

    @Test
    public void testMarkedCellDefaultsToItsLastOutput() throws Exception {
        assertEquals(List.of(INLINE, THUMBNAIL, INLINE), imageTypes(notebook(
                code("{\"nbsphinx-thumbnail\": {}}", image(PNG), image(PNG)),
                code("{}", image(PNG)))));
    }

    @Test
    public void testSvgBeforePng() throws Exception {
        List<Metadata> list = parse(notebook(code("{}", image(PNG + ", " + SVG))));
        assertEquals("image/svg+xml", list.get(1).get(HttpHeaders.CONTENT_TYPE));
        assertEquals(THUMBNAIL, list.get(1).get(TikaCoreProperties.EMBEDDED_RESOURCE_TYPE));
    }

    @Test
    public void testBrokenMarkerMeansNoThumbnail() throws Exception {
        String[][] cases = {
                {notebook(markdownMarked(), code("{}", image(PNG))), "only allowed in code cells"},
                {notebook(code(tag(), image(PNG)), code(tag(), image(PNG))), "only allowed once"},
                {notebook(code("{\"nbsphinx-thumbnail\": {\"size\": 2}}", image(PNG))),
                        "invalid key(s)"},
                {notebook(code("{\"nbsphinx-thumbnail\": true}", image(PNG))), "invalid key(s)"},
                {notebook(code("{\"nbsphinx-thumbnail\": {\"output-index\": 3}}", image(PNG))),
                        "invalid output-index 3"},
                {notebook(code(tag()), code("{}", image(PNG))), "no outputs"},
                {notebook(code(tag(), image(PNG), stream())), "unsupported output type"},
                {notebook(code(tag(), "{\"output_type\": \"display_data\", \"data\": "
                        + "{\"text/html\": \"<b>x</b>\"}}"), code("{}", image(PNG))),
                        "no image in output 0"},
        };
        for (String[] c : cases) {
            List<Metadata> list = parse(c[0]);
            for (Metadata m : list.subList(1, list.size())) {
                assertEquals(INLINE, m.get(TikaCoreProperties.EMBEDDED_RESOURCE_TYPE), c[1]);
            }
            assertContains(c[1], list.get(0).get(TikaCoreProperties.TIKA_META_EXCEPTION_WARNING));
        }
    }

    @Test
    public void testNoImages() throws Exception {
        List<Metadata> list = parse(notebook(code("{}", stream())));
        assertEquals(1, list.size());
        assertNull(list.get(0).get(TikaCoreProperties.TIKA_META_EXCEPTION_WARNING));
    }

    @Test
    public void testNotAnObject() {
        assertThrows(TikaException.class, () -> new JupyterParser().parse(
                TikaInputStream.get("[1, 2]".getBytes(UTF_8)), new DefaultHandler(),
                new Metadata(), new ParseContext()));
    }

    private List<String> imageTypes(String notebook) throws Exception {
        List<String> types = new ArrayList<>();
        List<Metadata> list = parse(notebook);
        for (Metadata m : list.subList(1, list.size())) {
            types.add(m.get(TikaCoreProperties.EMBEDDED_RESOURCE_TYPE));
        }
        return types;
    }

    private List<Metadata> parse(String notebook) throws Exception {
        try (TikaInputStream tis = TikaInputStream.get(notebook.getBytes(UTF_8))) {
            return getRecursiveMetadata(tis, new JupyterParser(), new Metadata(),
                    new ParseContext(), false);
        }
    }

    private static String notebook(String... cells) {
        return "{\"cells\": [" + String.join(", ", cells) + "], \"metadata\": {}, "
                + "\"nbformat\": 4, \"nbformat_minor\": 5}";
    }

    private static String code(String metadata, String... outputs) {
        return "{\"cell_type\": \"code\", \"metadata\": " + metadata + ", \"outputs\": ["
                + String.join(", ", outputs) + "], \"source\": \"x\"}";
    }

    private static String markdown(String metadata) {
        return "{\"cell_type\": \"markdown\", \"metadata\": " + metadata + ", \"source\": \"m\"}";
    }

    private static String markdownMarked() {
        return markdown("{\"tags\": [\"nbsphinx-thumbnail\"]}");
    }

    /** A raw cell with an image output, which is not a code cell and never the thumbnail. */
    private static String raw(String output) {
        return "{\"cell_type\": \"raw\", \"metadata\": {}, \"outputs\": [" + output
                + "], \"source\": \"r\"}";
    }

    private static String tag() {
        return "{\"tags\": [\"nbsphinx-thumbnail\"]}";
    }

    private static String image(String data) {
        return "{\"output_type\": \"display_data\", \"metadata\": {}, \"data\": {" + data + "}}";
    }

    private static String stream() {
        return "{\"output_type\": \"stream\", \"name\": \"stdout\", \"text\": [\"out\\n\"]}";
    }

    private static String error() {
        return "{\"output_type\": \"error\", \"ename\": \"E\", \"evalue\": \"v\", \"traceback\": []}";
    }
}
