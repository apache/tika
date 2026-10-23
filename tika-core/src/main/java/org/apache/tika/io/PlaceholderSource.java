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
import java.nio.channels.SeekableByteChannel;
import java.nio.file.Path;

/**
 * Empty stand-in for content that is never extracted: its emptiness describes the
 * placeholder, not the document, so it reports an unknown length rather than zero.
 *
 * @see TikaInputStream#getPlaceholder()
 */
class PlaceholderSource extends InputStream implements TikaInputSource {

    private final TemporaryResources tmp;
    private Path spilledPath;

    PlaceholderSource(TemporaryResources tmp) {
        this.tmp = tmp;
    }

    @Override
    public int read() {
        return -1;
    }

    @Override
    public int read(byte[] b, int off, int len) {
        // InputStream contract: a zero-length read returns 0, even at EOF
        return len == 0 ? 0 : -1;
    }

    @Override
    public long skip(long n) {
        return 0;
    }

    @Override
    public int available() {
        return 0;
    }

    @Override
    public void seekTo(long newPosition) throws IOException {
        if (newPosition != 0) {
            throw new IOException("Invalid seek position: " + newPosition + " (empty source)");
        }
    }

    @Override
    public Path materializedPath() {
        return spilledPath;
    }

    @Override
    public boolean hasPath() {
        return spilledPath != null;
    }

    @Override
    public Path getPath(String suffix) throws IOException {
        if (spilledPath == null) {
            spilledPath = tmp.createTempFile(suffix);
        }
        return spilledPath;
    }

    @Override
    public long getLength() {
        return -1;
    }

    @Override
    public boolean hasReliableLength() {
        return false;
    }

    @Override
    public boolean isPlaceholder() {
        return true;
    }

    @Override
    public void enableRewind(CacheMemoryBudget budget) {
        // No-op: there is nothing to rewind
    }

    @Override
    public SeekableByteChannel getSeekableByteChannel() {
        return new MemorySeekableByteChannel(new byte[0], 0);
    }

    @Override
    public synchronized void mark(int readlimit) {
    }

    @Override
    public synchronized void reset() {
    }

    @Override
    public boolean markSupported() {
        return true;
    }
}
