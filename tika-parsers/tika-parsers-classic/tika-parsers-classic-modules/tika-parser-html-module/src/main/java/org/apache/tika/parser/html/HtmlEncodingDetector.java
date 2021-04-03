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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.tika.config.Field;
import org.apache.tika.detect.EncodingDetector;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.utils.CharsetUtils;

/**
 * Character encoding detector for determining the character encoding of a
 * HTML document based on the potential charset parameter found in a
 * Content-Type http-equiv meta tag somewhere near the beginning. Especially
 * useful for determining the type among multiple closely related encodings
 * (ISO-8859-*) for which other types of encoding detection are unreliable.
 *
 * @since Apache Tika 1.2
 */
public class HtmlEncodingDetector implements EncodingDetector {

    // TIKA-357 - use bigger buffer for meta tag sniffing (was 4K)
    private static final int DEFAULT_MARK_LIMIT = 8192;
    private static final Pattern HTTP_META_PATTERN =
            Pattern.compile("(?is)<\\s*meta(?:/|\\s+)([^<>]+)");
    //this should match both the older:
    //<meta http-equiv="content-type" content="text/html; charset=xyz"/>
    //and
    //html5 <meta charset="xyz">
    //See http://webdesign.about.com/od/metatags/qt/meta-charset.htm
    //for the noisiness that one might encounter in charset attrs.
    //Chose to go with strict ([-_:\\.a-z0-9]+) to match encodings
    //following http://docs.oracle.com/javase/7/docs/api/java/nio/charset/Charset.html
    //For a more general "not" matcher, try:
    //("(?is)charset\\s*=\\s*['\\\"]?\\s*([^<>\\s'\\\";]+)")
    private static final Pattern FLEXIBLE_CHARSET_ATTR_PATTERN =
            Pattern.compile(("(?is)\\bcharset\\s*=\\s*(?:['\\\"]\\s*)?([-_:\\.a-z0-9]+)"));
    private static final Charset ASCII = Charset.forName("US-ASCII");
    /**
     * HTML can include non-iana supported charsets that Java
     * recognizes, e.g. "unicode".  This can lead to incorrect detection/mojibake.
     * Ignore charsets in html meta-headers that are not supported by IANA.
     * See: TIKA-2592
     */
    private static Set<String> CHARSETS_UNSUPPORTED_BY_IANA;

    static {
        Set<String> unsupported = new HashSet<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                HtmlEncodingDetector.class
                        .getResourceAsStream("StandardCharsets_unsupported_by_IANA.txt"),
                StandardCharsets.UTF_8))) {
            String line = reader.readLine();
            while (line != null) {
                if (line.startsWith("#")) {
                    line = reader.readLine();
                    continue;
                }
                line = line.trim();
                if (line.length() > 0) {
                    unsupported.add(line.toLowerCase(Locale.US));
                }
                line = reader.readLine();
            }
        } catch (IOException e) {
            throw new IllegalArgumentException(
                    "couldn't find StandardCharsets_unsupported_by_IANA.txt on the class path");
        }
        CHARSETS_UNSUPPORTED_BY_IANA = Collections.unmodifiableSet(unsupported);
    }

    @Field
    private int markLimit = DEFAULT_MARK_LIMIT;

    public Charset detect(InputStream input, Metadata metadata) throws IOException {
        if (input == null) {
            return null;
        }

        // Read enough of the text stream to capture possible meta tags
        input.mark(markLimit);
        byte[] buffer = new byte[markLimit];
        int n = 0;
        int m = input.read(buffer);
        while (m != -1 && n < buffer.length) {
            n += m;
            m = input.read(buffer, n, buffer.length - n);
        }
        input.reset();

        // Interpret the head as ASCII and try to spot a meta tag with
        // a possible character encoding hint

        String head = ASCII.decode(ByteBuffer.wrap(buffer, 0, n)).toString();
        //strip out comments
        String headNoComments = head.replaceAll("<!--.*?(-->|$)", " ");
        //try to find the encoding in head without comments
        Charset charset = findCharset(headNoComments);
        //if nothing is found, back off to find any encoding
        if (charset == null) {
            return findCharset(head);
        }
        return charset;

    }

    //returns null if no charset was found
    private Charset findCharset(String s) {

        Matcher equiv = HTTP_META_PATTERN.matcher(s);
        Matcher charsetMatcher = FLEXIBLE_CHARSET_ATTR_PATTERN.matcher("");
        //iterate through meta tags
        while (equiv.find()) {
            String attrs = equiv.group(1);
            charsetMatcher.reset(attrs);
            //iterate through charset= and return the first match
            //that is valid
            while (charsetMatcher.find()) {
                String candCharset = charsetMatcher.group(1);
                if (CHARSETS_UNSUPPORTED_BY_IANA.contains(candCharset.toLowerCase(Locale.US))) {
                    continue;
                }
                if ("x-user-defined".equalsIgnoreCase(candCharset)) {
                    candCharset = "windows-1252";
                }

                if (CharsetUtils.isSupported(candCharset)) {
                    try {
                        return CharsetUtils.forName(candCharset);
                    } catch (IllegalArgumentException e) {
                        //ignore
                    }
                }
            }
        }
        return null;
    }

    public int getMarkLimit() {
        return markLimit;
    }

    /**
     * How far into the stream to read for charset detection.
     * Default is 8192.
     *
     * @param markLimit
     */
    @Field
    public void setMarkLimit(int markLimit) {
        this.markLimit = markLimit;
    }
}
