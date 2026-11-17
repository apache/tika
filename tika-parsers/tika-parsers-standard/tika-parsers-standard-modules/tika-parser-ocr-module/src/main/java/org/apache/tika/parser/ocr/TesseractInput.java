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
package org.apache.tika.parser.ocr;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.commons.io.IOUtils;

/**
 * The image formats handed to tesseract, recognized by the signature at the start of the
 * file.
 */
final class TesseractInput {

    // the longest signature below
    private static final int HEAD_LENGTH = 12;

    private static final byte[][] SIGNATURES = {
            {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1a, '\n'},
            {(byte) 0xff, (byte) 0xd8, (byte) 0xff},
            {'I', 'I', 42, 0},
            {'M', 'M', 0, 42},
            {'I', 'I', 43, 0},
            {'M', 'M', 0, 43},
            {'B', 'M'},
            {'G', 'I', 'F', '8', '7', 'a'},
            {'G', 'I', 'F', '8', '9', 'a'},
            {0, 0, 0, 0x0c, 'j', 'P', ' ', ' ', '\r', '\n', (byte) 0x87, '\n'}
    };
    // ImageMagick's name for each signature above
    private static final String[] FORMATS = {"png", "jpeg", "tiff", "tiff", "tiff", "tiff", "bmp", "gif", "gif",
            "jp2"};

    private TesseractInput() {
    }

    static boolean isAccepted(Path path) throws IOException {
        return format(path) != null;
    }

    /**
     * @return the image format the file's signature names, or null if it is not accepted
     */
    static String format(Path path) throws IOException {
        byte[] head = new byte[HEAD_LENGTH];
        int n;
        try (InputStream is = Files.newInputStream(path)) {
            n = IOUtils.read(is, head);
        }
        return format(head, n);
    }

    static String format(byte[] head, int length) {
        if (length < HEAD_LENGTH) {
            return null;
        }
        for (int i = 0; i < SIGNATURES.length; i++) {
            if (startsWith(head, SIGNATURES[i])) {
                return FORMATS[i];
            }
        }
        // PNM: P1 through P6 followed by whitespace
        if (head[0] == 'P' && head[1] >= '1' && head[1] <= '6' &&
                (head[2] == ' ' || head[2] == '\t' || head[2] == '\r' || head[2] == '\n')) {
            return "pnm";
        }
        return null;
    }

    private static boolean startsWith(byte[] head, byte[] signature) {
        for (int i = 0; i < signature.length; i++) {
            if (head[i] != signature[i]) {
                return false;
            }
        }
        return true;
    }
}
