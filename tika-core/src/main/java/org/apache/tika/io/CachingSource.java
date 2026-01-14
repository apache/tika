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

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.commons.io.IOUtils;

/**
 * Input source that caches bytes from a raw InputStream.
 * <p>
 * Uses {@link CachingInputStream} to cache bytes as they are read,
 * enabling mark/reset/seek operations. If the cache exceeds a threshold,
 * it spills to a temporary file via {@link StreamCache}.
 */
class CachingSource extends InputStream implements TikaInputSource {

    private final TemporaryResources tmp;
    private CachingInputStream cachingStream;
    private long length;

    // After spilling to file, we switch to file-backed mode
    private Path spilledPath;
    private InputStream fileStream;

    CachingSource(InputStream source, TemporaryResources tmp, long length) {
        this.tmp = tmp;
        this.length = length;
        StreamCache cache = new StreamCache(tmp);
        this.cachingStream = new CachingInputStream(
                source instanceof BufferedInputStream ? source : new BufferedInputStream(source),
                cache
        );
    }

    @Override
    public int read() throws IOException {
        if (fileStream != null) {
            return fileStream.read();
        }
        return cachingStream.read();
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        if (fileStream != null) {
            return fileStream.read(b, off, len);
        }
        return cachingStream.read(b, off, len);
    }

    @Override
    public long skip(long n) throws IOException {
        if (fileStream != null) {
            return IOUtils.skip(fileStream, n);
        }
        //this is safe because cachingStream already does a read instead of trusting the skip
        return cachingStream.skip(n);
    }

    @Override
    public int available() throws IOException {
        if (fileStream != null) {
            return fileStream.available();
        }
        return cachingStream.available();
    }

    @Override
    public void seekTo(long position) throws IOException {
        if (fileStream != null) {
            // After spilling, we need to reopen the file and skip
            fileStream.close();
            fileStream = new BufferedInputStream(Files.newInputStream(spilledPath));
            if (position > 0) {
                IOUtils.skipFully(fileStream, position);
            }
        } else {
            cachingStream.seekTo(position);
        }
    }

    @Override
    public boolean hasPath() {
        return spilledPath != null;
    }

    @Override
    public Path getPath(TemporaryResources tmp, String suffix) throws IOException {
        if (spilledPath == null) {
            // Spill to file and switch to file-backed mode
            spilledPath = cachingStream.spillToFile(suffix);

            // Get current position before closing cache
            long currentPosition = cachingStream.getPosition();

            // Close only the cache, not the source stream (for archive support)
            cachingStream.closeCacheOnly();

            // Open file stream at current position
            fileStream = new BufferedInputStream(Files.newInputStream(spilledPath));
            if (currentPosition > 0) {
                IOUtils.skipFully(fileStream, currentPosition);
            }

            // Update length from file size
            long fileSize = Files.size(spilledPath);
            if (length == -1 || fileSize > 0) {
                length = fileSize;
            }

            cachingStream = null;
        }
        return spilledPath;
    }

    @Override
    public long getLength() {
        return length;
    }

    @Override
    public void close() throws IOException {
        if (fileStream != null) {
            fileStream.close();
        }
        if (cachingStream != null) {
            cachingStream.close();
        }
    }
}
