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

    private static final Pattern ENTITY_DEC = Pattern.compile("&#(\\d{1,7});");
    private static final Pattern ENTITY_HEX = Pattern.compile("&#[xX]([0-9a-fA-F]{1,6});");
    private static final Pattern ENTITY_NAMED =
            Pattern.compile("&(amp|lt|gt|quot|apos|nbsp|copy|reg);");

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
     * {@code &#xA9;}) are fully decoded; a small set of common named entities
     * is mapped; other named entities pass through literally.
     */
    static String expandHtmlEntities(String s) {
        s = ENTITY_DEC.matcher(s).replaceAll(mr -> {
            try {
                int cp = Integer.parseInt(mr.group(1));
                if (cp >= 0 && cp <= 0x10FFFF) {
                    return Matcher.quoteReplacement(new String(Character.toChars(cp)));
                }
            } catch (NumberFormatException ignored) {
                // overflow — leave literal
            }
            return Matcher.quoteReplacement(mr.group());
        });
        s = ENTITY_HEX.matcher(s).replaceAll(mr -> {
            try {
                int cp = Integer.parseInt(mr.group(1), 16);
                if (cp >= 0 && cp <= 0x10FFFF) {
                    return Matcher.quoteReplacement(new String(Character.toChars(cp)));
                }
            } catch (NumberFormatException ignored) {
                // overflow — leave literal
            }
            return Matcher.quoteReplacement(mr.group());
        });
        s = ENTITY_NAMED.matcher(s).replaceAll(mr -> {
            switch (mr.group(1)) {
                case "amp":  return "&";
                case "lt":   return "<";
                case "gt":   return ">";
                case "quot": return "\"";
                case "apos": return "'";
                case "nbsp": return " ";
                case "copy": return "©";
                case "reg":  return "®";
                default:     return Matcher.quoteReplacement(mr.group());
            }
        });
        return s;
    }
}
