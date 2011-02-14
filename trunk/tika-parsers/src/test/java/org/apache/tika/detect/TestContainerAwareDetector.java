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
package org.apache.tika.detect;

import java.io.IOException;
import java.io.InputStream;

import junit.framework.TestCase;

import org.apache.poi.poifs.filesystem.POIFSFileSystem;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;
import org.apache.tika.mime.MimeTypes;

/**
 * Junit test class for {@link ContainerAwareDetector}
 */
public class TestContainerAwareDetector extends TestCase {

    private final ContainerAwareDetector detector =
        new ContainerAwareDetector(MimeTypes.getDefaultMimeTypes());

    private void assertDetect(String file, String type) throws Exception {
        TikaInputStream stream = TikaInputStream.get(
                TestContainerAwareDetector.class.getResource(
                        "/test-documents/" + file));
        try {
            assertEquals(
                    MediaType.parse(type),
                    detector.detect(stream, new Metadata()));
        } finally {
            stream.close();
        }
    }

    public void testDetectOLE2() throws Exception {
        // Microsoft office types known by POI
        assertDetect("testEXCEL.xls", "application/vnd.ms-excel");
        assertDetect("testWORD.doc", "application/msword");
        assertDetect("testPPT.ppt", "application/vnd.ms-powerpoint");

        // Try some ones that POI doesn't handle, that are still OLE2 based
        assertDetect("testWORKS.wps", "application/vnd.ms-works");
        assertDetect("testCOREL.shw", "application/x-corelpresentations");
        assertDetect("testQUATTRO.qpw", "application/x-quattro-pro");
        assertDetect("testQUATTRO.wb3", "application/x-quattro-pro");
    }

    public void testOpenContainer() throws Exception {
        TikaInputStream stream = TikaInputStream.get(
                TestContainerAwareDetector.class.getResource(
                        "/test-documents/testPPT.ppt"));
        try {
            assertNull(stream.getOpenContainer());
            assertEquals(
                    MediaType.parse("application/vnd.ms-powerpoint"),
                    detector.detect(stream, new Metadata()));
            assertTrue(stream.getOpenContainer() instanceof POIFSFileSystem);
        } finally {
            stream.close();
        }
    }

    public void testDetectODF() throws Exception {
        assertDetect("testODFwithOOo3.odt", "application/vnd.oasis.opendocument.text");
        assertDetect("testOpenOffice2.odf", "application/vnd.oasis.opendocument.formula");
    }

    public void testDetectOOXML() throws Exception {
        assertDetect("testEXCEL.xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        assertDetect("testWORD.docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document");
        assertDetect("testPPT.pptx", "application/vnd.openxmlformats-officedocument.presentationml.presentation");

        // Check some of the less common OOXML types
        assertDetect("testPPT.pptm", "application/vnd.ms-powerpoint.presentation.macroenabled.12");
        assertDetect("testPPT.ppsx", "application/vnd.openxmlformats-officedocument.presentationml.slideshow");
        assertDetect("testPPT.ppsm", "application/vnd.ms-powerpoint.slideshow.macroEnabled.12");
    }

    public void testDetectIWork() throws Exception {
        assertDetect("testKeynote.key", "application/vnd.apple.keynote");
        assertDetect("testNumbers.numbers", "application/vnd.apple.numbers");
        assertDetect("testPages.pages", "application/vnd.apple.pages");
    }

    public void testDetectZip() throws Exception {
        assertDetect("test-documents.zip", "application/zip");
        assertDetect("test-zip-of-zip.zip", "application/zip");
        assertDetect("testJAR.jar", "application/java-archive");
    }

    private TikaInputStream getTruncatedFile(String name, int n)
            throws IOException {
        InputStream input =
            TestContainerAwareDetector.class.getResourceAsStream(
                    "/test-documents/" + name);
        try {
            byte[] bytes = new byte[n];
            int m = 0;
            while (m < bytes.length) {
                int i = input.read(bytes, m, bytes.length - m);
                if (i != -1) {
                    m += i;
                } else {
                    throw new IOException("Unexpected end of stream");
                }
            }
            return TikaInputStream.get(bytes);
        } finally {
            input.close();
        }
    }

    public void testTruncatedFiles() throws Exception {
        // First up a truncated OOXML (zip) file
        TikaInputStream xlsx = getTruncatedFile("testEXCEL.xlsx", 300);
        try {
            assertEquals(
                    MediaType.APPLICATION_ZIP,
                    detector.detect(xlsx, new Metadata()));
        } finally {
            xlsx.close();
        }

        // Now a truncated OLE2 file 
        TikaInputStream xls = getTruncatedFile("testEXCEL.xls", 400);
        try {
            assertEquals(
                    MediaType.application("x-tika-msoffice"),
                    detector.detect(xls, new Metadata()));
        } finally {
            xls.close();
        }
   }

}
