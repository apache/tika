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
import org.apache.tika.digest.DigesterFactory;
import org.apache.tika.digest.SkipContainerDocumentDigest;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.digestutils.CommonsDigesterFactory;

/**
 * Tests for SkipContainerDocumentDigest functionality with MockParser and embedded documents.
 * DigesterFactory is now configured via ParseContext (via other-configs in JSON).
 */
public class SkipContainerDocumentDigestTest extends TikaTest {

    private static final String DIGEST_KEY = TikaCoreProperties.TIKA_META_PREFIX + "digest" +
            TikaCoreProperties.NAMESPACE_PREFIX_DELIMITER + "MD5";

    @Test
    public void testDigestContainerAndEmbedded() throws Exception {
        // skipContainerDocumentDigest = false means digest everything
        CommonsDigesterFactory factory = new CommonsDigesterFactory();
        factory.setSkipContainerDocumentDigest(false);

        AutoDetectParser parser = new AutoDetectParser();

        ParseContext context = new ParseContext();
        context.set(DigesterFactory.class, factory);

        List<Metadata> metadataList = getRecursiveMetadata("mock_embedded_for_digest.xml",
                parser, new Metadata(), context, false);

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
        CommonsDigesterFactory factory = new CommonsDigesterFactory();
        factory.setSkipContainerDocumentDigest(true);

        AutoDetectParser parser = new AutoDetectParser();

        ParseContext context = new ParseContext();
        context.set(DigesterFactory.class, factory);

        List<Metadata> metadataList = getRecursiveMetadata("mock_embedded_for_digest.xml",
                parser, new Metadata(), context, false);

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
        CommonsDigesterFactory factory = new CommonsDigesterFactory();
        factory.setSkipContainerDocumentDigest(false); // Factory says digest all

        AutoDetectParser parser = new AutoDetectParser();

        // Set both factory and the marker in ParseContext - marker overrides factory
        ParseContext context = new ParseContext();
        context.set(DigesterFactory.class, factory);
        context.set(SkipContainerDocumentDigest.class, SkipContainerDocumentDigest.INSTANCE);

        List<Metadata> metadataList = getRecursiveMetadata("mock_embedded_for_digest.xml",
                parser, new Metadata(), context, false);

        // Should have container + embedded
        assertEquals(2, metadataList.size());

        // Container should NOT have digest because ParseContext marker overrides factory
        assertNull(metadataList.get(0).get(DIGEST_KEY),
                "Container document should NOT have digest when ParseContext marker is set");

        // Embedded should have digest
        assertNotNull(metadataList.get(1).get(DIGEST_KEY),
                "Embedded document should have digest");
    }

    @Test
    public void testNoDigesterConfigured() throws Exception {
        // When no digester is configured in ParseContext, no digests should be computed
        AutoDetectParser parser = new AutoDetectParser();

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
    public void testDigestWithFactoryInParseContext() throws Exception {
        // Test that DigesterFactory in ParseContext is used
        CommonsDigesterFactory factory = new CommonsDigesterFactory();
        factory.setSkipContainerDocumentDigest(false);

        AutoDetectParser parser = new AutoDetectParser();

        ParseContext context = new ParseContext();
        context.set(DigesterFactory.class, factory);

        List<Metadata> metadataList = getRecursiveMetadata("mock_embedded_for_digest.xml",
                parser, new Metadata(), context, false);

        // Should have container + embedded
        assertEquals(2, metadataList.size());

        // Both should have digest
        assertNotNull(metadataList.get(0).get(DIGEST_KEY),
                "Container document should have digest when ParseContext provides factory");
        assertNotNull(metadataList.get(1).get(DIGEST_KEY),
                "Embedded document should have digest when ParseContext provides factory");
    }

    @Test
    public void testSkipContainerOnFactory() throws Exception {
        // Test skipContainerDocumentDigest configured on the factory
        CommonsDigesterFactory factory = new CommonsDigesterFactory();
        factory.setSkipContainerDocumentDigest(true);

        AutoDetectParser parser = new AutoDetectParser();

        ParseContext context = new ParseContext();
        context.set(DigesterFactory.class, factory);

        List<Metadata> metadataList = getRecursiveMetadata("mock_embedded_for_digest.xml",
                parser, new Metadata(), context, false);

        // Should have container + embedded
        assertEquals(2, metadataList.size());

        // Container should NOT have digest because factory says to skip
        assertNull(metadataList.get(0).get(DIGEST_KEY),
                "Container document should NOT have digest when factory.skipContainerDocumentDigest=true");

        // Embedded should have digest
        assertNotNull(metadataList.get(1).get(DIGEST_KEY),
                "Embedded document should have digest");
    }
}
