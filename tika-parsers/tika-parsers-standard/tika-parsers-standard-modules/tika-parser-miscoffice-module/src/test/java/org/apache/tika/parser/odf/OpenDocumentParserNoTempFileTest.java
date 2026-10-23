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
package org.apache.tika.parser.odf;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import org.apache.tika.TikaTest;
import org.apache.tika.detect.DefaultDetector;
import org.apache.tika.detect.Detector;
import org.apache.tika.io.CacheMemoryBudget;
import org.apache.tika.io.TemporaryResources;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.BodyContentHandler;

/**
 * Inline pictures were spooled to a temp file before detection; with rewind support the
 * entry stays in memory. Watching the temp directory cannot see this -- each entry gets its
 * own TemporaryResources -- so the assertion is on the stream the detector is handed.
 */
public class OpenDocumentParserNoTempFileTest extends TikaTest {

    /** Records whether each inline picture reached detection backed by a file. */
    private static class SpyDetector implements Detector {
        private final Detector delegate = new DefaultDetector();
        private final List<Boolean> pictureHadFile = new ArrayList<>();

        @Override
        public MediaType detect(TikaInputStream input, Metadata metadata, ParseContext context)
                throws IOException {
            String type = metadata.get(TikaCoreProperties.EMBEDDED_RESOURCE_TYPE);
            if (TikaCoreProperties.EmbeddedResourceType.INLINE.toString().equals(type)) {
                pictureHadFile.add(input.hasFile());
            }
            return delegate.detect(input, metadata, context);
        }
    }

    @Test
    public void testInlinePicturesAreDetectedFromMemory() throws Exception {
        byte[] bytes;
        try (InputStream is = getResourceAsStream("/test-documents/testODTEmbedded.odt")) {
            bytes = is.readAllBytes();
        }
        SpyDetector spy = new SpyDetector();
        ParseContext context = new ParseContext();
        context.set(Detector.class, spy);
        context.set(CacheMemoryBudget.class, new CacheMemoryBudget(64L * 1024 * 1024));
        Metadata metadata = new Metadata();
        BodyContentHandler handler = new BodyContentHandler();
        try (TemporaryResources tmp = new TemporaryResources()) {
            TikaInputStream tis = TikaInputStream.get(new ByteArrayInputStream(bytes), tmp, metadata);
            new OpenDocumentParser().parse(tis, handler, metadata, context);
        }
        assertFalse(spy.pictureHadFile.isEmpty(), "no inline picture reached detection");
        for (Boolean hadFile : spy.pictureHadFile) {
            assertFalse(hadFile, "inline picture was spooled to disk before detection");
        }
        assertTrue(handler.toString().length() > 0, "content was extracted");
    }

    /** The picture must be re-readable after detection, or the embedded parse sees nothing. */
    @Test
    public void testEmbeddedPicturesStillParseAfterRewind() throws Exception {
        List<Metadata> metadataList = getRecursiveMetadata("testODTEmbedded.odt");
        boolean sawImage = false;
        for (Metadata m : metadataList) {
            assertEquals(null, m.get(TikaCoreProperties.EMBEDDED_EXCEPTION),
                    "embedded exception for " + m.get(TikaCoreProperties.RESOURCE_NAME_KEY));
            String type = m.get(TikaCoreProperties.EMBEDDED_RESOURCE_TYPE);
            if (TikaCoreProperties.EmbeddedResourceType.INLINE.toString().equals(type)) {
                sawImage = true;
            }
        }
        assertTrue(sawImage, "no inline picture in the recursive metadata");
    }
}
