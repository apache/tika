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
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import org.apache.tika.TikaTest;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;

public class Pkcs7EndToEndTest extends TikaTest {

    /**
     * TIKA-1997: a CMS-signed XML is detected as PKCS7, routed to Pkcs7Parser, refined to
     * signed-data, and its inner XML payload is unwrapped and its content extracted.
     */
    @Test
    public void testSignedXmlIsExtracted() throws Exception {
        List<Metadata> metadataList = getRecursiveMetadata("test.xml.p7m");
        // the container is refined from the coarse detected type to the CMS subtype
        assertEquals("application/pkcs7-mime; smime-type=signed-data",
                metadataList.get(0).get(Metadata.CONTENT_TYPE));
        // the signed XML payload is unwrapped and extracted
        StringBuilder content = new StringBuilder();
        for (Metadata m : metadataList) {
            String c = m.get(TikaCoreProperties.TIKA_CONTENT);
            if (c != null) {
                content.append(c);
            }
        }
        assertContains("TEST_APP", content.toString());
        assertContains("ESTRAZIONE", content.toString());
    }

    /** CMS compressedData (RFC 3274) is inflated and its inner payload extracted (here a PDF). */
    @Test
    public void testCompressedContentIsExtracted() throws Exception {
        List<Metadata> metadataList = getRecursiveMetadata("testPKCS7_compressed_def_long.p7z");
        assertEquals("application/pkcs7-mime; smime-type=compressed-data",
                metadataList.get(0).get(Metadata.CONTENT_TYPE));
        boolean pdf = metadataList.stream()
                .map(m -> m.get(Metadata.CONTENT_TYPE))
                .anyMatch(ct -> ct != null && ct.startsWith("application/pdf"));
        assertTrue(pdf, "expected an embedded application/pdf inflated from the compressed payload");
    }
}
