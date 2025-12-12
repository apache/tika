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

import org.apache.tika.TikaTest;
import org.apache.tika.digest.DigestDef;
import org.apache.tika.digest.SkipContainerDocumentDigest;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.AutoDetectParserConfig;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.digestutils.CommonsDigester;
import org.apache.tika.parser.digestutils.CommonsDigesterFactory;

/**
 * Tests for SkipContainerDocumentDigest functionality with MockParser and embedded documents.
 */
public class SkipContainerDocumentDigestTest extends TikaTest {

    private static final String DIGEST_KEY = TikaCoreProperties.TIKA_META_PREFIX + "digest" +
            TikaCoreProperties.NAMESPACE_PREFIX_DELIMITER + "MD5";

    @Test
    public void testDigestContainerAndEmbedded() throws Exception {
        // skipContainerDocumentDigest = false means digest everything
        AutoDetectParserConfig config = new AutoDetectParserConfig();
        config.digester(new CommonsDigester(100000, DigestDef.Algorithm.MD5));
        config.setSkipContainerDocumentDigest(false);

        AutoDetectParser parser = new AutoDetectParser();
        parser.setAutoDetectParserConfig(config);

        List<Metadata> metadataList = getRecursiveMetadata("mock_embedded_for_digest.xml", parser);

        // Should have container + embedded
        assertEquals(2, metadataList.size());

        // Container should have digest
        assertNotNull(metadataList.get(0).get(DIGEST_KEY),
                "Container document should have digest");

        // Embedded should have digest
        assertNotNull(metadataList.get(1).get(DIGEST_KEY),
                "Embedded document should have digest");
    }

    @Test
    public void testSkipContainerDigestOnly() throws Exception {
        // skipContainerDocumentDigest = true means skip container, digest only embedded
        AutoDetectParserConfig config = new AutoDetectParserConfig();
        config.digester(new CommonsDigester(100000, DigestDef.Algorithm.MD5));
        config.setSkipContainerDocumentDigest(true);

        AutoDetectParser parser = new AutoDetectParser();
        parser.setAutoDetectParserConfig(config);

        List<Metadata> metadataList = getRecursiveMetadata("mock_embedded_for_digest.xml", parser);

        // Should have container + embedded
        assertEquals(2, metadataList.size());

        // Container should NOT have digest
        assertNull(metadataList.get(0).get(DIGEST_KEY),
                "Container document should NOT have digest when skipContainerDocumentDigest=true");

        // Embedded should have digest
        assertNotNull(metadataList.get(1).get(DIGEST_KEY),
                "Embedded document should have digest");
    }

    @Test
    public void testSkipContainerDocumentDigestMarkerInParseContext() throws Exception {
        // Test that the SkipContainerDocumentDigest marker in ParseContext works
        AutoDetectParserConfig config = new AutoDetectParserConfig();
        config.digester(new CommonsDigester(100000, DigestDef.Algorithm.MD5));
        config.setSkipContainerDocumentDigest(false); // Config says digest all

        AutoDetectParser parser = new AutoDetectParser();
        parser.setAutoDetectParserConfig(config);

        // Set the marker in ParseContext to override config
        ParseContext context = new ParseContext();
        context.set(SkipContainerDocumentDigest.class, SkipContainerDocumentDigest.INSTANCE);

        List<Metadata> metadataList = getRecursiveMetadata("mock_embedded_for_digest.xml",
                parser, new Metadata(), context, false);

        // Should have container + embedded
        assertEquals(2, metadataList.size());

        // Container should NOT have digest because ParseContext marker overrides config
        assertNull(metadataList.get(0).get(DIGEST_KEY),
                "Container document should NOT have digest when ParseContext marker is set");

        // Embedded should have digest
        assertNotNull(metadataList.get(1).get(DIGEST_KEY),
                "Embedded document should have digest");
    }

    @Test
    public void testNoDigesterConfigured() throws Exception {
        // When no digester is configured, no digests should be computed
        AutoDetectParserConfig config = new AutoDetectParserConfig();
        // Don't set any digester

        AutoDetectParser parser = new AutoDetectParser();
        parser.setAutoDetectParserConfig(config);

        List<Metadata> metadataList = getRecursiveMetadata("mock_embedded_for_digest.xml", parser);

        // Should have container + embedded
        assertEquals(2, metadataList.size());

        // Neither should have digest
        assertNull(metadataList.get(0).get(DIGEST_KEY),
                "Container should not have digest when no digester configured");
        assertNull(metadataList.get(1).get(DIGEST_KEY),
                "Embedded should not have digest when no digester configured");
    }

    @Test
    public void testDigestWithFactory() throws Exception {
        // Test using the factory pattern
        CommonsDigesterFactory factory = new CommonsDigesterFactory();
        factory.setMarkLimit(100000);

        AutoDetectParserConfig config = new AutoDetectParserConfig();
        config.setDigesterFactory(factory);
        config.setSkipContainerDocumentDigest(false);

        AutoDetectParser parser = new AutoDetectParser();
        parser.setAutoDetectParserConfig(config);

        List<Metadata> metadataList = getRecursiveMetadata("mock_embedded_for_digest.xml", parser);

        // Should have container + embedded
        assertEquals(2, metadataList.size());
    }
}
