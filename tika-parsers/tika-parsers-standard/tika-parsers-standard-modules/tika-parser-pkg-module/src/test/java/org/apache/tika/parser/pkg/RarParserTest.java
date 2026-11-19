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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.TimeZone;
import java.util.zip.CRC32;

import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.xml.sax.ContentHandler;

import org.apache.tika.exception.EncryptedDocumentException;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.FileSystem;
import org.apache.tika.metadata.HttpHeaders;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.parser.PasswordProvider;
import org.apache.tika.sax.BodyContentHandler;
import org.apache.tika.test.TestLocales;

/**
 * Test case for parsing rar files.
 */
public class RarParserTest extends AbstractPkgTest {

    /**
     * Tests that the ParseContext parser is correctly
     * fired for all the embedded entries.
     */
    @Test
    public void testEmbedded() throws Exception {
        ContentHandler handler = new BodyContentHandler();
        Metadata metadata = new Metadata();

        try (TikaInputStream tis = getResourceAsStream("/test-documents/test-documents.rar")) {
            AUTO_DETECT_PARSER.parse(tis, handler, metadata, trackingContext);
        }

        // Should have found all 9 documents, but not the directory
        assertEquals(9, tracker.filenames.size());
        assertEquals(9, tracker.mediatypes.size());
        assertEquals(9, tracker.modifiedAts.size());

        // Should have names but not content types, as rar doesn't
        //  store the content types
        assertEquals("test-documents/testEXCEL.xls", tracker.filenames.get(0));
        assertEquals("test-documents/testHTML.html", tracker.filenames.get(1));
        assertEquals("test-documents/testOpenOffice2.odt", tracker.filenames.get(2));
        assertEquals("test-documents/testPDF.pdf", tracker.filenames.get(3));
        assertEquals("test-documents/testPPT.ppt", tracker.filenames.get(4));
        assertEquals("test-documents/testRTF.rtf", tracker.filenames.get(5));
        assertEquals("test-documents/testTXT.txt", tracker.filenames.get(6));
        assertEquals("test-documents/testWORD.doc", tracker.filenames.get(7));
        assertEquals("test-documents/testXML.xml", tracker.filenames.get(8));

        for (String type : tracker.mediatypes) {
            assertNull(type);
        }
        for (String crt : tracker.createdAts) {
            assertNull(crt);
        }
        // junrar FileHeader.getDateDos uses Calendar.getInstance() (TIKA-4920)
        if (!TestLocales.nonGregorianDefault()) {
            for (String mod : tracker.modifiedAts) {
                assertNotNull(mod);
                assertTrue(mod.startsWith("20"), "Modified at " + mod);
            }
        }

        // Should have filenames in the content string
        String content = handler.toString();
        assertContains("test-documents/testHTML.html", content);
        assertContains("test-documents/testEXCEL.xls", content);
        assertContains("test-documents/testOpenOffice2.odt", content);
        assertContains("test-documents/testPDF.pdf", content);
        assertContains("test-documents/testPPT.ppt", content);
        assertContains("test-documents/testRTF.rtf", content);
        assertContains("test-documents/testTXT.txt", content);
        assertContains("test-documents/testWORD.doc", content);
        assertContains("test-documents/testXML.xml", content);
    }

    @Test
    public void testCorruptEntryKeepsLaterEntries(@TempDir Path tmp) throws Exception {
        byte[] rar;
        try (TikaInputStream tis = getResourceAsStream("/test-documents/test-documents.rar")) {
            rar = IOUtils.toByteArray(tis);
        }
        // testEXCEL.xls, the first entry, has its packed data at 80..2885
        rar[1500] ^= 0x5A;
        Path corrupt = tmp.resolve("corrupt.rar");
        Files.write(corrupt, rar);

        List<Metadata> metadataList = getRecursiveMetadata(corrupt, new ParseContext(), false);
        assertContains("testEXCEL.xls",
                metadataList.get(0).get(TikaCoreProperties.TIKA_META_EXCEPTION_EMBEDDED_STREAM));
        assertContains("test-documents/testXML.xml",
                metadataList.get(0).get(TikaCoreProperties.TIKA_CONTENT));
        assertTrue(hasEmbeddedPath(metadataList, "/testEXCEL.xls"));
        assertTrue(hasEmbeddedPath(metadataList, "/testXML.xml"));
    }

    @Test
    public void testRar5(@TempDir Path tmp) throws Exception {
        Path rar5 = Files.write(tmp.resolve("stored.rar"), storedRar5(-1,
                "page-002.txt", "second page", "page-001.txt", "first page"));
        List<Metadata> metadataList = getRecursiveMetadata(rar5, new ParseContext(), false);
        assertContains("version=5", metadataList.get(0).get(HttpHeaders.CONTENT_TYPE));
        assertNull(metadataList.get(0).get(TikaCoreProperties.CONTAINER_EXCEPTION));
        assertEquals(3, metadataList.size());
        assertTrue(hasEmbeddedPath(metadataList, "/page-001.txt"));
        assertTrue(hasEmbeddedPath(metadataList, "/page-002.txt"));
        assertContains("page-001.txt", metadataList.get(0).get(TikaCoreProperties.TIKA_CONTENT));
    }

    /** RAR5 stores a Unix time, an instant: it does not move with the JVM's time zone. */
    @Test
    public void testRar5TimesAreInstants(@TempDir Path tmp) throws Exception {
        Path rar5 = Files.write(tmp.resolve("timed.rar"),
                storedRar5(1_700_000_000L, "a.txt", "a"));
        TimeZone zone = TimeZone.getDefault();
        try {
            for (String id : new String[]{"UTC", "America/New_York", "Asia/Tokyo"}) {
                TimeZone.setDefault(TimeZone.getTimeZone(id));
                List<Metadata> metadataList = getRecursiveMetadata(rar5, new ParseContext(), false);
                assertEquals("2023-11-14T22:13:20Z",
                        metadataList.get(1).get(FileSystem.MODIFIED), id);
            }
        } finally {
            TimeZone.setDefault(zone);
        }
    }

    /**
     * A RAR5 archive of stored (uncompressed) files, written after the RAR 5.0 archive format
     * description (https://www.rarlab.com/technote.htm): signature, main archive header, one
     * file header with its data per file, end of archive header. A modification time other
     * than -1 is written as Unix time.
     */
    private static byte[] storedRar5(long mtime, String... namesAndContents) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.writeBytes(new byte[]{'R', 'a', 'r', '!', 0x1a, 0x07, 0x01, 0x00});
        out.writeBytes(rar5Block(concat(vint(1), vint(0), vint(0))));
        for (int i = 0; i < namesAndContents.length; i += 2) {
            byte[] name = namesAndContents[i].getBytes(StandardCharsets.UTF_8);
            byte[] data = namesAndContents[i + 1].getBytes(StandardCharsets.UTF_8);
            CRC32 crc = new CRC32();
            crc.update(data);
            byte[] time = mtime < 0 ? new byte[0] : littleEndian((int) mtime);
            out.writeBytes(rar5Block(concat(vint(2), vint(0x0002), vint(data.length),
                    vint(mtime < 0 ? 0x0004 : 0x0006), vint(data.length), vint(0x20), time,
                    littleEndian((int) crc.getValue()), vint(0), vint(0), vint(name.length),
                    name)));
            out.writeBytes(data);
        }
        out.writeBytes(rar5Block(concat(vint(5), vint(0), vint(0))));
        return out.toByteArray();
    }

    /** A header block: CRC32 of the size and the header, the size as a vint, the header. */
    private static byte[] rar5Block(byte[] header) {
        byte[] sized = concat(vint(header.length), header);
        CRC32 crc = new CRC32();
        crc.update(sized);
        return concat(littleEndian((int) crc.getValue()), sized);
    }

    private static byte[] vint(long value) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        do {
            int b = (int) (value & 0x7f);
            value >>>= 7;
            out.write(value != 0 ? b | 0x80 : b);
        } while (value != 0);
        return out.toByteArray();
    }

    private static byte[] littleEndian(int value) {
        return new byte[]{(byte) value, (byte) (value >>> 8), (byte) (value >>> 16),
                (byte) (value >>> 24)};
    }

    private static byte[] concat(byte[]... parts) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (byte[] part : parts) {
            out.writeBytes(part);
        }
        return out.toByteArray();
    }

    private static boolean hasEmbeddedPath(List<Metadata> metadataList, String path) {
        return metadataList.stream()
                .anyMatch(m -> path.equals(m.get(TikaCoreProperties.EMBEDDED_RESOURCE_PATH)));
    }

    /**
     * Note - we don't currently support Encrypted RAR files,
     * so all we can do is throw a helpful exception
     */
    @Test
    public void testEncryptedRar() throws Exception {
        Parser parser = new RarParser();

        try (TikaInputStream tis = getResourceAsStream("/test-documents/test-documents-enc.rar")) {
            Metadata metadata = new Metadata();
            ContentHandler handler = new BodyContentHandler();
            ParseContext context = new ParseContext();
            context.set(PasswordProvider.class, new PasswordProvider() {
                @Override
                public String getPassword(Metadata metadata) {
                    return "ApacheTika";
                }
            });

            // Note - we don't currently support encrypted RAR
            // files so we can't check the contents
            parser.parse(tis, handler, metadata, trackingContext);
            fail("No support yet for Encrypted RAR files");
        } catch (EncryptedDocumentException e) {
            // Good, as expected right now
        }
    }
}
