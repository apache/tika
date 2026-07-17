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

import org.junit.jupiter.api.Test;
import org.xml.sax.helpers.DefaultHandler;

import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;

public class Pkcs7ParserTest {

    /** Parse a fixture and return the content type the parser refined it to. */
    private String parsedType(String resource) throws Exception {
        Metadata metadata = new Metadata();
        try (TikaInputStream tis = TikaInputStream.get(
                Pkcs7ParserTest.class.getResourceAsStream("/test-documents/" + resource))) {
            new Pkcs7Parser().parse(tis, new DefaultHandler(), metadata, new ParseContext());
        }
        return metadata.get(Metadata.CONTENT_TYPE);
    }

    @Test
    public void testSubtypeRefinement() throws Exception {
        assertEquals("application/pkcs7-mime; smime-type=signed-data",
                parsedType("testPKCS7_signed_data_def.p7m"));
        assertEquals("application/pkcs7-mime; smime-type=signed-data",
                parsedType("testPKCS7_signed_data_ind.p7m"));
        assertEquals("application/pkcs7-mime; smime-type=signed-data",
                parsedType("test.xml.p7m"));
        assertEquals("application/pkcs7-mime; smime-type=enveloped-data",
                parsedType("testPKCS7_enveloped_def.p7m"));
        assertEquals("application/pkcs7-mime; smime-type=enveloped-data",
                parsedType("testPKCS7_enveloped_ind.p7m"));
        assertEquals("application/pkcs7-mime; smime-type=certs-only",
                parsedType("testPKCS7_certs_only_def.p7c"));
        assertEquals("application/pkcs7-mime; smime-type=certs-only",
                parsedType("testPKCS7_certs_only_ind.p7c"));
        assertEquals("application/pkcs7-mime; smime-type=compressed-data",
                parsedType("testPKCS7_compressed_def_long.p7z"));
        assertEquals("application/pkcs7-mime; smime-type=compressed-data",
                parsedType("testPKCS7_compressed_ind.p7z"));
        // TIKA-2856: digestedData refined at parse time (coarse magic now routes it here)
        assertEquals("application/pkcs7-mime; smime-type=digested-data",
                parsedType("testPKCS7_digested.p7"));
    }

    /** Detached signatures are labelled pkcs7-signature and no longer throw. */
    @Test
    public void testDetachedSignatures() throws Exception {
        assertEquals("application/pkcs7-signature", parsedType("testPKCS7_signature_def.p7s"));
        assertEquals("application/pkcs7-signature", parsedType("testPKCS7_signature_ind.p7s"));
        assertEquals("application/pkcs7-signature", parsedType("testDetached.p7s"));
    }
}
