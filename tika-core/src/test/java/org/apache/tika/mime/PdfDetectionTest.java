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
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import java.io.InputStream;
import java.time.Duration;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;

/**
 * Detection of PDFs whose {@code %PDF-} header is preceded by {@code %%} comment
 * lines, which would otherwise be claimed by the matlab {@code %%} magic.
 *
 * @see <a href="https://issues.apache.org/jira/browse/TIKA-3328">TIKA-3328</a>
 * @see <a href="https://issues.apache.org/jira/browse/TIKA-4782">TIKA-4782</a>
 */
public class PdfDetectionTest {

    private static final MediaType PDF = MediaType.application("pdf");

    private static final MediaType MATLAB = MediaType.text("x-matlab");

    private static final String PDF_BODY = "%PDF-1.7\r\n1 0 obj\r\n";

    private static MimeTypes MIME_TYPES;

    @BeforeAll
    public static void setUp() {
        MIME_TYPES = MimeTypes.getDefaultMimeTypes();
    }

    /**
     * Print-shop job ticket ahead of the header; the {@code %PDF-} lands well past
     * the 512-byte window of the older TIKA-3328 rule.
     */
    @Test
    public void testPrintTicketHeader() throws Exception {
        try (InputStream in = getClass().getResourceAsStream("test-pdf-with-print-ticket-header.pdf")) {
            assertNotNull(in, "missing test file");
            assertEquals(PDF, detect(in));
        }
    }

    /**
     * 10 and 50 lines of 60 push the header past the 512-byte window the older rules
     * can reach; 1 line keeps TIKA-3328 covered.
     */
    @Test
    public void testCommentLinesBeforeHeader() throws Exception {
        for (int lines : new int[]{1, 10, 50}) {
            assertEquals(PDF, detect(commentLines(lines, 60) + PDF_BODY), lines + " comment lines");
        }
        assertEquals(PDF, detect(commentLines(5, 150) + PDF_BODY), "maximum-length lines");
    }

    /**
     * Both bounds at once. This is the case that catches a prefix sized within the
     * documented bounds but past the 8K MagicDetector hands a regex.
     */
    @Test
    public void testLargestAcceptedPrefix() throws Exception {
        assertEquals(PDF, detect(commentLines(50, 150) + PDF_BODY));
    }

    @Test
    public void testBlankLinesAndLineEndings() throws Exception {
        assertEquals(PDF, detect("%%BeginTicket\r\n\r\n%%EndTicket\n\n" + PDF_BODY));
        assertEquals(PDF, detect("\r\n\r\n" + PDF_BODY));
        assertEquals(PDF, detect("%%a\r%%b\r" + PDF_BODY));
        assertEquals(PDF, detect("%%a\n%%b\n" + PDF_BODY.replace("%PDF-1.", "%PDF-2.")));
    }

    /**
     * Each negative case puts the header past 512 bytes, so only the TIKA-4782 rule
     * could have matched it.
     */
    @Test
    public void testCommentPrefixIsBounded() throws Exception {
        assertNotEquals(PDF, detect(commentLines(51, 60) + PDF_BODY), "51 comment lines");
        assertNotEquals(PDF, detect(commentLines(5, 151) + PDF_BODY), "over-long comment lines");
    }

    /**
     * Only comment and blank lines may precede the header: anything else and this is
     * some other format that happens to embed a PDF.
     */
    @Test
    public void testNonCommentPrefixIsNotPdf() throws Exception {
        assertNotEquals(PDF, detect(commentLines(10, 60) + "x = 1;\r\n" + PDF_BODY));
    }

    @Test
    public void testMatlabStillDetected() throws Exception {
        assertEquals(MATLAB, detect("%% cell one\r\nx = 1;\r\n%% cell two\r\ny = x + 1;\r\n"));
    }

    /**
     * The TIKA-4782 regex must not backtrack. Earlier drafts of it hung Java's matcher
     * indefinitely on these inputs; linear forms answer in well under a millisecond, so
     * a generous timeout separates the two without being timing-sensitive.
     */
    @Test
    public void testNoCatastrophicBacktracking() {
        String[] hostile = new String[]{
                "\r\n".repeat(4096),
                "\r".repeat(8192),
                "%".repeat(8192),
                "%%".repeat(4096),
                "%%a\r\n".repeat(1638),
                "%%a\r\n\r\n".repeat(1024),
                commentLines(50, 150) + "\r\n".repeat(1000)
        };
        assertTimeoutPreemptively(Duration.ofSeconds(10), () -> {
            for (String s : hostile) {
                detect(s);
            }
        });
    }

    /**
     * @param lineLength characters per line including the leading {@code %%}, excluding the CRLF
     */
    private static String commentLines(int count, int lineLength) {
        StringBuilder line = new StringBuilder("%%");
        while (line.length() < lineLength) {
            line.append('A');
        }
        line.append("\r\n");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < count; i++) {
            sb.append(line);
        }
        return sb.toString();
    }

    private static MediaType detect(String bytes) throws Exception {
        try (InputStream is = TikaInputStream.get(bytes.getBytes(ISO_8859_1))) {
            return MIME_TYPES.detect(is, new Metadata());
        }
    }

    private static MediaType detect(InputStream in) throws Exception {
        try (InputStream is = TikaInputStream.get(in)) {
            return MIME_TYPES.detect(is, new Metadata());
        }
    }
}
