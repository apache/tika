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
package org.apache.tika.detect;

import java.nio.charset.Charset;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Maps detected charsets to safer superset charsets for decoding.
 *
 * <p>When Tika detects a charset that is a strict subset of a broader encoding,
 * it is safer to decode with the superset — the superset handles all byte
 * sequences the subset can produce, plus the extension characters the subset
 * cannot represent. Decoding with only the subset risks mojibake on any
 * extension characters present in the document.</p>
 *
 * <p>Policy: Content-Type and detected-encoding metadata report the <em>detected</em>
 * charset. Actual stream decoding uses the superset. The superset used is recorded
 * in {@link org.apache.tika.metadata.TikaCoreProperties#DECODED_CHARSET}.</p>
 *
 * <h3>Superset map</h3>
 * <ul>
 *   <li>EUC-KR → x-windows-949 (MS949 is a strict superset: all EUC-KR byte sequences
 *       decode identically, extension chars in x-windows-949 would mojibake under EUC-KR)</li>
 *   <li>Big5 → Big5-HKSCS (HKSCS adds Hong Kong Supplementary Characters)</li>
 *   <li>GB2312 → GB18030 (GB18030 is a strict superset of both GB2312 and GBK)</li>
 *   <li>GBK → GB18030 (GB18030 is a strict superset; enables 4-byte extension sequences)</li>
 *   <li>Shift_JIS → windows-31j (MS932 is a strict superset with NEC/IBM extensions)</li>
 * </ul>
 */
public final class CharsetSupersets {

    /**
     * Maps detected charset canonical names (case-sensitive, as returned by
     * {@link Charset#name()}) to their superset charset canonical name.
     */
    public static final Map<String, String> SUPERSET_MAP;

    static {
        Map<String, String> m = new HashMap<>();
        m.put("EUC-KR",    "x-windows-949");
        m.put("Big5",      "Big5-HKSCS");
        m.put("GB2312",    "GB18030");
        m.put("GBK",       "GB18030");
        m.put("Shift_JIS", "windows-31j");
        SUPERSET_MAP = Collections.unmodifiableMap(m);
    }

    private CharsetSupersets() {
    }

    /**
     * Returns the superset charset to use for decoding, or {@code null} if
     * {@code detected} has no superset override.
     *
     * @param detected the charset returned by the encoding detector
     * @return superset charset, or {@code null} if none is defined
     */
    public static Charset supersetOf(Charset detected) {
        if (detected == null) {
            return null;
        }
        String supersetName = SUPERSET_MAP.get(detected.name());
        if (supersetName == null) {
            return null;
        }
        try {
            return Charset.forName(supersetName);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
