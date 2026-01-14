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
import java.nio.file.Path;

/**
 * Package-private InputStream wrapper that caches all bytes read,
 * allowing seeking back to any previously-read position.
 * <p>
 * Bytes are cached in a {@link StreamCache} which stores them in memory
 * up to a threshold, then spills to a temporary file.
 */
class CachingInputStream extends InputStream {

    private final InputStream source;
    private final StreamCache cache;

    private long position;      // Current logical position in the stream
    private boolean sourceExhausted;

    CachingInputStream(InputStream source, StreamCache cache) {
        this.source = source;
        this.cache = cache;
        this.position = 0;
        this.sourceExhausted = false;
    }

    @Override
    public int read() throws IOException {
        if (position < cache.size()) {
            // Reading from cache (replay mode)
            int b = cache.readAt(position);
            if (b != -1) {
                position++;
            }
            return b;
        }

        if (sourceExhausted) {
            return -1;
        }

        // Reading new byte from source
        int b = source.read();
        if (b == -1) {
            sourceExhausted = true;
            return -1;
        }

        cache.append(b);
        position++;
        return b;
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        if (len == 0) {
            return 0;
        }

        int totalRead = 0;

        // First, read any available bytes from cache
        long cacheSize = cache.size();
        if (position < cacheSize) {
            int availableInCache = (int) Math.min(len, cacheSize - position);
            int n = cache.readAt(position, b, off, availableInCache);
            if (n > 0) {
                position += n;
                totalRead += n;
                off += n;
                len -= n;
            }
        }

        // If we need more bytes and source isn't exhausted, read from source
        if (len > 0 && !sourceExhausted) {
            int n = source.read(b, off, len);
            if (n == -1) {
                sourceExhausted = true;
            } else if (n > 0) {
                cache.append(b, off, n);
                position += n;
                totalRead += n;
            }
        }

        return totalRead > 0 ? totalRead : -1;
    }

    @Override
    public long skip(long n) throws IOException {
        if (n <= 0) {
            return 0;
        }

        // We need to actually read the bytes to cache them
        long skipped = 0;
        byte[] buffer = new byte[4096];
        while (skipped < n) {
            int toRead = (int) Math.min(buffer.length, n - skipped);
            int read = read(buffer, 0, toRead);
            if (read == -1) {
                break;
            }
            skipped += read;
        }
        return skipped;
    }

    /**
     * Seek to a specific position in the stream.
     * Can only seek to positions that have already been read (cached).
     */
    void seekTo(long newPosition) throws IOException {
        if (newPosition < 0) {
            throw new IOException("Cannot seek to negative position: " + newPosition);
        }
        if (newPosition > cache.size()) {
            throw new IOException("Cannot seek past cached content. Position: " + newPosition + ", cached: " + cache.size());
        }
        this.position = newPosition;
    }

    /**
     * Get the current position in the stream.
     */
    long getPosition() {
        return position;
    }

    /**
     * Get the number of bytes currently cached.
     */
    long getCachedSize() {
        return cache.size();
    }

    /**
     * Force all remaining content to be read and cached, then return the file path.
     */
    Path spillToFile(String suffix) throws IOException {
        return cache.toFile(source, suffix);
    }

    /**
     * Check if the cache has spilled to a file.
     */
    boolean isFileBacked() {
        return cache.isFileBacked();
    }

    @Override
    public void close() throws IOException {
        source.close();
        cache.close();
    }

    /**
     * Close only the cache, not the underlying source stream.
     * Used when TikaInputStream spills to file - the source stream
     * (e.g., an archive stream) may need to remain open.
     */
    void closeCacheOnly() throws IOException {
        cache.close();
    }

    @Override
    public int available() throws IOException {
        // Return cached bytes available from current position
        long cachedAvailable = cache.size() - position;
        if (cachedAvailable > 0) {
            return (int) Math.min(cachedAvailable, Integer.MAX_VALUE);
        }
        return source.available();
    }

    @Override
    public boolean markSupported() {
        // Mark/reset is handled at the TikaInputStream level
        return false;
    }
}
