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
package org.apache.tika.mime;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;

import org.apache.tika.detect.MagicDetector;
import org.apache.tika.metadata.Metadata;

/**
 * Defines a magic match.
 */
class MagicMatch implements Clause {

    private static final MediaType MATCH =
        new MediaType("x-tika", "magic-match");

    private final int length;

    private final MagicDetector detector;

    MagicMatch(int offsetStart, int offsetEnd, String type, String mask,
            String value) throws MimeTypeException {

        byte[] patternBytes = decodeValue(type, value);
        byte[] maskBytes;
        if (mask != null) {
            maskBytes = decodeValue(type, mask);
        } else {
            maskBytes = new byte[patternBytes.length];
            Arrays.fill(maskBytes, (byte) 0xff);
        }
        this.length = Math.max(patternBytes.length, maskBytes.length);

        if (patternBytes.length < length) {
            byte[] buffer = new byte[length];
            System.arraycopy(patternBytes, 0, buffer, 0, patternBytes.length);
            patternBytes = buffer;
        } else if (maskBytes.length < length) {
            byte[] buffer = new byte[length];
            Arrays.fill(buffer, (byte) 0xff);
            System.arraycopy(maskBytes, 0, buffer, 0, maskBytes.length);
            maskBytes = buffer;
        }

        for (int i = 0; i < length; i++) {
            patternBytes[i] &= maskBytes[i];
        }

        this.detector = new MagicDetector(
                MATCH, patternBytes, maskBytes, offsetStart, offsetEnd);
    }

    private byte[] decodeValue(String type, String value)
            throws MimeTypeException {
        // Preliminary check
        if ((value == null) || (type == null)) {
            return null;
        }

        byte[] decoded = null;
        String tmpVal = null;
        int radix = 8;

        // hex
        if (value.startsWith("0x")) {
            tmpVal = value.substring(2);
            radix = 16;
        } else {
            tmpVal = value;
            radix = 8;
        }

        if (type.equals("string")) {
            decoded = decodeString(value);

        } else if (type.equals("byte")) {
            decoded = tmpVal.getBytes();

        } else if (type.equals("host16") || type.equals("little16")) {
            int i = Integer.parseInt(tmpVal, radix);
            decoded = new byte[] { (byte) (i >> 8), (byte) (i & 0x00FF) };

        } else if (type.equals("big16")) {
            int i = Integer.parseInt(tmpVal, radix);
            decoded = new byte[] { (byte) (i >> 8), (byte) (i & 0x00FF) };

        } else if (type.equals("host32") || type.equals("little32")) {
            long i = Long.parseLong(tmpVal, radix);
            decoded = new byte[] { (byte) ((i & 0x000000FF)),
                    (byte) ((i & 0x0000FF00) >> 8),
                    (byte) ((i & 0x00FF0000) >> 16),
                    (byte) ((i & 0xFF000000) >> 24) };

        } else if (type.equals("big32")) {
            long i = Long.parseLong(tmpVal, radix);
            decoded = new byte[] { (byte) ((i & 0xFF000000) >> 24),
                    (byte) ((i & 0x00FF0000) >> 16),
                    (byte) ((i & 0x0000FF00) >> 8), (byte) ((i & 0x000000FF)) };
        }
        return decoded;
    }

    private byte[] decodeString(String value) throws MimeTypeException {
        if (value.startsWith("0x")) {
            byte[] bytes = new byte[(value.length() - 2) / 2];
            for (int i = 0; i < bytes.length; i++) {
                bytes[i] = (byte)
                    Integer.parseInt(value.substring(2 + i * 2, 4 + i * 2), 16);
            }
            return bytes;
        }

        try {
            ByteArrayOutputStream decoded = new ByteArrayOutputStream();

            for (int i = 0; i < value.length(); i++) {
                if (value.charAt(i) == '\\') {
                    if (value.charAt(i + 1) == '\\') {
                        decoded.write('\\');
                        i++;
                    } else if (value.charAt(i + 1) == 'x') {
                        decoded.write(Integer.parseInt(
                                value.substring(i + 2, i + 4), 16));
                        i += 3;
                    } else {
                        int j = i + 1;
                        while ((j < i + 4) && (j < value.length())
                                && (Character.isDigit(value.charAt(j)))) {
                            j++;
                        }
                        decoded.write(Short.decode(
                                "0" + value.substring(i + 1, j)).byteValue());
                        i = j - 1;
                    }
                } else {
                    decoded.write(value.charAt(i));
                }
            }
            return decoded.toByteArray();
        } catch (NumberFormatException e) {
            throw new MimeTypeException("Invalid string value: " + value, e);
        }
    }

    public boolean eval(byte[] data) {
        try {
            return detector.detect(
                    new ByteArrayInputStream(data), new Metadata()) == MATCH;
        } catch (IOException e) {
            // Should never happen with a ByteArrayInputStream
            return false;
        }
    }

    public int size() {
        return length;
    }

    public String toString() {
        return detector.toString();
    }

}
