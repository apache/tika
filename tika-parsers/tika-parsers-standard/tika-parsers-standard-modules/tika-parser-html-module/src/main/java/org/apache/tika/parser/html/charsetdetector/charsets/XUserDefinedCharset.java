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
package org.apache.tika.parser.html.charsetdetector.charsets;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.CoderResult;
import java.nio.charset.StandardCharsets;

public class XUserDefinedCharset extends Charset {

    public XUserDefinedCharset() {
        super("x-user-defined", null);
    }

    @Override
    public boolean contains(Charset cs) {
        return cs.equals(StandardCharsets.US_ASCII);
    }

    public CharsetDecoder newDecoder() {
        return new CharsetDecoder(this, 1, 1) {
            @Override
            protected CoderResult decodeLoop(ByteBuffer in, CharBuffer out) {
                while (true) {
                    if (!in.hasRemaining()) {
                        return CoderResult.UNDERFLOW;
                    }
                    if (!out.hasRemaining()) {
                        return CoderResult.OVERFLOW;
                    }
                    byte b = in.get();
                    out.append((char) ((b >= 0) ? b : 0xF700 + (b & 0xFF)));
                }
            }
        };
    }

    public CharsetEncoder newEncoder() {
        throw new NotImplementedException("Encoding to x-user-defined is not implemented");
    }

    public static class NotImplementedException extends RuntimeException {
        public NotImplementedException(String msg) {
            super(msg);
        }
    }

}
