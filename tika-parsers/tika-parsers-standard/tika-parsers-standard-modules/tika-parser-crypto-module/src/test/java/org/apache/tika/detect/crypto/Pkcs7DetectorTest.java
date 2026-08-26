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
package org.apache.tika.detect.crypto;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.ParseContext;

/** The opt-in detector surfaces the CMS smime-type at detect time (instantiated directly, not SPI). */
public class Pkcs7DetectorTest {

    private final Pkcs7Detector detector = new Pkcs7Detector();

    private MediaType detect(String resource) throws Exception {
        try (TikaInputStream tis = TikaInputStream.get(
                Pkcs7DetectorTest.class.getResourceAsStream("/test-documents/" + resource))) {
            return detector.detect(tis, new Metadata(), new ParseContext());
        }
    }

    @Test
    public void testSubtypeAtDetectTime() throws Exception {
        assertEquals(MediaType.parse("application/pkcs7-mime; smime-type=signed-data"),
                detect("testPKCS7_signed_data_def.p7m"));
        assertEquals(MediaType.parse("application/pkcs7-mime; smime-type=enveloped-data"),
                detect("testPKCS7_enveloped_def.p7m"));
        assertEquals(MediaType.parse("application/pkcs7-mime; smime-type=certs-only"),
                detect("testPKCS7_certs_only_def.p7c"));
        assertEquals(MediaType.application("pkcs7-signature"),
                detect("testPKCS7_signature_def.p7s"));
    }

    @Test
    public void testNonCmsAndNull() throws Exception {
        assertEquals(MediaType.OCTET_STREAM, detector.detect(null, new Metadata(), new ParseContext()));
        try (TikaInputStream tis = TikaInputStream.get(new byte[]{0x30, 0x03, 0x02, 0x01, 0x00})) {
            assertEquals(MediaType.OCTET_STREAM, detector.detect(tis, new Metadata(), new ParseContext()));
        }
    }
}
