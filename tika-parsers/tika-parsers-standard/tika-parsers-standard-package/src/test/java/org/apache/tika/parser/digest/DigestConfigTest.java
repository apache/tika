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
package org.apache.tika.parser.digest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;

import org.junit.jupiter.api.Test;

import org.apache.tika.TikaLoaderHelper;
import org.apache.tika.TikaTest;
import org.apache.tika.config.loader.TikaLoader;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;

/**
 * Tests for digest configuration via JSON config files.
 * Tests both CommonsDigester and BouncyCastleDigester via AutoDetectParser configuration.
 */
public class DigestConfigTest extends TikaTest {

    private static final String P = TikaCoreProperties.TIKA_META_PREFIX + "digest" +
            TikaCoreProperties.NAMESPACE_PREFIX_DELIMITER;

    // Expected digest values for test_recursive_embedded.docx (lowercase hex)
    private static final String EXPECTED_MD2 = "d768c8e27b0b52c6eaabfaa7122d1d4f";
    private static final String EXPECTED_MD5 = "59f626e09a8c16ab6dbc2800c685f772";
    private static final String EXPECTED_SHA1 = "7a1f001d163ac90d8ea54c050faf5a38079788a6";
    private static final String EXPECTED_SHA256 =
            "c4b7fab030a8b6a9d6691f6699ac8e6f" + "82bc53764a0f1430d134ae3b70c32654";
    private static final String EXPECTED_SHA384 =
            "ebe368b9326fef44408290724d187553" + "8b8a6923fdf251ddab72c6e4b5d54160" +
                    "9db917ba4260d1767995a844d8d654df";
    private static final String EXPECTED_SHA512 =
            "ee46d973ee1852c018580c242955974d" + "da4c21f36b54d7acd06fcf68e974663b" +
                    "fed1d256875be58d22beacf178154cc3" + "a1178cb73443deaa53aa0840324708bb";
    private static final String EXPECTED_SHA3_512 =
            "04337f667a250348a1acb992863b3ddc" + "eab38365c206c18d356d2b31675ad669" +
                    "5fb5497f4e79b11640aefbb8042a5dbb" + "7ec6c2c6c1b6e19210453591c52cb6eb";
    private static final String EXPECTED_SHA1_BASE32 = "PIPQAHIWHLEQ3DVFJQCQ7L22HADZPCFG";

    // ================= CommonsDigester Tests =================

    @Test
    public void testCommonsDigesterBasic() throws Exception {
        TikaLoader loader = TikaLoaderHelper.getLoader("tika-config-commons-digests-basic.json");
        Parser p = loader.loadAutoDetectParser();
        ParseContext context = loader.loadParseContext();
        Metadata m = new Metadata();
        getXML("test_recursive_embedded.docx", p, m, context);

        assertEquals(EXPECTED_MD2, m.get(P + "MD2"), "MD2 digest should match");
        assertEquals(EXPECTED_MD5, m.get(P + "MD5"), "MD5 digest should match");
        assertEquals(EXPECTED_SHA1, m.get(P + "SHA1"), "SHA1 digest should match");
        assertEquals(EXPECTED_SHA256, m.get(P + "SHA256"), "SHA256 digest should match");
        assertEquals(EXPECTED_SHA384, m.get(P + "SHA384"), "SHA384 digest should match");
        assertEquals(EXPECTED_SHA512, m.get(P + "SHA512"), "SHA512 digest should match");
    }

    @Test
    public void testCommonsDigesterWithBase32() throws Exception {
        TikaLoader loader = TikaLoaderHelper.getLoader("tika-config-digests.json");
        Parser p = loader.loadAutoDetectParser();
        ParseContext context = loader.loadParseContext();
        Metadata m = new Metadata();
        getXML("test_recursive_embedded.docx", p, m, context);

        // SHA256 with BASE32 encoding - just verify it exists with non-default key
        assertNotNull(m.get(P + "SHA256:BASE32"),
                "SHA256:BASE32 digest should be present");
        // MD5 with default HEX encoding
        assertEquals(EXPECTED_MD5, m.get(P + "MD5"), "MD5 digest should match");
    }

    @Test
    public void testCommonsDigesterLengthsCalculated() throws Exception {
        // This tests that TIKA-4016 added lengths
        TikaLoader loader = TikaLoaderHelper.getLoader("tika-config-commons-digests-basic.json");
        Parser p = loader.loadAutoDetectParser();
        ParseContext context = loader.loadParseContext();
        List<Metadata> metadataList = getRecursiveMetadata("test_recursive_embedded.docx", p, context);
        for (Metadata m : metadataList) {
            assertNotNull(m.get(Metadata.CONTENT_LENGTH));
        }
    }

    @Test
    public void testCommonsDigesterSkipContainer() throws Exception {
        // Tests skipContainerDocumentDigest on the factory (configured in other-configs)
        TikaLoader loader = TikaLoaderHelper.getLoader("tika-config-digests-skip-container.json");
        Parser p = loader.loadAutoDetectParser();
        ParseContext context = loader.loadParseContext();
        List<Metadata> metadataList = getRecursiveMetadata("test_recursive_embedded.docx", p, context);

        // Container should NOT have digest
        assertNull(metadataList.get(0).get(P + "MD5"),
                "Container document should NOT have digest when skipContainerDocumentDigest=true");

        // Embedded documents should have digest
        for (int i = 1; i < metadataList.size(); i++) {
            assertNotNull(metadataList.get(i).get(P + "MD5"),
                    "Embedded document " + i + " should have digest");
        }
    }

    // ================= BouncyCastleDigester Tests =================

    @Test
    public void testBouncyCastleDigesterBasic() throws Exception {
        TikaLoader loader = TikaLoaderHelper.getLoader("tika-config-bc-digests-basic.json");
        Parser p = loader.loadAutoDetectParser();
        ParseContext context = loader.loadParseContext();
        Metadata m = new Metadata();
        getXML("test_recursive_embedded.docx", p, m, context);

        assertEquals(EXPECTED_MD2, m.get(P + "MD2"), "MD2 digest should match");
        assertEquals(EXPECTED_MD5, m.get(P + "MD5"), "MD5 digest should match");
        assertEquals(EXPECTED_SHA1, m.get(P + "SHA1"), "SHA1 digest should match");
        assertEquals(EXPECTED_SHA256, m.get(P + "SHA256"), "SHA256 digest should match");
        assertEquals(EXPECTED_SHA384, m.get(P + "SHA384"), "SHA384 digest should match");
        assertEquals(EXPECTED_SHA512, m.get(P + "SHA512"), "SHA512 digest should match");
    }

    @Test
    public void testBouncyCastleDigesterMultipleAlgorithms() throws Exception {
        TikaLoader loader = TikaLoaderHelper.getLoader("tika-config-bc-digests-multiple.json");
        Parser p = loader.loadAutoDetectParser();
        ParseContext context = loader.loadParseContext();
        Metadata m = new Metadata();
        getXML("test_recursive_embedded.docx", p, m, context);

        assertEquals(EXPECTED_MD5, m.get(P + "MD5"), "MD5 digest should match");
        assertEquals(EXPECTED_SHA256, m.get(P + "SHA256"), "SHA256 digest should match");
        assertEquals(EXPECTED_SHA384, m.get(P + "SHA384"), "SHA384 digest should match");
        assertEquals(EXPECTED_SHA512, m.get(P + "SHA512"), "SHA512 digest should match");
        assertEquals(EXPECTED_SHA3_512, m.get(P + "SHA3_512"), "SHA3_512 digest should match");

        // MD2 was not configured
        assertNull(m.get(P + "MD2"), "MD2 should not be present");
    }

    @Test
    public void testBouncyCastleDigesterBase32Encoding() throws Exception {
        TikaLoader loader = TikaLoaderHelper.getLoader("tika-config-bc-digests-base32.json");
        Parser p = loader.loadAutoDetectParser();
        ParseContext context = loader.loadParseContext();
        Metadata m = new Metadata();
        getXML("test_recursive_embedded.docx", p, m, context);

        // Non-default encoding includes encoding in the key
        assertEquals(EXPECTED_SHA1_BASE32, m.get(P + "SHA1:BASE32"),
                "SHA1 BASE32 digest should match");
    }

    @Test
    public void testBouncyCastleDigesterLengthsCalculated() throws Exception {
        TikaLoader loader = TikaLoaderHelper.getLoader("tika-config-bc-digests-basic.json");
        Parser p = loader.loadAutoDetectParser();
        ParseContext context = loader.loadParseContext();
        List<Metadata> metadataList = getRecursiveMetadata("test_recursive_embedded.docx", p, context);
        for (Metadata m : metadataList) {
            assertNotNull(m.get(Metadata.CONTENT_LENGTH));
        }
    }
}
