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

/**
 * An implementation of the standard "replacement" charset defined by the W3C.
 * See: https://encoding.spec.whatwg.org/#replacement
 */
public class ReplacementCharset extends Charset {

    public ReplacementCharset() {
        super("replacement", null);
    }

    @Override
    public boolean contains(Charset cs) {
        return cs.equals(this);
    }

    public CharsetDecoder newDecoder() {
        return new CharsetDecoder(this, Float.MIN_VALUE, 1) {
            private boolean replacementErrorReturned = false;

            @Override
            protected CoderResult decodeLoop(ByteBuffer in, CharBuffer out) {
                if (in.hasRemaining() && !replacementErrorReturned) {
                    replacementErrorReturned = true;
                    return CoderResult.malformedForLength(in.remaining());
                }
                in.position(in.limit());
                return CoderResult.UNDERFLOW;
            }

            @Override
            protected void implReset() {
                replacementErrorReturned = false;
            }
        };
    }

    public CharsetEncoder newEncoder() {
        throw new UnsupportedOperationException("This charset does not support encoding");
    }
}
