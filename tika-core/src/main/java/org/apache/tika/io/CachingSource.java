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
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.FileChannel;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

import org.apache.commons.io.IOUtils;

import org.apache.tika.metadata.HttpHeaders;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.utils.StringUtils;

/**
 * Input source that wraps a raw InputStream with optional caching.
 * <p>
 * Starts in passthrough mode using {@link BufferedInputStream} for basic
 * mark/reset support. When {@link #enableRewind(CacheMemoryBudget)} is called
 * (at position 0), switches to caching mode using {@link CachingInputStream}
 * which enables full rewind/seek capability.
 * <p>
 * If caching is not enabled, {@link #seekTo(long)} will fail for any position
 * other than the current position.
 */
class CachingSource extends InputStream implements TikaInputSource {

    private final TemporaryResources tmp;
    private final Metadata metadata;
    // temp-file suffix for threshold spills, which precede any getPath(suffix) call
    private final String suffix;
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
    // Retained after a spill so close() can still close it; not closed at spill
    // time because an archive stream may still be in use.
    private InputStream spilledSource;

    CachingSource(InputStream source, TemporaryResources tmp, long length, Metadata metadata,
                  String suffix) {
        this.tmp = tmp;
        this.length = length;
        this.metadata = metadata;
        this.suffix = suffix;
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
    public void enableRewind(CacheMemoryBudget budget) throws IOException {
        // Already in caching or file mode - no-op
        if (cachingStream != null || fileStream != null) {
            return;
        }

        if (passthroughPosition != 0) {
            throw new IOException(
                    "Cannot enable rewind: position is " + passthroughPosition +
                            ", must be 0. Call enableRewind() before reading.");
        }

        // Switch to caching mode
        StreamCache cache = new StreamCache(tmp, suffix, budget);
        cachingStream = new CachingInputStream(passthroughStream, cache);
        passthroughStream = null;
    }

    @Override
    public SeekableByteChannel getSeekableByteChannel() throws IOException {
        if (spilledPath != null) {
            return FileChannel.open(spilledPath, StandardOpenOption.READ);
        }
        // If still in passthrough mode, switch to caching first (same rule as getPath)
        if (cachingStream == null) {
            if (passthroughPosition != 0) {
                throw new IOException(
                        "Cannot create seekable view: position is " + passthroughPosition +
                                ", must be 0. Call enableRewind() before reading.");
            }
            enableRewind(null);
        }
        SeekableByteChannel channel = cachingStream.getSeekableByteChannel();
        // Record the drained length like getPath() does (feeds SecureContentHandler's
        // zip-bomb ratio)
        length = channel.size();
        if (metadata != null &&
                StringUtils.isBlank(metadata.get(HttpHeaders.CONTENT_LENGTH))) {
            metadata.set(HttpHeaders.CONTENT_LENGTH, Long.toString(length));
        }
        return channel;
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
                enableRewind(null);
            }

            // Spill to file and switch to file-backed mode
            spilledPath = cachingStream.spillToFile(suffix);

            // Get current position before closing cache
            long currentPosition = cachingStream.getPosition();

            // close only the cache; close() releases the source later
            spilledSource = cachingStream.getSource();
            cachingStream.closeCacheOnly();

            // Open file stream at current position. Registered with tmp so a caller that
            // disposes the TemporaryResources without closing this source (Tika.detect)
            // still releases the handle -- and releases it before the file is deleted.
            fileStream = new BufferedInputStream(Files.newInputStream(spilledPath));
            tmp.addResource(this::closeFileStream);
            if (currentPosition > 0) {
                IOUtils.skipFully(fileStream, currentPosition);
            }
            filePosition = currentPosition;

            // The spooled size is ground truth, even when it is 0 and a
            // Content-Length claimed otherwise
            length = Files.size(spilledPath);

            // Update metadata if not already set
            if (metadata != null &&
                    StringUtils.isBlank(metadata.get(HttpHeaders.CONTENT_LENGTH))) {
                metadata.set(HttpHeaders.CONTENT_LENGTH, Long.toString(length));
            }

            cachingStream = null;
        }
        return spilledPath;
    }

    @Override
    public long getLength() {
        return length;
    }

    // seekTo() reopens fileStream, so the registered resource must close whichever
    // handle is current rather than the one open at registration time.
    private void closeFileStream() throws IOException {
        if (fileStream != null) {
            fileStream.close();
        }
    }

    @Override
    public void close() throws IOException {
        IOException exception = null;
        for (Closeable closeable :
                new Closeable[]{this::closeFileStream, cachingStream, passthroughStream, spilledSource}) {
            if (closeable == null) {
                continue;
            }
            try {
                closeable.close();
            } catch (IOException e) {
                if (exception == null) {
                    exception = e;
                } else {
                    exception.addSuppressed(e);
                }
            }
        }
        if (exception != null) {
            throw exception;
        }
    }
}
