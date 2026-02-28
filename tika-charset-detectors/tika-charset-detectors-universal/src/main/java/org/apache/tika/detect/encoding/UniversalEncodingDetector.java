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
package org.apache.tika.detect.encoding;

import java.io.IOException;
import java.io.Serializable;
import java.nio.charset.Charset;

import org.apache.tika.config.ConfigDeserializer;
import org.apache.tika.config.JsonConfig;
import org.apache.tika.config.TikaComponent;
import org.apache.tika.detect.EncodingDetector;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;

// TODO(TIKA-XXXX): UniversalEncodingDetector is registered as SPI (spi=true) as a
// workaround for a weakness in MlEncodingDetector with short, repetitive CJK byte sequences
// such as Shift_JIS / EUC-JP / Big5 / GBK filenames in ZIP archives.
//
// Root cause: MlEncodingDetector uses byte bigram statistics trained on natural-language text.
// A 9-byte Shift_JIS filename repeated ~11 times to reach MIN_BYTES_FOR_DETECTING_CHARSET
// produces an artificial, perfectly periodic bigram distribution that doesn't match anything
// in the model's training corpus.  The model currently returns ISO-8859-7 (Greek) for the
// pattern [0x95,0xB6,0x8F,0xCD] repeated — bytes that are structurally valid Shift_JIS
// double-byte pairs but look like high Latin/Greek bytes to a statistical model.
//
// Universal (juniversalchardet) correctly identifies these via its state-machine prober,
// which checks byte-level structural validity rather than learned distributions.
// CharSoupEncodingDetector arbitrates between ML and Universal: when language scoring is
// inconclusive (as it is for a repeated 9-byte filename), it falls back to the candidate
// with the fewest U+FFFD replacement characters — which is always the structurally valid one.
//
// Follow-up work once the MADLAD training data is fully downloaded and the model is retrained:
//   1. Add Shift_JIS (cp932), EUC-JP, EUC-KR, Big5, and GBK training samples that include
//      SHORT natural-text content (words, short sentences — not just paragraphs) to improve
//      ML detection in the 20–100 byte range where it currently has a gap.
//   2. Evaluate using EvalCharsetDetectors / DiagnoseCharsetDetector with short CJK inputs
//      to measure improvement and find remaining weak spots.
//
// Universal is NOT a temporary workaround to be replaced — it is a structural complement to
// the statistical ML approach.  Rule-based state-machine probers (Universal) win on very short
// or repetitive byte sequences because they apply encoding-spec constraints (is byte N a valid
// lead byte? is byte N+1 a valid trail byte?) and need no statistical texture at all.
// Statistical models (ML) win on longer, varied natural text where distributions are rich.
// This is a well-known pattern: rules beat statistics at the extremes; statistics beat rules
// in the ambiguous middle.  The ML + Universal + CharSoup design exploits both strengths.
@TikaComponent
public class UniversalEncodingDetector implements EncodingDetector {

    private static final int BUFSIZE = 1024;

    private static final int DEFAULT_MARK_LIMIT = 16 * BUFSIZE;

    /**
     * Configuration class for JSON deserialization.
     */
    public static class Config implements Serializable {
        private int markLimit = DEFAULT_MARK_LIMIT;

        public int getMarkLimit() {
            return markLimit;
        }

        public void setMarkLimit(int markLimit) {
            this.markLimit = markLimit;
        }
    }

    private Config defaultConfig = new Config();

    /**
     * Default constructor for SPI loading.
     */
    public UniversalEncodingDetector() {
    }

    /**
     * Constructor with explicit Config object.
     *
     * @param config the configuration
     */
    public UniversalEncodingDetector(Config config) {
        this.defaultConfig = config;
    }

    /**
     * Constructor for JSON configuration.
     * Requires Jackson on the classpath.
     *
     * @param jsonConfig JSON configuration
     */
    public UniversalEncodingDetector(JsonConfig jsonConfig) {
        this(ConfigDeserializer.buildConfig(jsonConfig, Config.class));
    }

    public Charset detect(TikaInputStream tis, Metadata metadata, ParseContext parseContext) throws IOException {
        if (tis == null) {
            return null;
        }

        int markLimit = defaultConfig.getMarkLimit();
        tis.mark(markLimit);
        try {
            UniversalEncodingListener listener = new UniversalEncodingListener(metadata);

            byte[] b = new byte[BUFSIZE];
            int n = 0;
            int m = tis.read(b);
            while (m != -1 && n < markLimit && !listener.isDone()) {
                n += m;
                listener.handleData(b, 0, m);
                m = tis.read(b, 0, Math.min(b.length, markLimit - n));
            }

            return listener.dataEnd();
        } catch (LinkageError e) {
            return null; // juniversalchardet is not available
        } finally {
            tis.reset();
        }
    }

    public Config getDefaultConfig() {
        return defaultConfig;
    }
}
