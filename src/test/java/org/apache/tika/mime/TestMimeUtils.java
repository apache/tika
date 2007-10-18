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

//JDK imports
import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;

import org.apache.tika.metadata.TikaMimeKeys;

import junit.framework.TestCase;

/**
 * 
 * Test Suite for the {@link MimeTypes} repository.
 * 
 */
public class TestMimeUtils extends TestCase implements TikaMimeKeys {

    private static final String tikaMimeFile = "org/apache/tika/mime/tika-mimetypes.xml";
    
    private static URL u;

    static {
        try {
            u = new URL("http://mydomain.com/x.pdf?x=y");
        } catch (MalformedURLException e) {
            fail(e.getMessage());
        }
    }

    private static final File f = new File("/a/b/c/x.pdf");

    private MimeUtils utils;

    public TestMimeUtils() {
        utils = new MimeUtils(tikaMimeFile);
        assertNotNull(utils);
    }

    public void testLoadMimeTypes() {
        assertNotNull(utils.getRepository().forName("application/octet-stream"));
        assertNotNull(utils.getRepository().forName("text/x-tex"));
    }


    /**
     * Tests MIME type determination based solely on the URL's extension.
     */
    public void testGuessMimeTypes() {

        assertEquals("application/pdf", utils.getRepository().getMimeType(
                "x.pdf").getName());
        assertEquals("application/pdf", utils.getRepository().getMimeType(u)
                .getName());
        assertEquals("application/pdf", utils.getRepository().getMimeType(f)
                .getName());
        assertEquals("text/plain", utils.getRepository().getMimeType("x.txt")
                .getName());
        assertEquals("text/html", utils.getRepository().getMimeType("x.htm")
                .getName());
        assertEquals("text/html", utils.getRepository().getMimeType("x.html")
                .getName());
        assertEquals("application/xhtml+xml", utils.getRepository()
                .getMimeType("x.xhtml").getName());
        assertEquals("application/xml", utils.getRepository().getMimeType(
                "x.xml").getName());
        assertEquals("application/msword", utils.getRepository().getMimeType(
                "x.doc").getName());
        assertEquals("application/vnd.ms-powerpoint", utils.getRepository()
                .getMimeType("x.ppt").getName());
        assertEquals("application/vnd.ms-excel", utils.getRepository()
                .getMimeType("x.xls").getName());
        assertEquals("application/zip", utils.getRepository().getMimeType(
                "x.zip").getName());
        assertEquals("application/vnd.oasis.opendocument.text", utils
                .getRepository().getMimeType("x.odt").getName());
        assertEquals("application/octet-stream", utils.getRepository()
                .getMimeType("x.xyz").getName());
    }


    /**
     * Tests MimeUtils.getMimeType(URL), which examines both the byte header
     * and, if necessary, the URL's extension.
     */
    public void testMimeDeterminationForTestDocuments() {

        assertEquals("text/html", getMimeType("testHTML.html"));
        assertEquals("application/zip", getMimeType("test-documents.zip"));
        assertEquals("application/vnd.ms-excel", getMimeType("testEXCEL.xls"));
        assertEquals("text/html", getMimeType("testHTML_utf8.html"));
        assertEquals("application/vnd.oasis.opendocument.text",
                getMimeType("testOpenOffice2.odt"));
        assertEquals("application/pdf", getMimeType("testPDF.pdf"));
        assertEquals("application/vnd.ms-powerpoint", getMimeType("testPPT.ppt"));
        assertEquals("application/rtf", getMimeType("testRTF.rtf"));
        assertEquals("text/plain", getMimeType("testTXT.txt"));
        assertEquals("application/msword", getMimeType("testWORD.doc"));
        assertEquals("application/xml", getMimeType("testXML.xml"));
    }

    private String getMimeType(String filename) {

        String type = null;

        try {
            URL url = getClass().getResource("/test-documents/" + filename);
            type = utils.getType(url);
        } catch (MalformedURLException e) {
            fail(e.getMessage());
        } catch (IOException e) {
            fail(e.getMessage());
        }

        return type;
    }
}
