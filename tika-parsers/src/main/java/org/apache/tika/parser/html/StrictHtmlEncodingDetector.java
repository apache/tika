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

import org.apache.tika.detect.EncodingDetector;
import org.apache.tika.metadata.Metadata;

import java.io.*;
import java.nio.charset.Charset;
import java.nio.charset.IllegalCharsetNameException;
import java.nio.charset.StandardCharsets;
import java.nio.charset.UnsupportedCharsetException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static java.nio.charset.StandardCharsets.*;
import static org.apache.tika.parser.html.StrictHtmlEncodingDetector.SequenceMatcher.caseInsensitive;
import static org.apache.tika.parser.html.StrictHtmlEncodingDetector.SingleByteMatcher.matchers;

/**
 * This is a strict html encoding detector that enforces the standard
 * far more strictly than the HtmlEncodingDetector.
 */
public class StrictHtmlEncodingDetector implements EncodingDetector {
    private static final String CHARSET_LABEL_FILE = "whatwg-encoding-labels.tsv";
    private static Map<String, Charset> CHARSET_LABELS = getCharsetLabels();

    private static Map<String, Charset> getCharsetLabels() {
        String path = StrictHtmlEncodingDetector.class.getPackage().getName().replace('.', '/');
        String filename = '/' + path + '/' + CHARSET_LABEL_FILE;
        InputStream inputStream = StrictHtmlEncodingDetector.class.getResourceAsStream(filename);
        Objects.requireNonNull(inputStream, "Missing charset label mapping file : " + filename);
        try (BufferedReader buffer = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.US_ASCII))) {
            return buffer.lines()
                    .filter(s -> !s.startsWith("#"))
                    .map(s -> s.split("\t"))
                    .filter(parts -> parts.length >= 2)
                    .collect(Collectors.toMap(
                            parts -> parts[0],
                            StrictHtmlEncodingDetector::charsetFromStandard
                    ));
        } catch (IOException e) {
            throw new UncheckedIOException("Unable to read the charset label mapping", e);
        }
    }

    private static Charset charsetFromStandard(String[] names) {
        for (int i = 1; i < names.length; i++) {
            try {
                return Charset.forName(names[1]);
            } catch (IllegalCharsetNameException | UnsupportedCharsetException e) {/* pass */}
        }
        // The only single-byte charset extended charset that must be present on every Java platform
        return StandardCharsets.ISO_8859_1;
    }

    private static Charset getCharsetByLabel(String label) {
        if (label == null) return null;
        label = label.trim().toLowerCase(Locale.US);
        return CHARSET_LABELS.get(label);
    }

    @Override
    public Charset detect(InputStream input, Metadata metadata) throws IOException {
        PreScanner preScanner = new PreScanner(input);

        // If there is a BOM at the beginning, the detection does not go further
        Charset bomCharset = preScanner.detectBOM();
        if (bomCharset != null) return bomCharset;

        // Assume that if there was a charset specified either by the end user or the transport level,
        // it was stored in the metadata
        String incomingCharsetName = metadata.get(Metadata.CONTENT_ENCODING);
        if (incomingCharsetName != null) {
            Charset incomingCharset = getCharsetByLabel(incomingCharsetName);
            if (incomingCharset != null) return incomingCharset;
        }

        return preScanner.scan();
    }

    static class PreScanner {

        private static final Pattern META_CHARSET_PATTERN = Pattern.compile("charset\\s*=\\s*([\"']?)([^\"'\\s;]+)\\1");
        private static ByteMatcher COMMENT_START = new SequenceMatcher("<!--");
        private static ByteMatcher COMMENT_END = new SequenceMatcher("-->");
        private static ByteMatcher LETTER = new OrMatcher(
                new RangeMatcher((byte) 'a', (byte) 'z'),
                new RangeMatcher((byte) 'A', (byte) 'Z')
        );
        private static ByteMatcher SPACE = new OrMatcher(matchers(0x09, 0x0A, 0x0C, 0x0D, 0x20));
        private static ByteMatcher SLASH = new SingleByteMatcher((byte) '/');
        private static ByteMatcher EQUAL = new SingleByteMatcher((byte) '=');
        private static ByteMatcher TAG_END = new SingleByteMatcher((byte) '>');
        private static ByteMatcher SINGLE_QUOTE = new SingleByteMatcher((byte) '\'');
        private static ByteMatcher DOUBLE_QUOTE = new SingleByteMatcher((byte) '"');
        private static ByteMatcher QUOTE = new OrMatcher(SINGLE_QUOTE, DOUBLE_QUOTE);
        private static ByteMatcher TAG_END_OR_SLASH = new OrMatcher(SLASH, TAG_END);
        private static ByteMatcher SPACE_OR_SLASH = new OrMatcher(SPACE, SLASH);
        private static ByteMatcher SPACE_OR_TAG_END = new OrMatcher(SPACE, TAG_END);
        private static ByteMatcher META_START = new SequenceMatcher(caseInsensitive("<meta"), SPACE_OR_SLASH);
        private static ByteMatcher TAG_START = new SequenceMatcher(
                new SingleByteMatcher((byte) '<'),
                new OrMatcher(SLASH, LETTER)
        );
        private static ByteMatcher TAG_BODY = new NegativeMatcher(new OrMatcher(SPACE, TAG_END));
        private static ByteMatcher SPECIAL_TAG_START = new SequenceMatcher(
                new SingleByteMatcher((byte) '<'),
                new OrMatcher(matchers("!/?"))
        );
        private static ByteMatcher UTF8_BOM = new SequenceMatcher(matchers(0xEF, 0xBB, 0xBF));
        private static ByteMatcher UTF16_BE_BOM = new SequenceMatcher(matchers(0xFE, 0xFF));
        private static ByteMatcher UTF16_LE_BOM = new SequenceMatcher(matchers(0xFF, 0xFE));


        PushbackInputStream stream;
        private CharsetDetectionResult detectedCharset = new CharsetDetectionResult();

        public PreScanner(InputStream inputStream) {
            this.stream = new PushbackInputStream(inputStream, 32);
        }

        public Charset scan() {
            while (processAtLeastOneByte()) {
                if (detectedCharset.isFound()) {
                    return detectedCharset.getCharset();
                }
            }
            return null;
        }

        private Charset detectBOM() {
            try {
                if (UTF8_BOM.matches(stream)) return StandardCharsets.UTF_8;
                else if (UTF16_BE_BOM.matches(stream)) return StandardCharsets.UTF_16BE;
                else if (UTF16_LE_BOM.matches(stream)) return StandardCharsets.UTF_16LE;
            } catch (IOException e) { /* stream could not be read, also return null */ }
            return null;
        }

        private boolean processAtLeastOneByte() {
            try {
                return processComment() ||
                        processMeta() ||
                        processTag() ||
                        processSpecialTag() ||
                        processAny();
            } catch (IOException e) {
                return false;
            }
        }

        private boolean processAny() throws IOException {
            int read = stream.read();
            return read != -1;
        }

        private boolean hasBytes() throws IOException {
            int read = stream.read();
            if (read != -1) stream.unread(read);
            return read != -1;
        }

        private boolean processComment() throws IOException {
            if (COMMENT_START.matches(stream)) {
                // The two '-' in the '-->' sequence can be the same as those in the '<!--' sequence.
                stream.unread("--".getBytes(StandardCharsets.US_ASCII));
                return COMMENT_END.advanceUntilMatches(stream);
            }
            return false;
        }

        private boolean processTag() throws IOException {
            if (TAG_START.matches(stream)) {
                TAG_BODY.skipAll(stream);
                while (getAttribute() != null) {/*ignore the attribute*/}
                return true;
            }
            return false;
        }

        private boolean processSpecialTag() throws IOException {
            if (SPECIAL_TAG_START.matches(stream)) {
                TAG_BODY.skipAll(stream);
                return TAG_END.advanceUntilMatches(stream);
            }
            return false;
        }

        private boolean processMeta() throws IOException {
            if (META_START.matches(stream)) {
                Set<String> attributeNames = new HashSet<>();
                boolean gotPragma = false;
                Boolean needPragma = null;
                CharsetDetectionResult charset = new CharsetDetectionResult();
                while (hasBytes()) {
                    Attribute attribute = getAttribute();
                    if (attribute == null) break;
                    if (attributeNames.contains(attribute.getName())) continue;
                    attributeNames.add(attribute.getName());
                    switch (attribute.getName()) {
                        case "http-equiv":
                            if (attribute.getValue().equals("content-type"))
                                gotPragma = true;
                            break;
                        case "content":
                            String charsetName = getEncodingFromMeta(attribute.getValue());
                            if (!charset.isFound() && charsetName != null) {
                                charset.find(charsetName);
                                needPragma = true;
                            }
                            break;
                        case "charset":
                            charset.find(attribute.getValue());
                            needPragma = false;
                            break;
                        default: // Ignore non-charset related attributes
                    }
                }
                if (needPragma != null && !(needPragma && !gotPragma)) {
                    detectedCharset = charset;
                    return true;
                }
            }
            return false;
        }

        private String getEncodingFromMeta(String attributeValue) {
            Matcher matcher = META_CHARSET_PATTERN.matcher(attributeValue);
            if (!matcher.find()) return null;
            return matcher.group(2);
        }

        private Attribute getAttribute() throws IOException {
            SPACE_OR_SLASH.skipAll(stream);
            if (TAG_END.peekMatches(stream)) return null;
            StringBuilder name = new StringBuilder();
            while (!EQUAL.peekMatches(stream) || name.length() == 0) {
                if (TAG_END_OR_SLASH.peekMatches(stream)) {
                    break;
                } else if (SPACE.peekMatches(stream)) {
                    SPACE.skipAll(stream);
                    break;
                } else {
                    name.append(getLowerCaseChar());
                }
            }

            if (!EQUAL.matches(stream)) return new Attribute(name.toString(), "");
            SPACE.skipAll(stream);

            StringBuilder value = new StringBuilder();
            byte[] quoteMatched = QUOTE.match(stream);
            if (quoteMatched != null) {
                char quote = (char) quoteMatched[0];
                int nextChar = -1;
                while (nextChar != quote) {
                    if (nextChar != -1) value.append((char) nextChar);
                    nextChar = getLowerCaseChar();
                }
            } else {
                while (!SPACE_OR_TAG_END.peekMatches(stream)) {
                    value.append(getLowerCaseChar());
                }
            }
            return new Attribute(name.toString(), value.toString());
        }

        private char getLowerCaseChar() throws IOException {
            int nextPoint = stream.read();
            if (nextPoint == -1) throw new IOException();
            if (nextPoint >= 'A' && nextPoint <= 'Z') nextPoint += 0x20; // lowercase
            return (char) nextPoint;
        }
    }

    static class Attribute {
        String name;
        String value;

        public Attribute(String name, String value) {
            this.name = name;
            this.value = value;
        }

        public String getName() {
            return name;
        }

        public String getValue() {
            return value;
        }
    }

    /**
     * A detection may either not find a charset, find an invalid charset, or find a valid charset
     */
    static class CharsetDetectionResult {
        private boolean found = false;
        private Charset charset = null;

        public CharsetDetectionResult() { /* default result: not found */}

        public boolean isFound() {
            return found;
        }

        public void find(String charsetName) {
            this.found = true;
            charsetName = charsetName.trim();
            if ("x-user-defined".equals(charsetName)) charsetName = "windows-1252";
            this.charset = getCharsetByLabel(charsetName);
            // The specification states: If charset is a UTF-16 encoding, then set charset to UTF-8.
            if (UTF_16LE.equals(charset) || UTF_16BE.equals(charset)) charset = UTF_8;
        }

        public Charset getCharset() {
            // the result may be null even if found is true, in the case there is a charset specified,
            // but it is invalid
            return charset;
        }
    }

    static abstract class ByteMatcher {

        abstract byte[] match(PushbackInputStream pushbackInputStream) throws IOException;

        boolean matches(PushbackInputStream pushbackInputStream) throws IOException {
            return this.match(pushbackInputStream) != null;
        }

        boolean advanceUntilMatches(PushbackInputStream pushbackInputStream) throws IOException {
            while (!this.matches(pushbackInputStream)) {
                int nextByte = pushbackInputStream.read();
                if (nextByte == -1) return false;
            }
            return true;
        }

        void skipAll(PushbackInputStream pushbackInputStream) throws IOException {
            while (matches(pushbackInputStream)) {/* just skip the byte */}
        }

        public boolean peekMatches(PushbackInputStream pushbackInputStream) throws IOException {
            byte[] matched = this.match(pushbackInputStream);
            if (matched != null) pushbackInputStream.unread(matched);
            return matched != null;
        }
    }

    static class SingleByteMatcher extends ByteMatcher {
        private byte b;

        public SingleByteMatcher(byte b) {
            this.b = b;
        }

        public static ByteMatcher[] matchers(String s) {
            return matchers(s.chars());
        }

        public static ByteMatcher[] matchers(int... bytes) {
            return matchers(IntStream.of(bytes));
        }

        public static ByteMatcher[] matchers(IntStream byteStream) {
            return byteStream
                    .mapToObj(i -> new SingleByteMatcher((byte) i))
                    .toArray(ByteMatcher[]::new);
        }

        @Override
        byte[] match(PushbackInputStream pushbackInputStream) throws IOException {
            int read = pushbackInputStream.read();
            if ((byte) read == b) return new byte[]{b};
            if (read != -1) pushbackInputStream.unread(read);
            return null;
        }
    }

    static class SequenceMatcher extends ByteMatcher {
        private ByteMatcher[] matchers;

        public SequenceMatcher(ByteMatcher... matchers) {
            this.matchers = matchers;
        }

        public SequenceMatcher(String s) {
            this(matchers(s));
        }

        public static SequenceMatcher caseInsensitive(String s) {
            ByteMatcher[] lowerMatchers = matchers(s.toLowerCase(Locale.US));
            ByteMatcher[] upperMatchers = matchers(s.toUpperCase(Locale.US));
            OrMatcher[] matchers = IntStream
                    .range(0, Math.min(lowerMatchers.length, upperMatchers.length))
                    .mapToObj(i -> new OrMatcher(lowerMatchers[i], upperMatchers[i]))
                    .toArray(OrMatcher[]::new);
            return new SequenceMatcher(matchers);
        }

        @Override
        byte[] match(PushbackInputStream pushbackInputStream) throws IOException {
            ByteArrayOutputStream allMatched = new ByteArrayOutputStream();
            for (ByteMatcher m : matchers) {
                byte[] matched = m.match(pushbackInputStream);
                if (matched == null) {
                    pushbackInputStream.unread(allMatched.toByteArray());
                    return null;
                } else {
                    allMatched.write(matched);
                }
            }
            return allMatched.toByteArray();
        }
    }

    static class OrMatcher extends ByteMatcher {
        private ByteMatcher[] matchers;

        public OrMatcher(ByteMatcher... matchers) {
            this.matchers = matchers;
        }

        @Override
        byte[] match(PushbackInputStream pushbackInputStream) throws IOException {
            for (ByteMatcher m : matchers) {
                byte[] matched = m.match(pushbackInputStream);
                if (matched != null) return matched;
            }
            return null;
        }
    }

    static class NegativeMatcher extends ByteMatcher {
        private ByteMatcher matcher;

        public NegativeMatcher(ByteMatcher matcher) {
            this.matcher = matcher;
        }

        @Override
        byte[] match(PushbackInputStream pushbackInputStream) throws IOException {
            byte[] matched = matcher.match(pushbackInputStream);
            if (matched == null) {
                int read = pushbackInputStream.read();
                if (read == -1) return null;
                return new byte[]{(byte) read};
            } else {
                pushbackInputStream.unread(matched);
                return null;
            }
        }
    }

    static class RangeMatcher extends ByteMatcher {
        private byte low;
        private byte high;

        public RangeMatcher(byte low, byte high) {
            this.low = low;
            this.high = high;
        }


        @Override
        byte[] match(PushbackInputStream pushbackInputStream) throws IOException {
            int read = pushbackInputStream.read();
            if (read >= low && read <= high) return new byte[]{(byte) read};
            if (read != -1) pushbackInputStream.unread(read);
            return null;
        }
    }
}
