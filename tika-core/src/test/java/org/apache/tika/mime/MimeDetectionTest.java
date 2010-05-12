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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

import junit.framework.TestCase;

import org.apache.tika.config.TikaConfig;
import org.apache.tika.metadata.Metadata;

public class MimeDetectionTest extends TestCase {

    private MimeTypes mimeTypes;

    /** @inheritDoc */
    @Override
    protected void setUp() throws Exception {
        super.setUp();
        this.mimeTypes = TikaConfig.getDefaultConfig().getMimeRepository();
        //this.mimeTypes = MimeTypesFactory.create("/org/apache/tika/mime/tika-mimetypes-minimal.xml");
    }

    public void testDetection() throws Exception {
        testFile("image/svg+xml", "circles.svg");
        testFile("image/svg+xml", "circles-with-prefix.svg");
        testFile("image/png", "datamatrix.png");
        testFile("text/html", "test.html");
        testFile("application/xml", "test-iso-8859-1.xml");
        testFile("application/xml", "test-utf8.xml");
        testFile("application/xml", "test-utf16le.xml");
        testFile("application/xml", "test-utf16be.xml");
        testFile("application/xml", "test-long-comment.xml");
        testFile("application/xslt+xml", "stylesheet.xsl");
        testUrl(
                "application/rdf+xml",
                "http://www.ai.sri.com/daml/services/owl-s/1.2/Process.owl",
                "test-difficult-rdf1.xml");
        testUrl(
                "application/rdf+xml",
                "http://www.w3.org/2002/07/owl#",
                "test-difficult-rdf2.xml");
        // add evil test from TIKA-327
        testFile("text/html", "evilhtml.html");
        // add another evil html test from TIKA-357
        testFile("text/html", "testlargerbuffer.html");
    }

    public void testByteOrderMark() throws Exception {
        assertEquals(MediaType.TEXT_PLAIN, mimeTypes.detect(
                new ByteArrayInputStream("\ufffetest".getBytes("UTF-16LE")),
                new Metadata()));
        assertEquals(MediaType.TEXT_PLAIN, mimeTypes.detect(
                new ByteArrayInputStream("\ufffetest".getBytes("UTF-16BE")),
                new Metadata()));
        assertEquals(MediaType.TEXT_PLAIN, mimeTypes.detect(
                new ByteArrayInputStream("\ufffetest".getBytes("UTF-8")),
                new Metadata()));
    }

    public void testAutosetSupertype() throws MimeTypeException {
    	MimeTypes types = new MimeTypes();
    	MimeType type = types.forName("application/something+xml");
    	assertEquals("application/xml", type.getSuperType().getName());
    	
    	type = types.forName("text/something");
    	assertEquals("text/plain", type.getSuperType().getName());
    }

    private void testUrl(String expected, String url, String file) throws IOException{
        InputStream in = getClass().getResourceAsStream(file);
        testStream(expected, url, in);
    }

    private void testFile(String expected, String filename) throws IOException {
        InputStream in = getClass().getResourceAsStream(filename);
        testStream(expected, filename, in);
    }
    
    private void testStream(String expected, String urlOrFileName, InputStream in) throws IOException{
        assertNotNull("Test stream: ["+urlOrFileName+"] is null!", in);
        if (!in.markSupported()) {
            in = new java.io.BufferedInputStream(in);
        }
        try {
            Metadata metadata = new Metadata();
            String mime = this.mimeTypes.detect(in, metadata).toString();
            assertEquals(urlOrFileName + " is not properly detected: detected.", expected, mime);

            //Add resource name and test again
            metadata.set(Metadata.RESOURCE_NAME_KEY, urlOrFileName);
            mime = this.mimeTypes.detect(in, metadata).toString();
            assertEquals(urlOrFileName + " is not properly detected after adding resource name.", expected, mime);
        } finally {
            in.close();
        }        
    }

}
