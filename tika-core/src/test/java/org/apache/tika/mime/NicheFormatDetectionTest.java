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
package org.apache.tika.mime;

import static java.nio.charset.StandardCharsets.ISO_8859_1;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;

import org.junit.jupiter.api.Test;

import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;

/**
 * Formats found as application/octet-stream in Common Crawl; headers are synthesized
 * from the shapes seen there.
 */
public class NicheFormatDetectionTest {

    private static final MimeTypes MIME_TYPES = MimeTypes.getDefaultMimeTypes();

    @Test
    public void testFec() throws Exception {
        MediaType fec = MediaType.application("x-fec");
        assertDetected(fec, "HDR\u001cFEC\u001c8.3\u001cCMDI CRIMSON FILER\u001c5.0\u001c\u001c\n");
        assertTrue(MIME_TYPES.getMediaTypeRegistry().isSpecializationOf(fec, MediaType.TEXT_PLAIN));
    }

    @Test
    public void testGarminGdb() throws Exception {
        assertDetected(MediaType.application("x-garmin-gdb"),
                "MsRcf\u0000\u0002\u0000\u0000\u0000Dm\u0000");
    }

    @Test
    public void testGettextCatalog() throws Exception {
        MediaType mo = MediaType.application("x-gettext-translation");
        assertDetected(mo, "Þ\u0012\u0004\u0095\u0000\u0000\u0000\u0000\u008e\u0005\u0000\u0000");
        assertDetected(mo, "\u0095\u0004\u0012Þ\u0000\u0000\u0000\u0000\u0000\u0000\u0005\u008e");
        // unknown revision
        assertDetected(MediaType.OCTET_STREAM, "Þ\u0012\u0004\u0095\u0000\u0007\u0000\u0000");
    }

    @Test
    public void testMpqAndWarcraft() throws Exception {
        MediaType mpq = MediaType.application("x-mpq");
        assertDetected(mpq, "MPQ\u001a \u0000\u0000\u0000");
        assertDetected(mpq, "MPQ\u001b\u0000\u0002\u0000\u0000");
        assertDetected(MediaType.OCTET_STREAM, "MPQ\u001a\u0099\u0000\u0000\u0000");
        assertDetected(MediaType.application("x-warcraft3-map"), "HM3W\u0000\u0000\u0000\u0000Map");
        assertDetected(MediaType.application("x-warcraft3-replay"),
                "Warcraft III recorded game\u001a\u0000");
    }

    @Test
    public void testBinaryDiffs() throws Exception {
        MediaType vcdiff = MediaType.application("x-vcdiff");
        assertDetected(vcdiff, "ÖÃÄ\u0000\u0005");
        assertDetected(vcdiff, "ÖÃÄ\u0000\u0000");
        // undefined Hdr_Indicator bits
        assertDetected(MediaType.OCTET_STREAM, "ÖÃÄ\u0000\u0010");
        assertDetected(MediaType.application("x-xdelta"), "%XDZ004%\u0000\u0000");
    }

    @Test
    public void testSid() throws Exception {
        MediaType sid = MediaType.audio("prs.sid");
        assertDetected(sid, "RSID\u0000\u0002\u0000|");
        assertDetected(sid, "PSID\u0000\u0001\u0000v");
        assertDetected(MediaType.OCTET_STREAM, "PSID\u0000\t\u0000v");
    }

    @Test
    public void testPaintShopPro() throws Exception {
        assertDetected(MediaType.image("x-paintshoppro"),
                "Paint Shop Pro Image File\n\u001a\u0000\u0000\u0000\u0000\u0000");
    }

    @Test
    public void testElastoManiaLevel() throws Exception {
        MediaType lev = MediaType.application("x-elasto-mania-level");
        assertDetected(lev, "POT14\u0000\u0000");
        assertDetected(lev, "POT06\u0000\u0000");
    }

    @Test
    public void testBinaryPgpKeys() throws Exception {
        MediaType keys = MediaType.application("pgp-keys");
        // public key, 2-byte length, RSA
        assertDetected(keys, "\u0099\u0001\r\u0004ABCD\u0001");
        // secret key, 1-byte length, EdDSA
        assertDetected(keys, "\u0094X\u0004ABCD\u0016");
        assertDetected(MediaType.OCTET_STREAM, "\u0099\u0001\r\u0004ABCD\u0007");
    }

    @Test
    public void testRetroArchivesAndGames() throws Exception {
        assertDetected(MediaType.application("x-stuffit"), "SIT!\u0000\u0003\u0000$abrLau");
        assertDetected(MediaType.application("vnd.symbian.install"), "z\u001a \u0010\u0000\u0000\u0000\u0000");
        assertDetected(MediaType.application("x-ms-reader"), "ITOLITLS\u0001\u0000\u0000\u0000");
        assertDetected(MediaType.application("x-lrzip"), "LRZI\u0000\u0006");
        assertDetected(MediaType.application("x-glulx"), "Glul\u0000\u0003\u0001\u0000");
        assertDetected(MediaType.video("x-flc"), "abcd\u0012¯\u0016\u0000ABCD\b\u0000");
        assertDetected(MediaType.video("x-fli"), "abcd\u0011¯\u0016\u0000ABCD\b\u0000");
    }

    @Test
    public void testAudioModulesAndApe() throws Exception {
        assertDetected(MediaType.audio("x-ape"), "MAC \u0096\u000f4\u0000");
        assertDetected(MediaType.audio("x-s3m"), "unknown" + "\u0000".repeat(21) + "\u001a\u0010"
                + "\u0000".repeat(14) + "SCRM");
    }

    @Test
    public void testMusicAndAudioFormats() throws Exception {
        assertDetected(MediaType.application("x-nwc"), "[NWZ]\u0000xÚ");
        assertDetected(MediaType.application("x-nwc"), "[NoteWorthy ArtWare]\u0000");
        assertDetected(MediaType.application("x-finale"), "ENIGMA BINARY FILE\u0000");
        assertDetected(MediaType.audio("x-rex2"), "CAT \u0000\t\u0000\u0000REX2HEAD");
        assertDetected(MediaType.audio("x-mfi"), "melo\u0000\u0000\u0011#\u0000]\u0001\u0001\u0004vers");
        assertDetected(MediaType.OCTET_STREAM, "melody\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0000");
    }

    @Test
    public void testGameAndConsoleFormats() throws Exception {
        assertDetected(MediaType.application("x-gbx"), "GBX\u0006\u0000BUCR");
        assertDetected(MediaType.application("x-gbx"), "GBX\u0003\u0000BUC");
        assertDetected(MediaType.OCTET_STREAM, "GBX\t\u0000BUCR");
        assertDetected(MediaType.application("x-ps3-package"), "\u007fPKG\u0080\u0000\u0000\u0001");
        assertDetected(MediaType.application("x-ps3-package"), "\u007fPKG\u0000\u0000\u0000\u0001");
        assertDetected(MediaType.application("x-ps3-theme"), "P3TF\u0000\u0000\u0001\u0010");
        assertDetected(MediaType.application("x-trainz-cdp"), "ACS$\u0001\u0000\u0000\u0000\u0000\u0000\u0000\u0000");
        assertDetected(MediaType.application("x-spectrum-rzx"), "RZX!\u0000\f");
        assertDetected(MediaType.image("x-quake-sprite"), "IDSP\u0002\u0000\u0000\u0000");
    }

    @Test
    public void testArchivesAndPackages() throws Exception {
        assertDetected(MediaType.application("x-nufx"), "NõFélå§³");
        assertDetected(MediaType.application("x-lzx"), "LZX\u0000\f\u0000\n\u0004");
        assertDetected(MediaType.application("x-mikrotik-npk"), "\u001eñÐº");
        assertDetected(MediaType.application("x-clamav-diff"), "ClamAV-Diff:119:526:");
        assertDetected(MediaType.application("x-clamav-database"), "ClamAV-VDB:01 Jan 2020");
    }

    @Test
    public void testCasioFxInterface() throws Exception {
        StringBuilder fxi = new StringBuilder("20");
        for (char c : "\u001fFX-INTERFACE - YELLOW COMPUTING".toCharArray()) {
            fxi.append((char) (c ^ 0xe7));
        }
        assertDetected(MediaType.application("x-casio-fxi"), fxi.toString());
    }

    private static void assertDetected(MediaType expected, String latin1) throws IOException {
        byte[] bytes = new byte[64];
        byte[] header = latin1.getBytes(ISO_8859_1);
        System.arraycopy(header, 0, bytes, 0, header.length);
        try (TikaInputStream tis = TikaInputStream.get(bytes)) {
            assertEquals(expected, MIME_TYPES.detect(tis, new Metadata(), new ParseContext()));
        }
    }
}
