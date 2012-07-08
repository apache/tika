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

import org.apache.tika.detect.EncodingDetector;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;
import org.apache.tika.utils.CharsetUtils;
import org.mozilla.universalchardet.CharsetListener;
import org.mozilla.universalchardet.Constants;
import org.mozilla.universalchardet.UniversalDetector;

public class UniversalEncodingDetector implements EncodingDetector {

    private static final int BUFSIZE = 1024;

    private static final int LOOKAHEAD = 16 * BUFSIZE;

    public Charset detect(InputStream input, Metadata metadata)
            throws IOException {
        if (input == null) {
            return null;
        }

        Result result = new Result(metadata);
        UniversalDetector detector = new UniversalDetector(result);

        input.mark(LOOKAHEAD);
        byte[] b = new byte[BUFSIZE];
        int n = 0;
        int m = input.read(b);
        while (m != -1 && n < LOOKAHEAD && !detector.isDone()) {
            n += m;
            detector.handleData(b, 0, m);
            m = input.read(b, 0, Math.min(b.length, LOOKAHEAD - n));
        }
        input.reset();

        detector.dataEnd();
        return result.getCharset();
    }

    private static class Result implements CharsetListener {

        private static final Charset DEFAULT_LATIN_ENCODING =
                Charset.forName("ISO-8859-1");

        private String hint = null;

        private Charset charset = null;

        public Result(Metadata metadata) {
            MediaType type =
                    MediaType.parse(metadata.get(Metadata.CONTENT_TYPE));
            if (type != null) {
                hint = type.getParameters().get("charset");
            }
            if (hint == null) {
                hint = metadata.get(Metadata.CONTENT_ENCODING);
            }
        }

        public void report(String charset) {
            if (!Constants.CHARSET_WINDOWS_1252.equals(charset)) {
                try {
                    this.charset = CharsetUtils.forName(charset);
                } catch (IllegalArgumentException e) {
                    // ignore
                }
            } else {
                if (hint != null) {
                    try {
                        this.charset =
                                CharsetUtils.forName(CharsetUtils.clean(hint));
                    } catch (IllegalArgumentException e) {
                        // ignore
                    }
                } else {
                    this.charset = DEFAULT_LATIN_ENCODING;
                }
            }
        }

        public Charset getCharset() {
            return charset;
        }

    }

}
