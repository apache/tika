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

import static org.apache.tika.parser.crypto.PemFixtures.pem;
import static org.apache.tika.parser.crypto.PemFixtures.resource;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.xml.sax.ContentHandler;
import org.xml.sax.helpers.DefaultHandler;

import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.HttpHeaders;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;

public class Pkcs7ParserTest {

    /** Parse a fixture and return the content type the parser refined it to. */
    private String parsedType(String resource) throws Exception {
        return parsedType(resource(resource), new ParseContext());
    }

    private String parsedType(byte[] bytes, ParseContext context) throws Exception {
        Metadata metadata = new Metadata();
        try (TikaInputStream tis = TikaInputStream.get(bytes)) {
            new Pkcs7Parser().parse(tis, new DefaultHandler(), metadata, context);
        }
        return metadata.get(HttpHeaders.CONTENT_TYPE);
    }

    static Stream<Arguments> pemCases() {
        return Stream.of("testPKCS7_signed_data_def.p7m", "testPKCS7_signed_data_ind.p7m",
                        "testPKCS7_enveloped_def.p7m", "testPKCS7_certs_only_def.p7c",
                        "testPKCS7_certs_only_ind.p7c", "testPKCS7_compressed_def_long.p7z",
                        "testPKCS7_digested.p7", "testPKCS7_signature_def.p7s")
                .flatMap(r -> Stream.of(Arguments.of(r, "PKCS7", "\n"),
                        Arguments.of(r, "CMS", "\r\n")));
    }

    /** TIKA-5002: PEM armor refines exactly like the DER it wraps. */
    @ParameterizedTest
    @MethodSource("pemCases")
    public void testPemRefinesLikeDer(String resource, String label, String eol) throws Exception {
        byte[] der = resource(resource);
        String expected = parsedType(der, new ParseContext());
        assertNotNull(expected);
        assertEquals(expected, parsedType(pem(der, label, eol), new ParseContext()));
    }

    @Test
    public void testPemFixture() throws Exception {
        assertEquals("application/pkcs7-mime; smime-type=signed-data",
                parsedType("testPKCS7_signed_data_def.pem"));
    }

    @Test
    public void testPemPayloadExtracted() throws Exception {
        byte[] der = resource("test.xml.p7m");
        assertArrayEquals(extractedPayload(der), extractedPayload(pem(der, "PKCS7", "\n")));
    }

    @Test
    public void testMalformedPemDoesNotThrow() throws Exception {
        byte[] junk = "-----BEGIN PKCS7-----\nA===\n-----END PKCS7-----\n"
                .getBytes(StandardCharsets.US_ASCII);
        assertNull(parsedType(junk, new ParseContext()));
        byte[] notCms = pem("not cms".getBytes(StandardCharsets.US_ASCII), "CMS", "\n");
        assertNull(parsedType(notCms, new ParseContext()));
    }

    private byte[] extractedPayload(byte[] input) throws Exception {
        byte[][] payload = new byte[1][];
        ParseContext context = new ParseContext();
        context.set(Parser.class, new Parser() {
            @Override
            public Set<MediaType> getSupportedTypes(ParseContext ctx) {
                return Set.of();
            }

            @Override
            public void parse(TikaInputStream tis, ContentHandler handler, Metadata metadata,
                              ParseContext ctx) throws IOException {
                payload[0] = tis.readAllBytes();
            }
        });
        parsedType(input, context);
        assertNotNull(payload[0]);
        return payload[0];
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
