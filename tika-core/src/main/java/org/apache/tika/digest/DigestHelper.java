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
package org.apache.tika.digest;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.tika.extractor.DefaultEmbeddedStreamTranslator;
import org.apache.tika.extractor.EmbeddedStreamTranslator;
import org.apache.tika.io.TemporaryResources;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.parser.ParseContext;

/**
 * Utility class for computing digests on streams.
 * This follows the same pattern as AutoDetectParser's maybeSpool() method.
 */
public class DigestHelper {

    private static final EmbeddedStreamTranslator EMBEDDED_STREAM_TRANSLATOR =
            new DefaultEmbeddedStreamTranslator();

    /**
     * Computes digests on the stream if configured.
     * This is called directly from AutoDetectParser.parse() before type detection.
     *
     * @param tis                          the TikaInputStream to digest
     * @param digester                     the digester to use (may be null)
     * @param skipContainerDocumentDigest  if true, skip digesting for top-level documents (depth 0)
     * @param metadata                     metadata to read embedded depth from and write digests to
     * @param context                      parse context (may contain SkipContainerDocumentDigest marker)
     * @throws IOException if an I/O error occurs
     */
    public static void maybeDigest(TikaInputStream tis,
                                   Digester digester,
                                   boolean skipContainerDocumentDigest,
                                   Metadata metadata,
                                   ParseContext context) throws IOException {
        if (digester == null) {
            return;
        }
        // Check both the config setting and the ParseContext marker
        if (skipContainerDocumentDigest || SkipContainerDocumentDigest.shouldSkip(context)) {
            Integer depth = metadata.getInt(TikaCoreProperties.EMBEDDED_DEPTH);
            if (depth == null || depth == 0) {
                return;
            }
        }

        // Handle embedded stream translation if needed (e.g., for OLE2 objects in TikaInputStream's open container)
        if (EMBEDDED_STREAM_TRANSLATOR.shouldTranslate(tis, metadata)) {
            try (TemporaryResources tmp = new TemporaryResources()) {
                Path tmpBytes = tmp.createTempFile();
                try (OutputStream os = Files.newOutputStream(tmpBytes)) {
                    EMBEDDED_STREAM_TRANSLATOR.translate(tis, metadata, os);
                }
                try (TikaInputStream translated = TikaInputStream.get(tmpBytes)) {
                    digester.digest(translated, metadata, context);
                }
            }
        } else {
            digester.digest(tis, metadata, context);
        }
    }
}
