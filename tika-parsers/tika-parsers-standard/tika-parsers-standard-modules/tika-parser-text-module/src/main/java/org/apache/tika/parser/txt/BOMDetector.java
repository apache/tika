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
package org.apache.tika.parser.txt;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.UnsupportedCharsetException;

import org.apache.commons.io.ByteOrderMark;
import org.apache.commons.io.IOUtils;

import org.apache.tika.detect.EncodingDetector;
import org.apache.tika.metadata.Metadata;

public class BOMDetector implements EncodingDetector {

    private static final ByteOrderMark[] BOMS =
            //order matters -- have to try the 32 before the 16
            new ByteOrderMark[] {
                    ByteOrderMark.UTF_8,
                    ByteOrderMark.UTF_32BE,
                    ByteOrderMark.UTF_32LE,
                    ByteOrderMark.UTF_16BE,
                    ByteOrderMark.UTF_16LE
            };
    private static final Charset[] CHARSETS = new Charset[BOMS.length];

    private static final int MIN_BYTES = 2;
    private static final int MAX_BYTES = 4;

    static {
        for (int i = 0; i < BOMS.length; i++) {
            try {
                CHARSETS[i] = Charset.forName(BOMS[i].getCharsetName());
            } catch (UnsupportedCharsetException e) {
                //log it
            }
        }
    }
    @Override
    public Charset detect(InputStream input, Metadata metadata) throws IOException {
        input.mark(MAX_BYTES);
        byte[] bytes = new byte[MAX_BYTES];
        try {
            int numRead = IOUtils.read(input, bytes);
            if (numRead < MIN_BYTES) {
                return null;
            } else if (numRead < MAX_BYTES) {
                //s
                byte[] tmpBytes = new byte[numRead];
                System.arraycopy(bytes, 0, tmpBytes, 0, numRead);
                bytes = tmpBytes;
            }
        } finally {
            input.reset();
        }
        for (int i = 0; i < BOMS.length; i++) {
            ByteOrderMark bom = BOMS[i];
            if (startsWith(bom, bytes)) {
                return CHARSETS[i];
            }
        }
        return null;
    }

    private boolean startsWith(ByteOrderMark bom, byte[] bytes) {
        byte[] bomBytes = bom.getBytes();
        if (bytes.length < bomBytes.length) {
            return false;
        }
        for (int i = 0; i < bomBytes.length; i++) {
            if (bomBytes[i] != bytes[i]) {
                return false;
            }
        }
        return true;
    }
}
