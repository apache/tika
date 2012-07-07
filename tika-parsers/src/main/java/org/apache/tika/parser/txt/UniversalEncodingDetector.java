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
import java.util.Collections;

import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;
import org.mozilla.universalchardet.CharsetListener;
import org.mozilla.universalchardet.Constants;
import org.mozilla.universalchardet.UniversalDetector;

public class UniversalEncodingDetector implements EncodingDetector {

    private static final int BUFSIZE = 1024;

    private static final int LOOKAHEAD = 16 * BUFSIZE;

    public MediaType detect(InputStream input, Metadata metadata)
            throws IOException {
        if (input == null) {
            return MediaType.OCTET_STREAM;
        }

        Result result = new Result();
        UniversalDetector detector = new UniversalDetector(result);

        input.mark(LOOKAHEAD);
        try {
            byte[] b = new byte[BUFSIZE];
            int n = 0;
            int m = input.read(b);
            while (m != -1 && n < LOOKAHEAD && !detector.isDone()) {
                n += m;
                detector.handleData(b, 0, m);
                m = input.read(b, 0, Math.min(b.length, LOOKAHEAD - n));
            }
        } finally {
            input.reset();
        }

        detector.dataEnd();

        return result.getType();
    }

    private static class Result implements CharsetListener {

        private String charset = null;

        public void report(String charset) {
            if (Constants.CHARSET_WINDOWS_1252.equals(charset)) {
                this.charset = "ISO-8859-1";
            } else {
                this.charset = charset;
            }
        }

        public MediaType getType() {
            if (charset != null) {
                return new MediaType(
                        MediaType.TEXT_PLAIN,
                        Collections.singletonMap("charset", charset));
            } else {
                return MediaType.OCTET_STREAM;
            }
        }

    }

}
