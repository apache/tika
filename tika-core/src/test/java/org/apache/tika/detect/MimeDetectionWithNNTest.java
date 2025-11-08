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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.ByteArrayInputStream;
import java.io.IOException;


import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;
import org.apache.tika.mime.MimeDetectionTest;

public class MimeDetectionWithNNTest {

    private Detector detector;

    /**
     * @inheritDoc
     */
    @BeforeEach
    public void setUp() {
        detector = new NNExampleModelDetector();
    }

    /**
     * The test case only works on the detector that only has grb model as
     * currently the grb model is used as an example; if more models are added
     * in the TrainedModelDetector, the following tests will need to modified to reflect
     * the corresponding type instead of test-equal with the "OCTET_STREAM";
     *
     * @throws Exception
     */
    @Test
    public void testDetection() throws Exception {
        String octetStream_str = MediaType.OCTET_STREAM.toString();
        String grb_str = "application/x-grib";

        testFile(grb_str, "gdas1.forecmwf.2014062612.grib2");
        testFile(grb_str, "GLDAS_CLM10SUBP_3H.A19790202.0000.001.grb");

        testFile(octetStream_str, "circles.svg");
        testFile(octetStream_str, "circles-with-prefix.svg");
        testFile(octetStream_str, "datamatrix.png");
        testFile(octetStream_str, "test.html");
        testFile(octetStream_str, "test-iso-8859-1.xml");
        testFile(octetStream_str, "test-utf8.xml");
        testFile(octetStream_str, "test-utf8-bom.xml");
        testFile(octetStream_str, "test-utf16le.xml");
        testFile(octetStream_str, "test-utf16be.xml");
        testFile(octetStream_str, "test-long-comment.xml");
        testFile(octetStream_str, "stylesheet.xsl");
        testUrl(octetStream_str, "http://www.ai.sri.com/daml/services/owl-s/1.2/Process.owl",
                "test-difficult-rdf1.xml");
        testUrl(octetStream_str, "http://www.w3.org/2002/07/owl#", "test-difficult-rdf2.xml");
        // add evil test from TIKA-327
        testFile(octetStream_str, "test-tika-327.html");
        // add another evil html test from TIKA-357
        testFile(octetStream_str, "testlargerbuffer.html");
        // test fragment of HTML with <div> (TIKA-1102)
        testFile(octetStream_str, "htmlfragment");
        // test binary CGM detection (TIKA-1170)
        testFile(octetStream_str, "plotutils-bin-cgm-v3.cgm");
        // test HTML detection of malformed file, previously identified as
        // image/cgm (TIKA-1170)
        testFile(octetStream_str, "test-malformed-header.html.bin");

        // test GCMD Directory Interchange Format (.dif) TIKA-1561
        testFile(octetStream_str, "brwNIMS_2014.dif");
    }

    private void testUrl(String expected, String url, String file) throws IOException {
        TikaInputStream tis = TikaInputStream.get(MimeDetectionTest.class.getResourceAsStream(file));
        testStream(expected, url, tis);
    }

    private void testFile(String expected, String filename) throws IOException {

        TikaInputStream tis = TikaInputStream.get(MimeDetectionTest.class.getResourceAsStream(filename));
        testStream(expected, filename, tis);
    }

    private void testStream(String expected, String urlOrFileName, TikaInputStream tis)
            throws IOException {
        assertNotNull(tis, "Test stream: [" + urlOrFileName + "] is null!");

        try {
            Metadata metadata = new Metadata();
            String mime = this.detector.detect(tis, metadata).toString();
            assertEquals(expected, mime,
                    urlOrFileName + " is not properly detected: detected.");

            // Add resource name and test again
            // metadata.set(TikaCoreProperties.RESOURCE_NAME_KEY, urlOrFileName);
            mime = this.detector.detect(tis, metadata).toString();
            assertEquals(expected, mime,
                    urlOrFileName + " is not properly detected after adding resource name.");
        } finally {
            tis.close();
        }
    }

    /**
     * Test for type detection of empty documents.
     */
    @Test
    public void testEmptyDocument() throws IOException {
        assertEquals(MediaType.OCTET_STREAM,
                detector.detect(TikaInputStream.get(new byte[0]), new Metadata()));

    }

}
