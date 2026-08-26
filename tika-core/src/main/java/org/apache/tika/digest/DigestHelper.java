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

import org.apache.commons.io.output.CloseShieldOutputStream;

import org.apache.tika.extractor.DefaultEmbeddedStreamTranslator;
import org.apache.tika.extractor.EmbeddedStreamTranslator;
import org.apache.tika.io.CacheMemoryBudget;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.parser.ParseContext;

/**
 * Utility class for computing digests on streams.
 * <p>
 * The DigesterFactory is retrieved from ParseContext. Configure it via
 * the "parse-context" section in tika-config.json:
 * <pre>
 * "parse-context": {
 *   "commons-digester-factory": {
 *     "digests": [{ "algorithm": "SHA256" }],
 *     "skipContainerDocumentDigest": true
 *   }
 * }
 * </pre>
 */
public class DigestHelper {

    private static final EmbeddedStreamTranslator EMBEDDED_STREAM_TRANSLATOR =
            new DefaultEmbeddedStreamTranslator();

    /**
     * Computes digests on the stream if a DigesterFactory is configured in ParseContext.
     * <p>
     * This is called directly from AutoDetectParser.parse() before type detection.
     *
     * @param tis      the TikaInputStream to digest
     * @param metadata metadata to read depth from and write digests to
     * @param context  parse context (should contain DigesterFactory, may contain SkipContainerDocumentDigest marker)
     * @throws IOException if an I/O error occurs
     */
    public static void maybeDigest(TikaInputStream tis,
                                   Metadata metadata,
                                   ParseContext context) throws IOException {
        DigesterFactory digesterFactory = context.get(DigesterFactory.class);

        if (digesterFactory == null) {
            return;
        }

        // Get skip setting from factory or ParseContext marker
        boolean skipContainer = digesterFactory.isSkipContainerDocumentDigest()
                || SkipContainerDocumentDigest.shouldSkip(context);

        if (skipContainer) {
            Integer depth = metadata.getInt(TikaCoreProperties.EMBEDDED_DEPTH);
            if (depth == null || depth == 0) {
                return;
            }
        }

        Digester digester = digesterFactory.build();

        // The translator consumes `tis` (e.g. OLE2), so enableRewind() before and rewind()
        // after -- otherwise the caller would see an exhausted stream.
        // Safe to tee here: the translator only writes, so no mark/reset/skip can
        // desynchronize the digest from the bytes.
        if (EMBEDDED_STREAM_TRANSLATOR.shouldTranslate(tis, metadata)) {
            tis.enableRewind(context.get(CacheMemoryBudget.class));
            try {
                DigestSink sink = digester.digestSink(metadata, context);
                try {
                    // close-shielded: a translator that closes the sink must not be able to
                    // publish on our behalf
                    EMBEDDED_STREAM_TRANSLATOR.translate(tis, metadata,
                            CloseShieldOutputStream.wrap(sink));
                    // only a translation that ran to completion publishes
                    sink.commit();
                } finally {
                    sink.close();
                }
            } finally {
                tis.rewind();
            }
        } else {
            digester.digest(tis, metadata, context);
        }
    }
}
