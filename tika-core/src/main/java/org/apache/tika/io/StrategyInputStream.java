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
package org.apache.tika.io;

import java.io.IOException;
import java.io.InputStream;

/**
 * Thin InputStream wrapper around an {@link InputStreamBackingStrategy}.
 * <p>
 * This exists to allow {@link TikaInputStream} to continue extending
 * {@link org.apache.commons.io.input.TaggedInputStream}, which requires
 * wrapping an InputStream.
 */
class StrategyInputStream extends InputStream {

    private final InputStreamBackingStrategy strategy;
    private byte[] skipBuffer;

    StrategyInputStream(InputStreamBackingStrategy strategy) {
        this.strategy = strategy;
    }

    @Override
    public int read() throws IOException {
        return strategy.read();
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        return strategy.read(b, off, len);
    }

    @Override
    public long skip(long n) throws IOException {
        if (skipBuffer == null) {
            skipBuffer = new byte[4096];
        }
        return strategy.skip(n, skipBuffer);
    }

    @Override
    public int available() throws IOException {
        return strategy.available();
    }

    @Override
    public void close() throws IOException {
        strategy.close();
    }

    @Override
    public boolean markSupported() {
        return true;
    }

    // Note: mark() and reset() are handled at the TikaInputStream level,
    // not here. TikaInputStream tracks mark position and calls strategy.seekTo().

    InputStreamBackingStrategy getStrategy() {
        return strategy;
    }
}
