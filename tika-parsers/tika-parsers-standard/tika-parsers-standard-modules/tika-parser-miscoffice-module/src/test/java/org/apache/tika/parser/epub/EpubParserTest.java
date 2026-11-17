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
package org.apache.tika.parser.epub;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import org.apache.tika.TikaTest;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Epub;
import org.apache.tika.metadata.HttpHeaders;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.Property;
import org.apache.tika.metadata.TikaCoreProperties;

public class EpubParserTest extends TikaTest {

    @Test
    public void testXMLParser() throws Exception {

        XMLResult xmlResult = getXML("testEPUB.epub");
        assertEquals("2.0", xmlResult.metadata.get(Epub.VERSION));
        assertEquals("application/epub+zip", xmlResult.metadata.get(HttpHeaders.CONTENT_TYPE));
        assertEquals("en", xmlResult.metadata.get(TikaCoreProperties.LANGUAGE));
        assertEquals("This is an ePub test publication for Tika.",
                xmlResult.metadata.get(TikaCoreProperties.DESCRIPTION));
        assertEquals("Apache", xmlResult.metadata.get(TikaCoreProperties.PUBLISHER));

        String content = xmlResult.xml;
        assertContains("Plus a simple div", content);
        assertContains("First item", content);
        assertContains("The previous headings were <strong>subchapters</strong>", content);
        assertContains("Table data", content);
        assertContains("This is the text for chapter Two", content);

        //make sure style/script elements aren't extracted
        assertNotContained("nothing to see here", content);
        assertNotContained("nor here", content);
        assertNotContained("font-style", content);

        //make sure that there is only one of each
        assertContainsCount("<html", content, 1);
        assertContainsCount("<head", content, 1);
        assertContainsCount("<body", content, 1);
    }

    @Test
    public void testEpubOrder() throws Exception {
        List<Metadata> metadataList = getRecursiveMetadata("testEPUB.epub");
        assertEquals("2.0", metadataList.get(0).get(Epub.VERSION));

        //test attachments
        assertEquals(2, metadataList.size());
        assertEquals("image/jpeg", metadataList.get(1).get(HttpHeaders.CONTENT_TYPE));
        //the EPUB 2 cover meta names the cover image, emitted as the thumbnail
        assertEquals("OPS/CoverDesign.jpg",
                metadataList.get(1).get(TikaCoreProperties.RESOURCE_NAME_KEY));
        assertEquals(TikaCoreProperties.EmbeddedResourceType.THUMBNAIL.toString(),
                metadataList.get(1).get(TikaCoreProperties.EMBEDDED_RESOURCE_TYPE));
        String xml = metadataList.get(0).get(TikaCoreProperties.TIKA_CONTENT);
        int tocIndex = xml.indexOf("h3 class=\"toc_heading\">Table of Contents<");
        int ch1 = xml.indexOf("<h1>Chapter 1");
        int ch2 = xml.indexOf("<h1>Chapter 2");
        assert (tocIndex > -1 && ch1 > -1 && ch2 > -1);
        assert (tocIndex < ch1);
        assert (tocIndex < ch2);
        assert (ch1 < ch2);
        //remove streaming test
    }


    @Test
    public void testTruncated() throws Exception {
        // Truncated zips are salvaged by DefaultZipContainerDetector and the
        // recovered ZipFile is handed to EpubParser via openContainer.
        // EpubParser itself no longer salvages — it relies on the detector.
        List<Metadata> metadataList;
        try (TikaInputStream tis = truncate("testEPUB.epub", 10000)) {
            metadataList = getRecursiveMetadata(tis, true);
        }
        String xml = metadataList.get(0).get(TikaCoreProperties.TIKA_CONTENT);
        int ch1 = xml.indexOf("<h1>Chapter 1");
        int ch2 = xml.indexOf("<h1>Chapter 2");
        assert (ch1 < ch2);
    }

    @Test
    public void testContentsWXMLExtensions() throws Exception {
        //TIKA-2310
        List<Metadata> metadataList = getRecursiveMetadata("testEPUB_xml_ext.epub");
        assertEquals(1, metadataList.size());
        assertEquals("2.0", metadataList.get(0).get(Epub.VERSION));
        assertContains("It was a bright cold day in April",
                metadataList.get(0).get(TikaCoreProperties.TIKA_CONTENT));
    }

    @Test
    @Disabled("add files to repo?")
    public void testPrePaginated() throws Exception {
        //this file has pre-paginated on an itemRef in a spine
        //https://github.com/IDPF/epub3-samples/releases/download/20170606/cole-voyage-of-life.epub

        //this file has pre-paginated in header metadata
        //https://github.com/IDPF/epub3-samples/releases/download/20170606/cole-voyage-of-life-tol.epub

        List<Metadata> metadataList = getRecursiveMetadata("cole-voyage-of-life.epub");
        assertEquals("pre-paginated", metadataList.get(0).get(Epub.RENDITION_LAYOUT));
    }

    /**
     * The EPUB 3 cover-image manifest property names the cover; the other
     * images are not thumbnails.
     */
    @Test
    public void testEpub3CoverImageIsTheThumbnail() throws Exception {
        List<Metadata> metadataList = getRecursiveMetadata("testEPUB_multi-metadata-vals.epub");
        Metadata cover = null;
        for (Metadata m : metadataList) {
            if ("epub/images/cover.jpg".equals(m.get(TikaCoreProperties.RESOURCE_NAME_KEY))) {
                cover = m;
            } else {
                assertNotEquals(TikaCoreProperties.EmbeddedResourceType.THUMBNAIL.toString(),
                        m.get(TikaCoreProperties.EMBEDDED_RESOURCE_TYPE));
            }
        }
        assertNotNull(cover);
        assertEquals(TikaCoreProperties.EmbeddedResourceType.THUMBNAIL.toString(),
                cover.get(TikaCoreProperties.EMBEDDED_RESOURCE_TYPE));
    }

    @Test
    public void testMultipleMetadataValues() throws Exception {
        //TIKA_4466
        List<Metadata> metadataList = getRecursiveMetadata("testEPUB_multi-metadata-vals.epub");
        Set<String> publishers = Set.of("Standard Ebooks", "Guternberg");
        Set<String> titles = Set.of("The Inheritors", "An Extravagant Story", "The Inheritors: An Extravagant Story");
        Set<String> contributors = Set.of("The League of Moveable Type", "zikasak", "William Holyoake", "Clare Boothby",
                "Graeme Mackreth", "Distributed Proofreaders", "Szymon Szott", "David Reimer");
        Set<String> creators = Set.of("Joseph Conrad", "Ford Madox Ford");
        Set<String> languages = Set.of("en-GB", "en-US");
        Set<String> descriptions = Set.of("A young writer dabbling in journalism meets a strange, otherworldly woman with long-term political goals.",
                "additional description");
        Set<String> sources = Set.of("https://www.gutenberg.org/ebooks/14888", "https://archive.org/details/inheritorsanext01fordgoog/");
        Set<String> identifiers = Set.of("https://standardebooks.org/ebooks/joseph-conrad_ford-madox-ford/the-inheritors",
                "isbn:0571225470");
        Set<String> subjects = Set.of("Science fiction");

        Metadata m = metadataList.get(0);
        assertEquals(publishers, set(m, TikaCoreProperties.PUBLISHER));
        assertEquals(titles, set(m, TikaCoreProperties.TITLE));
        assertEquals(contributors, set(m, TikaCoreProperties.CONTRIBUTOR));
        assertEquals(creators, set(m, TikaCoreProperties.CREATOR));
        assertEquals(languages, set(m, TikaCoreProperties.LANGUAGE));
        assertEquals(descriptions, set(m, TikaCoreProperties.DESCRIPTION));
        assertEquals(sources, set(m, TikaCoreProperties.SOURCE));
        assertEquals(identifiers, set(m, TikaCoreProperties.IDENTIFIER));
        assertEquals(subjects, set(m, TikaCoreProperties.SUBJECT));

        assertEquals(2, m.getValues(TikaCoreProperties.RIGHTS).length);
        assertTrue(m.get(TikaCoreProperties.RIGHTS).startsWith("The source text and artwork"));
        assertEquals("test rights", m.getValues(TikaCoreProperties.RIGHTS)[1]);
    }

    /**
     * Manifest hrefs are URI references relative to the OPF: a '+' is a '+', percent
     * escapes are decoded, '../' and fragments are resolved.
     */
    @Test
    public void testHrefsAreResolvedAsUris() throws Exception {
        String[][] items = {
                {"a", "Text/plain.xhtml", "OEBPS/Text/plain.xhtml", "PLAINCHAPTER"},
                {"b", "Text/c++.xhtml", "OEBPS/Text/c++.xhtml", "PLUSCHAPTER"},
                {"c", "Text/My%20Chapter.xhtml", "OEBPS/Text/My Chapter.xhtml", "SPACECHAPTER"},
                {"d", "../Shared/up.xhtml", "Shared/up.xhtml", "UPCHAPTER"},
                {"e", "Text/frag.xhtml#start", "OEBPS/Text/frag.xhtml", "FRAGMENTCHAPTER"}};
        List<Metadata> list;
        try (TikaInputStream tis = TikaInputStream.get(epub(items, null))) {
            list = getRecursiveMetadata(tis, true);
        }
        String content = list.get(0).get(TikaCoreProperties.TIKA_CONTENT);
        for (String[] item : items) {
            assertContains(item[3], content);
        }
        Metadata cover = list.get(1);
        assertEquals("OEBPS/Images/cover art.png", cover.get(TikaCoreProperties.RESOURCE_NAME_KEY));
        assertEquals(TikaCoreProperties.EmbeddedResourceType.THUMBNAIL.toString(),
                cover.get(TikaCoreProperties.EMBEDDED_RESOURCE_TYPE));
    }

    /**
     * encryption.xml names its items by URI too: an item named with an escape is still known
     * as encrypted, so its bytes are neither parsed as a chapter nor emitted.
     */
    @Test
    public void testEscapedEncryptedItems() throws Exception {
        String[][] items = {{"a", "Text/My%20Chapter.xhtml", "OEBPS/Text/My Chapter.xhtml",
                "SPACECHAPTER"}};
        List<Metadata> list;
        try (TikaInputStream tis = TikaInputStream.get(epub(items,
                "OEBPS/Text/My%20Chapter.xhtml"))) {
            list = getRecursiveMetadata(tis, true);
        }
        assertContains("EncryptedDocumentException",
                list.get(0).get(TikaCoreProperties.CONTAINER_EXCEPTION));
        assertNotContained("SPACECHAPTER",
                String.valueOf(list.get(0).get(TikaCoreProperties.TIKA_CONTENT)));

        try (TikaInputStream tis = TikaInputStream.get(epub(items,
                "OEBPS/Images/cover%20art.png"))) {
            list = getRecursiveMetadata(tis, true);
        }
        assertContains("SPACECHAPTER", list.get(0).get(TikaCoreProperties.TIKA_CONTENT));
        assertEquals(1, list.size(), "the encrypted cover is not emitted");
    }

    /**
     * An EPUB with its OPF in OEBPS/, one spine item per {id, href, entry name, text}, a
     * cover image whose href holds an escaped space, and, if given, an encrypted URI.
     */
    private static byte[] epub(String[][] items, String encryptedUri) throws IOException {
        StringBuilder manifest = new StringBuilder();
        StringBuilder spine = new StringBuilder();
        for (String[] item : items) {
            manifest.append("<item id=\"").append(item[0]).append("\" href=\"").append(item[1])
                    .append("\" media-type=\"application/xhtml+xml\"/>");
            spine.append("<itemref idref=\"").append(item[0]).append("\"/>");
        }
        manifest.append("<item id=\"cover\" href=\"Images/cover%20art.png\" "
                + "media-type=\"image/png\" properties=\"cover-image\"/>");
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
            byte[] mimetype = "application/epub+zip".getBytes(StandardCharsets.US_ASCII);
            ZipEntry first = new ZipEntry("mimetype");
            first.setMethod(ZipEntry.STORED);
            first.setSize(mimetype.length);
            CRC32 crc = new CRC32();
            crc.update(mimetype);
            first.setCrc(crc.getValue());
            zip.putNextEntry(first);
            zip.write(mimetype);
            put(zip, "META-INF/container.xml", "<?xml version=\"1.0\"?><container version=\"1.0\" "
                    + "xmlns=\"urn:oasis:names:tc:opendocument:xmlns:container\"><rootfiles>"
                    + "<rootfile full-path=\"OEBPS/content.opf\" "
                    + "media-type=\"application/oebps-package+xml\"/></rootfiles></container>");
            if (encryptedUri != null) {
                put(zip, "META-INF/encryption.xml", "<?xml version=\"1.0\"?><encryption "
                        + "xmlns=\"urn:oasis:names:tc:opendocument:xmlns:container\" "
                        + "xmlns:enc=\"http://www.w3.org/2001/04/xmlenc#\"><enc:EncryptedData>"
                        + "<enc:CipherData><enc:CipherReference URI=\"" + encryptedUri + "\"/>"
                        + "</enc:CipherData></enc:EncryptedData></encryption>");
            }
            put(zip, "OEBPS/content.opf", "<?xml version=\"1.0\"?><package "
                    + "xmlns=\"http://www.idpf.org/2007/opf\" version=\"3.0\" "
                    + "unique-identifier=\"id\"><metadata "
                    + "xmlns:dc=\"http://purl.org/dc/elements/1.1/\"><dc:identifier id=\"id\">x"
                    + "</dc:identifier><dc:title>T</dc:title></metadata><manifest>" + manifest
                    + "</manifest><spine>" + spine + "</spine></package>");
            for (String[] item : items) {
                put(zip, item[2], "<?xml version=\"1.0\"?><html "
                        + "xmlns=\"http://www.w3.org/1999/xhtml\"><head><title>t</title></head>"
                        + "<body><p>" + item[3] + "</p></body></html>");
            }
            zip.putNextEntry(new ZipEntry("OEBPS/Images/cover art.png"));
            zip.write(new byte[]{(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1a, '\n'});
        }
        return bytes.toByteArray();
    }

    private static void put(ZipOutputStream zip, String name, String content) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(content.getBytes(StandardCharsets.UTF_8));
    }

    private Set<String> set(Metadata m, Property property) {
        return new HashSet<>(Arrays.asList(m.getValues(property)));
    }
}
