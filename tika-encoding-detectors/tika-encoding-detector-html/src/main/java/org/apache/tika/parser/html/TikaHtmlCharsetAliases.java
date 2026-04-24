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
package org.apache.tika.parser.html;

import java.nio.charset.Charset;
import java.nio.charset.IllegalCharsetNameException;
import java.nio.charset.UnsupportedCharsetException;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Curated subset of the WHATWG Encoding Standard label table
 * (https://encoding.spec.whatwg.org/) for use by {@link HtmlEncodingDetector}.
 *
 * <p>The WHATWG table is designed for <em>web browsers</em> where lenient
 * decoding with fallbacks is preferable to failing or producing mojibake.
 * For a generic content-extraction library the same policy can be
 * data-destructive, so this class intentionally departs from the spec in
 * three places:
 *
 * <ol>
 *   <li><strong>No replacement charset for ISO-2022-KR / ISO-2022-CN /
 *       HZ-GB-2312.</strong>  WHATWG maps these to a dummy "replacement"
 *       decoder that emits {@code U+FFFD} for every byte.  For security in a
 *       browser this is fine; for Tika it would throw away legitimate text
 *       in those encodings, so we simply leave the labels unmapped and let
 *       the downstream detector chain (Mojibuster's structural rules, etc.)
 *       handle them.</li>
 *   <li><strong>No ISO-8859-14 / 16 / 10 downgrades.</strong>  WHATWG
 *       collapses these into ISO-8859-1 / ISO-8859-4 because no major
 *       browser implements them.  Java's JDK does, so we let the label
 *       resolve to the native charset via {@link Charset#forName}.</li>
 *   <li><strong>{@code windows-949} / {@code MS949} / {@code CP949} →
 *       {@code x-windows-949} (not {@code EUC-KR}).</strong>  Unified Hangul
 *       Code is a strict superset of EUC-KR — resolving these labels to
 *       EUC-KR emits {@code U+FFFD} on extension bytes that MS949 decodes
 *       correctly.</li>
 * </ol>
 *
 * <p>All other WHATWG labels we recognise — including browser-friendly
 * aliases like {@code iso-8859-1} → {@code windows-1252}, {@code iso-8859-9}
 * → {@code windows-1254}, {@code tis-620} → {@code windows-874}, and the
 * naked {@code utf-16} → {@code UTF-16LE} BOM-absent default — match the
 * spec exactly.
 */
final class TikaHtmlCharsetAliases {

    private static final Map<String, Charset> CHARSETS_BY_LABEL = buildTable();

    private TikaHtmlCharsetAliases() {
    }

    /**
     * @param label a charset label from an HTML {@code <meta charset>} or
     *              {@code Content-Type} attribute
     * @return the Java charset this label resolves to, or {@code null} if the
     *         label is not in the curated alias table (callers should then
     *         fall back to {@link Charset#forName} with a supported-by-IANA
     *         check)
     */
    static Charset resolve(String label) {
        if (label == null) {
            return null;
        }
        return CHARSETS_BY_LABEL.get(label.trim().toLowerCase(Locale.US));
    }

    private static Map<String, Charset> buildTable() {
        Map<String, Charset> m = new HashMap<>();
        add(m, charset("Big5"), "big5", "big5-hkscs", "cn-big5", "csbig5", "x-x-big5");
        add(m, charset("EUC-JP"), "cseucpkdfmtjapanese", "euc-jp", "x-euc-jp");
        add(m, charset("EUC-KR"), "cseuckr", "csksc56011987", "euc-kr", "iso-ir-149", "korean",
                "ks_c_5601-1987", "ks_c_5601-1989", "ksc5601", "ksc_5601");
        // windows-949 / MS949 / CP949 are supersets of EUC-KR; route to x-windows-949
        // to preserve MS949 extension syllables (see class javadoc).
        add(m, charset("x-windows-949"), "windows-949", "ms949", "cp949");
        add(m, charset("GBK"), "chinese", "csgb2312", "csiso58gb231280", "gb2312", "gb_2312",
                "gb_2312-80", "gbk", "iso-ir-58", "x-gbk");
        add(m, charset("IBM866"), "866", "cp866", "csibm866", "ibm866");
        add(m, charset("ISO-2022-JP"), "csiso2022jp", "iso-2022-jp");
        add(m, charset("ISO-8859-13"), "iso-8859-13", "iso8859-13", "iso885913");
        add(m, charset("ISO-8859-15"), "csisolatin9", "iso-8859-15", "iso8859-15", "iso885915",
                "iso_8859-15", "l9");
        add(m, charset("ISO-8859-2"), "csisolatin2", "iso-8859-2", "iso-ir-101", "iso8859-2",
                "iso88592", "iso_8859-2", "iso_8859-2:1987", "l2", "latin2");
        add(m, charset("ISO-8859-3"), "csisolatin3", "iso-8859-3", "iso-ir-109", "iso8859-3",
                "iso88593", "iso_8859-3", "iso_8859-3:1988", "l3", "latin3");
        add(m, charset("ISO-8859-4"), "csisolatin4", "iso-8859-4", "iso-ir-110", "iso8859-4",
                "iso88594", "iso_8859-4", "iso_8859-4:1988", "l4", "latin4");
        add(m, charset("ISO-8859-5"), "csisolatincyrillic", "cyrillic", "iso-8859-5",
                "iso-ir-144", "iso8859-5", "iso88595", "iso_8859-5", "iso_8859-5:1988");
        add(m, charset("ISO-8859-6"), "arabic", "asmo-708", "csiso88596e", "csiso88596i",
                "csisolatinarabic", "ecma-114", "iso-8859-6", "iso-8859-6-e", "iso-8859-6-i",
                "iso-ir-127", "iso8859-6", "iso88596", "iso_8859-6", "iso_8859-6:1987");
        add(m, charset("ISO-8859-7"), "csisolatingreek", "ecma-118", "elot_928", "greek",
                "greek8", "iso-8859-7", "iso-ir-126", "iso8859-7", "iso88597", "iso_8859-7",
                "iso_8859-7:1987", "sun_eu_greek");
        // ISO-8859-8 (visual order) and ISO-8859-8-I (logical order):
        // we do not implement directionality remapping, so both resolve to ISO-8859-8
        // where available.
        add(m, charset("ISO-8859-8"), "csiso88598e", "csisolatinhebrew", "hebrew", "iso-8859-8",
                "iso-8859-8-e", "iso-ir-138", "iso8859-8", "iso88598", "iso_8859-8",
                "iso_8859-8:1988", "visual");
        add(m, charset("ISO-8859-8-I", "ISO-8859-8"), "csiso88598i", "iso-8859-8-i", "logical");
        add(m, charset("KOI8-R"), "cskoi8r", "koi", "koi8", "koi8-r", "koi8_r");
        add(m, charset("KOI8-U"), "koi8-ru", "koi8-u");
        add(m, charset("Shift_JIS"), "csshiftjis", "ms932", "ms_kanji", "shift-jis",
                "shift_jis", "sjis", "windows-31j", "x-sjis");
        add(m, charset("UTF-16BE"), "utf-16be");
        // Naked "utf-16" with no BOM defaults to UTF-16LE per WHATWG.
        add(m, charset("UTF-16LE"), "utf-16", "utf-16le");
        add(m, charset("UTF-8"), "unicode-1-1-utf-8", "utf-8", "utf8");
        add(m, charset("gb18030"), "gb18030");
        add(m, charset("windows-1250"), "cp1250", "windows-1250", "x-cp1250");
        add(m, charset("windows-1251"), "cp1251", "windows-1251", "x-cp1251");
        add(m, charset("windows-1252"), "ansi_x3.4-1968", "ascii", "cp1252", "cp819",
                "csisolatin1", "ibm819", "iso-8859-1", "iso-ir-100", "iso8859-1", "iso88591",
                "iso_8859-1", "iso_8859-1:1987", "l1", "latin1", "us-ascii", "windows-1252",
                "x-cp1252");
        add(m, charset("windows-1253"), "cp1253", "windows-1253", "x-cp1253");
        add(m, charset("windows-1254"), "cp1254", "csisolatin5", "iso-8859-9", "iso-ir-148",
                "iso8859-9", "iso88599", "iso_8859-9", "iso_8859-9:1989", "l5", "latin5",
                "windows-1254", "x-cp1254");
        add(m, charset("windows-1255"), "cp1255", "windows-1255", "x-cp1255");
        add(m, charset("windows-1256"), "cp1256", "windows-1256", "x-cp1256");
        add(m, charset("windows-1257"), "cp1257", "windows-1257", "x-cp1257");
        add(m, charset("windows-1258"), "cp1258", "windows-1258", "x-cp1258");
        add(m, charset("windows-874"), "dos-874", "iso-8859-11", "iso8859-11", "iso885911",
                "tis-620", "windows-874");
        add(m, charset("x-MacCyrillic"), "x-mac-cyrillic", "x-mac-ukrainian");
        add(m, charset("x-MacRoman"), "csmacintosh", "mac", "macintosh", "x-mac-roman");
        // x-user-defined is a browser-only passthrough; resolve to windows-1252,
        // which mirrors HtmlEncodingDetector's pre-existing behaviour.
        add(m, charset("windows-1252"), "x-user-defined");
        return m;
    }

    private static Charset charset(String... names) {
        for (String name : names) {
            try {
                return Charset.forName(name);
            } catch (IllegalCharsetNameException | UnsupportedCharsetException e) {
                // try next alternative
            }
        }
        return null;
    }

    private static void add(Map<String, Charset> m, Charset cs, String... labels) {
        if (cs == null) {
            return;
        }
        for (String label : labels) {
            m.put(label, cs);
        }
    }
}
