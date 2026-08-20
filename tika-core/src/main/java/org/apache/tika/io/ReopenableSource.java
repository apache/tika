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
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.channels.FileChannel;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

import org.apache.commons.io.IOUtils;
import org.apache.commons.io.function.IOSupplier;

/**
 * Input source backed by a re-openable stream supplier (e.g. an entry in a
 * random-access {@code ZipFile}, which can be re-opened via
 * {@code zipFile.getInputStream(entry)}).
 * <p>
 * Because the underlying content can be re-read on demand, {@link #enableRewind()}
 * is a no-op and {@link #seekTo(long)}/rewind simply re-open the source and skip.
 * This avoids the memory-then-disk caching that {@link CachingSource} performs for
 * one-shot streams -- notably the per-embedded-object spill during digesting.
 * A temp file is only created if {@link #getPath} is called (i.e. a parser or
 * detector genuinely needs a File on disk).
 */
class ReopenableSource extends InputStream implements TikaInputSource {

    private final IOSupplier<InputStream> opener;
    private final TemporaryResources tmp;
    private long length;

    private InputStream currentStream; // lazily opened
    private long position;
    private Path spilledPath;
    private long markPosition = -1;

    ReopenableSource(IOSupplier<InputStream> opener, TemporaryResources tmp, long length) {
        this.opener = opener;
        this.tmp = tmp;
        this.length = length;
        this.position = 0;
    }

    private void ensureOpen() throws IOException {
        if (currentStream == null) {
            currentStream = new BufferedInputStream(
                    spilledPath != null ? Files.newInputStream(spilledPath) : opener.get());
        }
    }

    @Override
    public int read() throws IOException {
        ensureOpen();
        int b = currentStream.read();
        if (b != -1) {
            position++;
        }
        return b;
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        ensureOpen();
        int n = currentStream.read(b, off, len);
        if (n > 0) {
            position += n;
        }
        return n;
    }

    @Override
    public long skip(long n) throws IOException {
        ensureOpen();
        long skipped = IOUtils.skip(currentStream, n);
        position += skipped;
        return skipped;
    }

    @Override
    public int available() throws IOException {
        ensureOpen();
        return currentStream.available();
    }

    @Override
    public void seekTo(long newPosition) throws IOException {
        if (newPosition < 0) {
            throw new IOException("Cannot seek to negative position: " + newPosition);
        }
        if (currentStream != null) {
            currentStream.close();
        }
        // Re-open from the beginning (from the spilled file if we've already spilled,
        // otherwise from the supplier) and skip forward.
        currentStream = new BufferedInputStream(
                spilledPath != null ? Files.newInputStream(spilledPath) : opener.get());
        if (newPosition > 0) {
            IOUtils.skipFully(currentStream, newPosition);
        }
        this.position = newPosition;
    }

    @Override
    public boolean hasPath() {
        return spilledPath != null;
    }

    @Override
    public Path getPath(String suffix) throws IOException {
        if (spilledPath == null) {
            // A caller needs a real File -- materialize once from a fresh stream.
            Path p = tmp.createTempFile(suffix);
            try (InputStream in = opener.get(); OutputStream out = Files.newOutputStream(p)) {
                IOUtils.copy(in, out);
            }
            spilledPath = p;
            if (length < 0) {
                length = Files.size(p);
            }
        }
        return spilledPath;
    }

    @Override
    public long getLength() {
        return length;
    }

    @Override
    public void enableRewind() throws IOException {
        // No-op: the source can be re-opened, so no caching is needed to rewind.
    }

    /** Up to this many bytes of a re-openable object may be buffered in memory on demand. */
    private static final long MAX_IN_MEMORY_BYTES = 64L * 1024 * 1024;

    @Override
    public SeekableByteChannel getSeekableByteChannel() throws IOException {
        if (spilledPath == null) {
            // Re-open a fresh stream and buffer it in memory if it fits (the true size is
            // unknowable up front, so the cap is enforced during the read). Does not disturb
            // this source's current position.
            try (InputStream in = opener.get()) {
                ByteArrayOutputStream bos = new ByteArrayOutputStream();
                byte[] buf = new byte[8192];
                long total = 0;
                int r;
                boolean fits = true;
                while ((r = in.read(buf)) != -1) {
                    total += r;
                    if (total > MAX_IN_MEMORY_BYTES) {
                        fits = false;
                        break;
                    }
                    bos.write(buf, 0, r);
                }
                if (fits) {
                    if (length < 0) {
                        length = total;   // the full read established the true length
                    }
                    return new MemorySeekableByteChannel(bos.toByteArray(), (int) total);
                }
            }
        }
        // Too large (or already spilled) -> serve from the spill file
        return FileChannel.open(getPath(null), StandardOpenOption.READ);
    }

    @Override
    public void close() throws IOException {
        if (currentStream != null) {
            currentStream.close();
        }
    }

    @Override
    public synchronized void mark(int readlimit) {
        markPosition = position;
    }

    @Override
    public synchronized void reset() throws IOException {
        if (markPosition < 0) {
            throw new IOException("Mark not set");
        }
        seekTo(markPosition);
    }

    @Override
    public boolean markSupported() {
        return true;
    }
}
