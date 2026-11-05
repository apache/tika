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
package org.apache.tika.parser.pkg;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import javax.imageio.ImageIO;

import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;
import org.junit.jupiter.api.Test;
import org.xml.sax.ContentHandler;

import org.apache.tika.extractor.EmbeddedDocumentExtractor;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.HttpHeaders;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.BodyContentHandler;

/**
 * Test case for parsing zip files.
 */
public class ZipParserTest extends AbstractPkgTest {

    @Test
    public void testZipParsing() throws Exception {
        ContentHandler handler = new BodyContentHandler();
        Metadata metadata = new Metadata();

        try (TikaInputStream tis = getResourceAsStream("/test-documents/test-documents.zip")) {
            AUTO_DETECT_PARSER.parse(tis, handler, metadata, recursingContext);
        }

        assertEquals("application/zip", metadata.get(HttpHeaders.CONTENT_TYPE));
        String content = handler.toString();
        assertContains("testEXCEL.xls", content);
        assertContains("Sample Excel Worksheet", content);
        assertContains("testHTML.html", content);
        assertContains("Test Indexation Html", content);
        assertContains("testOpenOffice2.odt", content);
        assertContains("This is a sample Open Office document", content);
        assertContains("testPDF.pdf", content);
        assertContains("Apache Tika", content);
        assertContains("testPPT.ppt", content);
        assertContains("Sample Powerpoint Slide", content);
        assertContains("testRTF.rtf", content);
        assertContains("indexation Word", content);
        assertContains("testTXT.txt", content);
        assertContains("Test d'indexation de Txt", content);
        assertContains("testWORD.doc", content);
        assertContains("This is a sample Microsoft Word Document", content);
        assertContains("testXML.xml", content);
        assertContains("Rida Benjelloun", content);
    }

    // TIKA-1036
    @Test
    public void testPlaceholders() throws Exception {
        String xml = getXML("testEmbedded.zip").xml;
        assertContains("<div class=\"embedded\" id=\"test1.txt\" />", xml);
        assertContains("<div class=\"embedded\" id=\"test2.txt\" />", xml);

        // Also make sure INTERNAL_PATH was
        // passed when parsing the embedded docs:
        ParseContext context = new ParseContext();
        GatherInternalPathsDocumentExtractor extractor = new GatherInternalPathsDocumentExtractor();
        context.set(EmbeddedDocumentExtractor.class, extractor);
        try (TikaInputStream tis = getResourceAsStream("/test-documents/testEmbedded.zip")) {
            AUTO_DETECT_PARSER.parse(tis, new BodyContentHandler(), new Metadata(), context);
        }

        assertTrue(extractor.allInternalPaths.contains("test1.txt"));
        assertTrue(extractor.allInternalPaths.contains("test2.txt"));
    }

    @Test
    public void test3MFIsParsedAsZip() throws Exception {
        List<Metadata> metadataList = getRecursiveMetadata("test3mf.3mf");
        Metadata container = metadataList.get(0);
        assertEquals("application/vnd.ms-package.3dmanufacturing-3dmodel+xml",
                container.get(HttpHeaders.CONTENT_TYPE));
        assertNull(container.get(TikaCoreProperties.CONTAINER_EXCEPTION));
        Set<String> names = new HashSet<>();
        for (Metadata m : metadataList.subList(1, metadataList.size())) {
            names.add(m.get(TikaCoreProperties.RESOURCE_NAME_KEY));
        }
        assertTrue(names.contains("3D/3dmodel.model"), names.toString());
    }

    @Test
    public void testZipEncrypted() throws Exception {
        List<Metadata> metadataList = getRecursiveMetadata("testZipEncrypted.zip");
        assertEquals(2, metadataList.size());
        String[] values = metadataList.get(0)
                .getValues(TikaCoreProperties.TIKA_META_EXCEPTION_EMBEDDED_STREAM);
        assertNotNull(values);
        assertEquals(1, values.length);
        assertContains("EncryptedDocumentException: stream (encrypted.txt) is encrypted",
                values[0]);


        assertContains("hello world", metadataList.get(1).get(TikaCoreProperties.TIKA_CONTENT));
    }

    @Test
    public void testDataDescriptorWithEmptyEntry() throws Exception {

        //test that an empty first entry does not cause problems
        List<Metadata> results = getRecursiveMetadata("testZip_with_DataDescriptor2.zip");
        assertEquals(5, results.size());

        //mime is 0 bytes
        assertContains("InputStream must have > 0 bytes",
                results.get(1).get("tk:exception:embedded-exception"));
        //source.xml is binary, not xml
        assertContains("TikaException: XML parse error",
                results.get(2).get("tk:exception:embedded-exception"));
        //manifest.xml has malformed xml
        assertContains("TikaException: XML parse error",
                results.get(4).get("tk:exception:embedded-exception"));
    }

    private static class GatherInternalPathsDocumentExtractor implements EmbeddedDocumentExtractor {
        public Set<String> allInternalPaths = new HashSet<>();

        public boolean shouldParseEmbedded(Metadata metadata, ParseContext parseContext) {
            String internalPath = metadata.get(TikaCoreProperties.INTERNAL_PATH);
            if (internalPath != null) {
                allInternalPaths.add(internalPath);
            }
            return false;
        }

        public void parseEmbedded(TikaInputStream inputStream, ContentHandler contentHandler,
                                  Metadata metadata, ParseContext parseContext, boolean outputHtml) {
            throw new UnsupportedOperationException("should never be called");
        }
    }

    @Test
    public void testComicBookCoverIsThumbnail() throws Exception {
        byte[] png = png();
        byte[] cbz = zip(new String[]{"002.png", "ComicInfo.xml", "__MACOSX/._000.png",
                ".000.png", "001.png"}, png);
        Map<String, Metadata> entries = entriesByName(cbz, "comic.cbz");
        assertEquals("application/vnd.comicbook+zip",
                entries.get("").get(HttpHeaders.CONTENT_TYPE));
        assertEquals(TikaCoreProperties.EmbeddedResourceType.THUMBNAIL.toString(),
                entries.get("001.png").get(TikaCoreProperties.EMBEDDED_RESOURCE_TYPE));
        assertNull(entries.get("002.png").get(TikaCoreProperties.EMBEDDED_RESOURCE_TYPE));
    }

    @Test
    public void testFixedPathPreviewIsThumbnail() throws Exception {
        byte[] png = png();
        String[][] cases = {
                {"art.kra", "application/x-krita", "preview.png", "mergedimage.png"},
                {"art.ora", "image/openraster", "Thumbnails/thumbnail.png", "data/layer000.png"},
                {"part.FCStd", "application/x-extension-fcstd", "thumbnails/Thumbnail.png",
                        "Document.xml"},
                {"art.procreate", "application/x-procreate", "QuickLook/Thumbnail.png",
                        "Signature/Signature.png"},
                {"design.sketch", "application/x-sketch", "previews/preview.png",
                        "images/0.png"},
        };
        for (String[] c : cases) {
            Map<String, Metadata> entries = entriesByName(zip(new String[]{c[3], c[2]}, png), c[0]);
            assertEquals(c[1], entries.get("").get(HttpHeaders.CONTENT_TYPE), c[0]);
            assertEquals(TikaCoreProperties.EmbeddedResourceType.THUMBNAIL.toString(),
                    entries.get(c[2]).get(TikaCoreProperties.EMBEDDED_RESOURCE_TYPE), c[0]);
            assertNull(entries.get(c[3]).get(TikaCoreProperties.EMBEDDED_RESOURCE_TYPE), c[0]);
        }
    }

    @Test
    public void testFixedPathPreviewMissing() throws Exception {
        Map<String, Metadata> entries =
                entriesByName(zip(new String[]{"mergedimage.png"}, png()), "art.kra");
        assertEquals("application/x-krita", entries.get("").get(HttpHeaders.CONTENT_TYPE));
        assertNull(entries.get("mergedimage.png").get(TikaCoreProperties.EMBEDDED_RESOURCE_TYPE));
    }

    @Test
    public void testDuplicatePreviewNameMarkedOnce() throws Exception {
        byte[] png = png();
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (ZipArchiveOutputStream zos = new ZipArchiveOutputStream(bos)) {
            for (int i = 0; i < 3; i++) {
                zos.putArchiveEntry(new ZipArchiveEntry("preview.png"));
                zos.write(png);
                zos.closeArchiveEntry();
            }
        }
        Metadata metadata = new Metadata();
        metadata.set(TikaCoreProperties.RESOURCE_NAME_KEY, "art.kra");
        try (TikaInputStream tis = TikaInputStream.get(bos.toByteArray())) {
            List<Metadata> list = getRecursiveMetadata(tis, metadata, new ParseContext(), false);
            assertEquals(4, list.size());
            long thumbnails = list.stream()
                    .filter(m -> TikaCoreProperties.EmbeddedResourceType.THUMBNAIL.toString()
                            .equals(m.get(TikaCoreProperties.EMBEDDED_RESOURCE_TYPE)))
                    .count();
            assertEquals(1, thumbnails);
        }
    }

    @Test
    public void testPlainZipHasNoThumbnail() throws Exception {
        Map<String, Metadata> entries =
                entriesByName(zip(new String[]{"001.png"}, png()), "images.zip");
        assertNull(entries.get("001.png").get(TikaCoreProperties.EMBEDDED_RESOURCE_TYPE));
    }

    private Map<String, Metadata> entriesByName(byte[] zip, String fileName) throws Exception {
        Metadata metadata = new Metadata();
        metadata.set(TikaCoreProperties.RESOURCE_NAME_KEY, fileName);
        Map<String, Metadata> byName = new HashMap<>();
        try (TikaInputStream tis = TikaInputStream.get(zip)) {
            List<Metadata> list = getRecursiveMetadata(tis, metadata, new ParseContext(), false);
            byName.put("", list.get(0));
            for (Metadata m : list.subList(1, list.size())) {
                byName.put(m.get(TikaCoreProperties.RESOURCE_NAME_KEY), m);
            }
        }
        return byName;
    }

    private static byte[] zip(String[] names, byte[] png) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(bos)) {
            for (String name : names) {
                zos.putNextEntry(new ZipEntry(name));
                zos.write(name.endsWith(".xml") ? "<ComicInfo/>".getBytes(UTF_8) : png);
                zos.closeEntry();
            }
        }
        return bos.toByteArray();
    }

    private static byte[] png() throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(4, 6, BufferedImage.TYPE_INT_RGB), "png", bos);
        return bos.toByteArray();
    }
}
