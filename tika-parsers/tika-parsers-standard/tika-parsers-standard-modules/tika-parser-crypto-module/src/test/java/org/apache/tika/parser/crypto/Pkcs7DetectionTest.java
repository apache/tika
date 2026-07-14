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
package org.apache.tika.parser.crypto;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.InputStream;

import org.junit.jupiter.api.Test;

import org.apache.tika.Tika;

/**
 * Content-only (magic) detection of the ASN.1/DER crypto families. Magic is coarse — the CMS
 * subtype (signed vs enveloped) is refined by {@link Pkcs7Parser}, not magic — so signed/enveloped
 * both detect as pkcs7-signature here.
 */
public class Pkcs7DetectionTest {

    private final Tika tika = new Tika();

    private String detect(String resource) throws Exception {
        try (InputStream is = Pkcs7DetectionTest.class.getResourceAsStream(
                "/test-documents/" + resource)) {
            return tika.detect(is);
        }
    }

    @Test
    public void testContentOnlyDetection() throws Exception {
        // new/fixed magic
        assertEquals("application/x-pkcs12", detect("testRSAKEYandCERT.p12"));
        assertEquals("application/timestamped-data", detect("Test4.pdf.tsd"));
        assertEquals("application/pkcs7-mime", detect("testPKCS7_compressed_def_long.p7z"));
        assertEquals("application/pkcs7-mime", detect("testPKCS7_compressed_ind.p7z"));
        // existing masked CMS magic detects signed/enveloped coarsely as pkcs7-signature by content
        assertEquals("application/pkcs7-signature", detect("testPKCS7_signed_data_def.p7m"));
        assertEquals("application/pkcs7-signature", detect("testPKCS7_enveloped_def.p7m"));
    }
}
