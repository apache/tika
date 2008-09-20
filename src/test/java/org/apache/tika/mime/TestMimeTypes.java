/**
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

// Junit imports
import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;

import junit.framework.TestCase;

import org.apache.tika.config.TikaConfig;

/**
 * 
 * Test Suite for the {@link MimeTypes} repository.
 * 
 */
public class TestMimeTypes extends TestCase {

    private MimeTypes repo;

    private static URL u;

    static {
        try {
            u = new URL("http://mydomain.com/x.pdf?x=y");
        } catch (MalformedURLException e) {
            fail(e.getMessage());
        }
    }

    private static final File f = new File("/a/b/c/x.pdf");

    public TestMimeTypes() {
        try {
            repo = TikaConfig.getDefaultConfig().getMimeRepository();
        } catch (Exception e) {
            fail(e.getMessage());
        }

    }

    public void testCaseSensitivity() {
        MimeType type = repo.getMimeType("test.PDF");
        assertNotNull(type);
        assertEquals(repo.getMimeType("test.pdf"), type);
        assertEquals(repo.getMimeType("test.PdF"), type);
        assertEquals(repo.getMimeType("test.pdF"), type);
    }

    public void testLoadMimeTypes() throws MimeTypeException {
        assertNotNull(repo.forName("application/octet-stream"));
        assertNotNull(repo.forName("text/x-tex"));
    }

    /**
     * Tests MIME type determination based solely on the URL's extension.
     */
    public void testGuessMimeTypes() {

        assertEquals("application/pdf", repo.getMimeType("x.pdf").getName());
        assertEquals("application/pdf", repo.getMimeType(u).getName());
        assertEquals("application/pdf", repo.getMimeType(f).getName());
        assertEquals("text/plain", repo.getMimeType("x.txt").getName());
        assertEquals("text/html", repo.getMimeType("x.htm").getName());
        assertEquals("text/html", repo.getMimeType("x.html").getName());
        assertEquals("application/xhtml+xml", repo.getMimeType("x.xhtml")
                .getName());
        assertEquals("application/xml", repo.getMimeType("x.xml").getName());
        assertEquals("application/msword", repo.getMimeType("x.doc").getName());
        assertEquals("application/vnd.ms-powerpoint", repo.getMimeType("x.ppt")
                .getName());
        assertEquals("application/vnd.ms-excel", repo.getMimeType("x.xls")
                .getName());
        assertEquals("application/zip", repo.getMimeType("x.zip").getName());
        assertEquals("application/vnd.oasis.opendocument.text", repo
                .getMimeType("x.odt").getName());
        assertEquals("application/octet-stream", repo.getMimeType("x.xyz")
                .getName());
    }

    /**
     * Tests MimeTypes.getMimeType(URL), which examines both the byte header
     * and, if necessary, the URL's extension.
     */
    public void testMimeDeterminationForTestDocuments() {

        assertEquals("text/html", getMimeType("testHTML.html"));
        assertEquals("application/zip", getMimeType("test-documents.zip"));
        // TODO: Currently returns generic MS Office type based on
        // the magic header. The getMimeType method should understand
        // MS Office types better.
        // assertEquals("application/vnd.ms-excel",
        // getMimeType("testEXCEL.xls"));
        // assertEquals("application/vnd.ms-powerpoint",
        // getMimeType("testPPT.ppt"));
        // assertEquals("application/msword", getMimeType("testWORD.doc"));
        assertEquals("text/html", getMimeType("testHTML_utf8.html"));
        assertEquals("application/vnd.oasis.opendocument.text",
                getMimeType("testOpenOffice2.odt"));
        assertEquals("application/pdf", getMimeType("testPDF.pdf"));
        assertEquals("application/rtf", getMimeType("testRTF.rtf"));
        assertEquals("text/plain", getMimeType("testTXT.txt"));
        assertEquals("application/xml", getMimeType("testXML.xml"));
        assertEquals("audio/basic", getMimeType("testAU.au"));
        assertEquals("audio/x-aiff", getMimeType("testAIFF.aif"));
        assertEquals("audio/x-wav", getMimeType("testWAV.wav"));
        assertEquals("audio/midi", getMimeType("testMID.mid"));
    }

    private String getMimeType(String filename) {

        String type = null;

        try {
            URL url = getClass().getResource("/test-documents/" + filename);
            type = repo.getType(url);
        } catch (MalformedURLException e) {
            fail(e.getMessage());
        } catch (IOException e) {
            fail(e.getMessage());
        }

        return type;
    }

}
