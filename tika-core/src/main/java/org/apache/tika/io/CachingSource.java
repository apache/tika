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

import org.apache.tika.metadata.Metadata;
import org.apache.tika.utils.StringUtils;

/**
 * Input source that wraps a raw InputStream with optional caching.
 * <p>
 * Starts in passthrough mode using {@link BufferedInputStream} for basic
 * mark/reset support. When {@link #enableRewind()} is called (at position 0),
 * switches to caching mode using {@link CachingInputStream} which enables
 * full rewind/seek capability.
 * <p>
 * If caching is not enabled, {@link #seekTo(long)} will fail for any position
 * other than the current position.
 */
class CachingSource extends InputStream implements TikaInputSource {

    private final TemporaryResources tmp;
    private final Metadata metadata;
    private long length;

    // Passthrough mode: just a BufferedInputStream
    private BufferedInputStream passthroughStream;
    private long passthroughPosition;

    // Caching mode: CachingInputStream for full rewind support
    private CachingInputStream cachingStream;

    // After spilling to file, we switch to file-backed mode
    private Path spilledPath;
    private InputStream fileStream;
    private long filePosition;  // Track position in file mode

    CachingSource(InputStream source, TemporaryResources tmp, long length, Metadata metadata) {
        this.tmp = tmp;
        this.length = length;
        this.metadata = metadata;
        // Start in passthrough mode
        this.passthroughStream = source instanceof BufferedInputStream
                ? (BufferedInputStream) source
                : new BufferedInputStream(source);
        this.passthroughPosition = 0;
    }

    @Override
    public int read() throws IOException {
        if (fileStream != null) {
            int b = fileStream.read();
            if (b != -1) {
                filePosition++;
            }
            return b;
        }
        if (cachingStream != null) {
            return cachingStream.read();
        }
        int b = passthroughStream.read();
        if (b != -1) {
            passthroughPosition++;
        }
        return b;
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        if (fileStream != null) {
            int n = fileStream.read(b, off, len);
            if (n > 0) {
                filePosition += n;
            }
            return n;
        }
        if (cachingStream != null) {
            return cachingStream.read(b, off, len);
        }
        int n = passthroughStream.read(b, off, len);
        if (n > 0) {
            passthroughPosition += n;
        }
        return n;
    }

    @Override
    public long skip(long n) throws IOException {
        if (fileStream != null) {
            long skipped = IOUtils.skip(fileStream, n);
            filePosition += skipped;
            return skipped;
        }
        if (cachingStream != null) {
            return cachingStream.skip(n);
        }
        long skipped = IOUtils.skip(passthroughStream, n);
        passthroughPosition += skipped;
        return skipped;
    }

    @Override
    public int available() throws IOException {
        if (fileStream != null) {
            return fileStream.available();
        }
        if (cachingStream != null) {
            return cachingStream.available();
        }
        return passthroughStream.available();
    }

    // Track mark position across all modes
    private long markPosition = -1;

    @Override
    public synchronized void mark(int readlimit) {
        if (fileStream != null) {
            // File mode - track position for seekTo-based reset
            markPosition = filePosition;
            return;
        }
        if (cachingStream != null) {
            // Caching mode - track position for seekTo-based reset
            markPosition = cachingStream.getPosition();
            return;
        }
        // Passthrough mode - delegate to BufferedInputStream
        passthroughStream.mark(readlimit);
        markPosition = passthroughPosition;
    }

    @Override
    public synchronized void reset() throws IOException {
        if (markPosition < 0) {
            throw new IOException("Mark not set");
        }
        if (fileStream != null) {
            // File mode - use seekTo
            seekTo(markPosition);
            return;
        }
        if (cachingStream != null) {
            // Caching mode - use seekTo
            cachingStream.seekTo(markPosition);
            return;
        }
        // Passthrough mode - delegate to BufferedInputStream
        passthroughStream.reset();
        passthroughPosition = markPosition;
    }

    @Override
    public boolean markSupported() {
        return true;
    }

    @Override
    public void enableRewind() {
        // Already in caching or file mode - no-op
        if (cachingStream != null || fileStream != null) {
            return;
        }

        if (passthroughPosition != 0) {
            throw new IllegalStateException(
                    "Cannot enable rewind: position is " + passthroughPosition +
                            ", must be 0. Call enableRewind() before reading.");
        }

        // Switch to caching mode
        StreamCache cache = new StreamCache(tmp);
        cachingStream = new CachingInputStream(passthroughStream, cache);
        passthroughStream = null;
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
            filePosition = position;
            return;
        }

        if (cachingStream != null) {
            cachingStream.seekTo(position);
            return;
        }

        // Passthrough mode - can only "seek" to current position
        if (position != passthroughPosition) {
            throw new IOException(
                    "Cannot seek in passthrough mode. Call enableRewind() first. " +
                            "Current position: " + passthroughPosition + ", requested: " + position);
        }
    }

    @Override
    public boolean hasPath() {
        return spilledPath != null;
    }

    @Override
    public Path getPath(String suffix) throws IOException {
        if (spilledPath == null) {
            // If still in passthrough mode, enable caching first
            if (cachingStream == null) {
                if (passthroughPosition != 0) {
                    throw new IOException(
                            "Cannot spill to file: position is " + passthroughPosition +
                                    ", must be 0. Call enableRewind() before reading if you need file access.");
                }
                enableRewind();
            }

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
            filePosition = currentPosition;

            // Update length from file size
            long fileSize = Files.size(spilledPath);
            if (length == -1 || fileSize > 0) {
                length = fileSize;
            }

            // Update metadata if not already set
            if (metadata != null &&
                    StringUtils.isBlank(metadata.get(Metadata.CONTENT_LENGTH))) {
                metadata.set(Metadata.CONTENT_LENGTH, Long.toString(length));
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
        if (passthroughStream != null) {
            passthroughStream.close();
        }
    }
}
