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
package org.apache.tika.ml.chardetect;

import java.io.IOException;
import java.util.Arrays;

import org.apache.commons.io.IOUtils;

import org.apache.tika.io.TikaInputStream;

/**
 * Reads an encoding-detection probe sized by <em>content</em>, not raw bytes.
 *
 * <p>A fixed raw probe (e.g. 16&nbsp;KB) starves the detectors when a page
 * leads with a large {@code <head>}/inline script: after tag-stripping there's
 * little body text, and the bytes that distinguish charsets sit past the
 * window.  This grows the read until ~{@code contentTarget} bytes of
 * tag-stripped content are present, capped at {@code rawCap} raw bytes.
 *
 * <p>Text-rich pages stop early (~one chunk); markup-heavy pages read deeper,
 * bounded by the cap.  Multi-byte encodings (UTF-16/32) register no ASCII tags
 * so they stop at {@code contentTarget} raw bytes.
 */
public final class AdaptiveProbe {

    /** Default body-content target. */
    public static final int DEFAULT_CONTENT_TARGET = 16384;
    /** Default hard ceiling on raw bytes read. */
    public static final int DEFAULT_RAW_CAP = 524288;

    private AdaptiveProbe() {
    }

    /**
     * Reads from {@code tis} (mark/reset preserved) until tag-stripped content
     * reaches {@code contentTarget}, the raw read reaches {@code rawCap}, or
     * EOF — whichever first.  Returns the raw bytes read.
     */
    public static byte[] read(TikaInputStream tis, int contentTarget, int rawCap)
            throws IOException {
        tis.mark(rawCap);
        try {
            byte[] buf = new byte[rawCap];
            byte[] stripDst = new byte[rawCap];
            int total = 0;
            while (total < rawCap) {
                int want = Math.min(rawCap - total, contentTarget);
                int n = IOUtils.read(tis, buf, total, want);
                total += n;
                HtmlByteStripper.Result r =
                        HtmlByteStripper.stripTags(buf, 0, total, stripDst, 0);
                int content = r.tagCount > 0 ? r.length : total;
                if (content >= contentTarget || n < want) {
                    break; // enough body text, or EOF
                }
            }
            if (total == 0) {
                return new byte[0];
            }
            return total == rawCap ? buf : Arrays.copyOf(buf, total);
        } finally {
            tis.reset();
        }
    }
}
