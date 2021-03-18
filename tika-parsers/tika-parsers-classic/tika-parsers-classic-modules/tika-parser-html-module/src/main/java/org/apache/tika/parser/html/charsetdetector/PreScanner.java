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
package org.apache.tika.parser.html.charsetdetector;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.AbstractMap;
import java.util.BitSet;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A scanner meant to detect charset meta tags in a byte stream
 * See: https://html.spec.whatwg.org/multipage/parsing.html#prescan-a-byte-stream-to-determine-its-encoding
 */
class PreScanner {

    private static final Pattern CHARSET_PATTERN =
            Pattern.compile("charset\\s*=\\s*([\"']?)([^\"'\\s;]+)\\1");
    private static final byte[] COMMENT_START = {(byte) '<', (byte) '!', (byte) '-', (byte) '-'};
    private static final byte[] COMMENT_END = {(byte) '-', (byte) '-', (byte) '>'};
    private static final byte[] META_TAG_START =
            {(byte) '<', (byte) 'm', (byte) 'e', (byte) 't', (byte) 'a'};
    private static final byte SLASH = (byte) '/';
    private static final byte EQUAL = (byte) '=';
    private static final byte TAG_START = (byte) '<';
    private static final byte TAG_END = (byte) '>';
    private static final BitSet QUOTE = bitSet('"', '\'');

    private static final BitSet WHITESPACE = bitSet(0x09, 0x0A, 0x0C, 0x0D, 0x0D, 0x20);
    private static final BitSet SPACE_OR_TAG_END = bitSet(WHITESPACE, TAG_END);
    private static final BitSet SPACE_OR_SLASH = bitSet(WHITESPACE, SLASH);
    private static final BitSet SPECIAL_TAGS = bitSet('!', '/', '?');

    private static final byte[] UTF8_BOM = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};
    private static final byte[] UTF16_BE_BOM = {(byte) 0xFE, (byte) 0xFF};
    private static final byte[] UTF16_LE_BOM = {(byte) 0xFF, (byte) 0xFE};
    private static final byte LOWER_A = (byte) 'a';
    private static final byte LOWER_Z = (byte) 'z';
    private static final byte UPPER_A = (byte) 'A';
    private static final byte UPPER_Z = (byte) 'Z';
    private BufferedInputStream stream;
    private CharsetDetectionResult detectedCharset = CharsetDetectionResult.notFound();

    PreScanner(InputStream inputStream) {
        this.stream = new BufferedInputStream(inputStream);
    }

    private static BitSet bitSet(int... bs) {
        BitSet bitSet = new BitSet(0xFF);
        for (int b : bs) bitSet.set(b);
        return bitSet;
    }

    private static BitSet bitSet(BitSet base, int... bs) {
        BitSet bitSet = (BitSet) base.clone();
        for (int b : bs) bitSet.set(b);
        return bitSet;
    }

    static String getEncodingFromMeta(String attributeValue) {
        Matcher matcher = CHARSET_PATTERN.matcher(attributeValue);
        if (!matcher.find()) {
            return null;
        }
        return matcher.group(2);
    }

    private static boolean contains(BitSet bitSet, byte b) {
        return bitSet.get(b & 0xFF);
    }

    Charset scan() {
        while (processAtLeastOneByte()) {
            if (detectedCharset.isFound()) {
                return detectedCharset.getCharset();
            }
        }
        return null;
    }

    Charset detectBOM() {
        try {
            if (expect(UTF8_BOM)) {
                return StandardCharsets.UTF_8;
            } else if (expect(UTF16_BE_BOM)) {
                return StandardCharsets.UTF_16BE;
            } else if (expect(UTF16_LE_BOM)) {
                return StandardCharsets.UTF_16LE;
            }
        } catch (IOException e) { /* stream could not be read, also return null */ }
        return null;
    }

    private boolean processAtLeastOneByte() {
        try {
            return processComment() || processMeta() || processTag() || processSpecialTag() ||
                    processAny();
        } catch (IOException e) {
            return false;
        }
    }

    private boolean processAny() throws IOException {
        int read = stream.read();
        return read != -1;
    }

    private boolean processTag() throws IOException {
        stream.mark(3);
        if (read() == TAG_START) {
            int read = stream.read();
            if (read == SLASH) {
                read = stream.read();
            }
            if ((LOWER_A <= read && read <= LOWER_Z) || (UPPER_A <= read && read <= UPPER_Z)) {
                do {
                    stream.mark(1);
                } while (!contains(SPACE_OR_TAG_END, read()));
                stream.reset();
                while (getAttribute() != null) {/* ignore the attribute*/}
                return true;
            }
        }
        stream.reset();
        return false;
    }

    private boolean processSpecialTag() throws IOException {
        stream.mark(2);
        if (read() == TAG_START && contains(SPECIAL_TAGS, read())) {
            skipUntil(TAG_END);
            return true;
        }
        stream.reset();
        return false;
    }

    private boolean processMeta() throws IOException {
        stream.mark(6); // len("<meta ") == 6
        if (readCaseInsensitive(META_TAG_START) && contains(SPACE_OR_SLASH, read())) {
            MetaProcessor metaProcessor = new MetaProcessor();
            for (Map.Entry<String, String> attribute = getAttribute(); attribute != null;
                    attribute = getAttribute()) {
                metaProcessor.processAttribute(attribute);
            }
            metaProcessor.updateDetectedCharset(detectedCharset);
            return true;
        }
        stream.reset();
        return false;
    }

    /**
     * Read an attribute from the stream
     *
     * @return the attribute as a Map.Entry, where the key is the attribute's name and
     * the value is the attribute's value. If there is no attribute, return null
     */
    private Map.Entry<String, String> getAttribute() throws IOException {
        String name = getAttributeName();
        if (name == null) {
            return null;
        }

        if (!expect(EQUAL)) {
            return new AbstractMap.SimpleEntry<>(name, "");
        }
        skipAll(WHITESPACE);

        String value = getAttributeValue();
        return new AbstractMap.SimpleEntry<>(name, value);
    }

    private String getAttributeName() throws IOException {
        skipAll(SPACE_OR_SLASH);
        if (expect(TAG_END)) {
            return null;
        }
        StringBuilder name = new StringBuilder();
        while (!(peek() == EQUAL && name.length() > 0) && !(peek() == TAG_END || peek() == SLASH) &&
                !skipAll(WHITESPACE)) {
            name.append((char) getLowerCaseChar());
        }
        return name.toString();
    }

    private String getAttributeValue() throws IOException {
        StringBuilder value = new StringBuilder();
        stream.mark(1);
        byte quote = read();
        if (contains(QUOTE, quote)) {
            for (byte b = getLowerCaseChar(); b != quote; b = getLowerCaseChar()) {
                value.append((char) b);
            }
        } else {
            stream.reset();
            for (byte b = getLowerCaseChar(); !contains(SPACE_OR_TAG_END, b);
                    b = getLowerCaseChar()) {
                value.append((char) b);
                stream.mark(1);
            }
            stream.reset(); // unread the space or tag end
        }
        return value.toString();
    }

    private boolean skipAll(BitSet bitSet) throws IOException {
        boolean skipped = false;
        stream.mark(1);
        for (byte read = read(); contains(bitSet, read); read = read()) {
            skipped = true;
            stream.mark(1);
        }
        stream.reset();
        return skipped;
    }

    private byte getLowerCaseChar() throws IOException {
        byte nextPoint = read();
        if (nextPoint >= 'A' && nextPoint <= 'Z') {
            nextPoint += 0x20; // lowercase
        }
        return nextPoint;
    }

    private boolean processComment() throws IOException {
        if (!expect(COMMENT_START)) {
            return false;
        }
        if (!expect(TAG_END)) {
            skipUntil(COMMENT_END);
        }
        return true;
    }

    private boolean expect(byte... expected) throws IOException {
        stream.mark(expected.length);
        for (byte b : expected) {
            byte read = read();
            if (read != b) {
                stream.reset();
                return false;
            }
        }
        return true;
    }

    private void skipUntil(byte... expected) throws IOException {
        while (!expect(expected)) {
            if (stream.read() == -1) {
                return;
            }
        }
    }

    private boolean readCaseInsensitive(byte... bs) throws IOException {
        for (byte b : bs)
            if (getLowerCaseChar() != b) {
                return false;
            }
        return true;
    }

    private byte read() throws IOException {
        int r = stream.read();
        if (r == -1) {
            throw new IOException();
        }
        return (byte) r;
    }

    private byte peek() throws IOException {
        stream.mark(1);
        byte b = read();
        stream.reset();
        return b;
    }
}
