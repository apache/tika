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
package org.apache.tika.ml.junkdetect;

import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.tika.ml.chardetect.HtmlByteStripper;

/**
 * Junk-detection HTML→text cleaning: strip tags, then <em>expand</em> entities
 * to codepoints.  Called by both {@code TrainJunkModel} and {@link
 * JunkFilterEncodingDetector} so training and inference prepare text identically
 * (no drift).
 *
 * <p>Unlike charset detection's {@link HtmlByteStripper#stripTagsAndEntities}
 * (which <em>drops</em> entities as charset-neutral ASCII noise), junk detection
 * expands them: the resulting codepoints — cross-script under a wrong decoding —
 * are what expose mojibake.
 */
public final class HtmlContentCleaner {

    private static final Pattern ENTITY = Pattern.compile(
            "&(?:#[xX]([0-9a-fA-F]+)|#(\\d+)|(amp|lt|gt|quot|apos|nbsp|copy|reg));");

    /** WHATWG remaps numeric references 0x80-0x9F to these windows-1252 characters; 0 = kept. */
    private static final char[] C1_REMAP = {
            '\u20AC', 0, '\u201A', '\u0192', '\u201E', '\u2026', '\u2020', '\u2021',
            '\u02C6', '\u2030', '\u0160', '\u2039', '\u0152', 0, '\u017D', 0,
            0, '\u2018', '\u2019', '\u201C', '\u201D', '\u2022', '\u2013', '\u2014',
            '\u02DC', '\u2122', '\u0161', '\u203A', '\u0153', 0, '\u017E', '\u0178'};

    private HtmlContentCleaner() {
    }

    /**
     * Strip HTML tags (entities preserved through the strip), then expand
     * entities to real codepoints.  No-op-ish on plain text (no tags/entities).
     */
    public static String clean(String s) {
        if (s == null || s.isEmpty()) {
            return s;
        }
        // Tag-strip via the byte stripper on the string's UTF-8 form; keeps
        // entities (stripTags, not stripTagsAndEntities) for the expand below.
        byte[] u = s.getBytes(StandardCharsets.UTF_8);
        byte[] dst = new byte[u.length];
        HtmlByteStripper.Result r = HtmlByteStripper.stripTags(u, 0, u.length, dst, 0);
        String tagless = (r.tagCount > 0 && r.length > 0)
                ? new String(dst, 0, r.length, StandardCharsets.UTF_8)
                : s;
        return expandHtmlEntities(tagless);
    }

    /**
     * Expand HTML entities to codepoints.  Numeric refs ({@code &#169;},
     * {@code &#xA9;}) are decoded as a browser does (WHATWG "numeric character
     * reference end state"); a small set of common named entities is mapped;
     * other named entities pass through literally.
     */
    static String expandHtmlEntities(String s) {
        // one pass, so an expansion is never re-read as a reference ("&#38;#x41;" stays "&#x41;")
        return ENTITY.matcher(s).replaceAll(mr -> {
            if (mr.group(1) != null) {
                return numericReference(mr.group(1), 16);
            }
            if (mr.group(2) != null) {
                return numericReference(mr.group(2), 10);
            }
            switch (mr.group(3)) {
                case "amp":  return "&";
                case "lt":   return "<";
                case "gt":   return ">";
                case "quot": return "\"";
                case "apos": return "'";
                case "nbsp": return " ";
                case "copy": return "©";
                default:     return "®";
            }
        });
    }

    /** NUL, surrogates and values past U+10FFFF become U+FFFD; 0x80-0x9F map via windows-1252. */
    private static String numericReference(String digits, int radix) {
        int start = 0;
        while (start < digits.length() - 1 && digits.charAt(start) == '0') {
            start++;
        }
        int cp = -1;
        if (digits.length() - start <= 7) {
            cp = Integer.parseInt(digits.substring(start), radix);
        }
        if (cp <= 0 || cp > 0x10FFFF || (cp >= 0xD800 && cp <= 0xDFFF)) {
            return "\uFFFD";
        }
        if (cp >= 0x80 && cp <= 0x9F && C1_REMAP[cp - 0x80] != 0) {
            return String.valueOf(C1_REMAP[cp - 0x80]);
        }
        return Matcher.quoteReplacement(new String(Character.toChars(cp)));
    }
}
