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
package org.apache.tika.grpc.mapper.transform;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import org.apache.tika.TikaTest;
import org.apache.tika.grpc.v1.Document;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.image.JpegParser;
import org.apache.tika.sax.BodyContentHandler;

/**
 * Verifies {@link ImageDocumentTransformer} against a real JPEG fixture carrying EXIF, GPS,
 * and IPTC metadata. Uses {@link JpegParser} directly rather than the auto-detecting parser:
 * on a machine with GDAL command-line tools installed, MIME-based auto-detection can route
 * {@code image/jpeg} to the (unrelated) GDAL raster parser instead, which does not extract
 * EXIF/TIFF metadata at all. Targeting the parser this transformer actually cares about keeps
 * the test deterministic regardless of what else is on the host.
 */
class ImageDocumentTransformerTest extends TikaTest {

    @Test
    void mapsTiffDimensionsAndTagsTheRest() throws Exception {
        Metadata metadata = new Metadata();
        // Calling JpegParser directly skips detection, so set Content-Type the way
        // AutoDetectParser normally would before delegating.
        metadata.set(Metadata.CONTENT_TYPE, "image/jpeg");
        try (TikaInputStream tis = getResourceAsStream("/test-documents/testJPEG_EXIF.jpg")) {
            new JpegParser().parse(tis, new BodyContentHandler(-1), metadata, new ParseContext());
        }

        ImageDocumentTransformer transformer = new ImageDocumentTransformer();
        assertTrue(transformer.appliesTo(metadata));

        Document.Builder builder = Document.newBuilder();
        java.util.Set<String> consumed = new java.util.HashSet<>();
        transformer.transform(metadata, builder, consumed);
        MetadataTagger.appendTail(metadata, consumed, builder);

        assertTrue(builder.getMetadata().getWidth() > 0);
        assertTrue(builder.getMetadata().getHeight() > 0);
        assertTrue(builder.getExtraCount() > 0);
    }
}
