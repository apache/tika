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

/**
 * Backing strategy for file-based inputs.
 * <p>
 * Data is already on disk. No caching is needed.
 * {@link #getPath} returns the existing path immediately.
 * Mark/reset works by reopening the file and skipping to the marked position.
 */
class FileBackedStrategy implements InputStreamBackingStrategy {

    private final Path path;
    private final long length;
    private InputStream currentStream;
    private long position;

    FileBackedStrategy(Path path) throws IOException {
        this.path = path;
        this.length = Files.size(path);
        this.currentStream = new BufferedInputStream(Files.newInputStream(path));
        this.position = 0;
    }

    @Override
    public int read() throws IOException {
        int b = currentStream.read();
        if (b != -1) {
            position++;
        }
        return b;
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        int n = currentStream.read(b, off, len);
        if (n > 0) {
            position += n;
        }
        return n;
    }

    @Override
    public long skip(long n, byte[] skipBuffer) throws IOException {
        long skipped = IOUtils.skip(currentStream, n, skipBuffer);
        position += skipped;
        return skipped;
    }

    @Override
    public int available() throws IOException {
        return currentStream.available();
    }

    private static final byte[] SKIP_BUFFER = new byte[4096];

    @Override
    public void seekTo(long newPosition) throws IOException {
        if (newPosition < 0) {
            throw new IOException("Cannot seek to negative position: " + newPosition);
        }
        if (newPosition > length) {
            throw new IOException("Cannot seek past end of file. Position: " +
                    newPosition + ", length: " + length);
        }

        // Close current stream and reopen at the beginning
        currentStream.close();
        currentStream = new BufferedInputStream(Files.newInputStream(path));

        // Skip to the new position
        if (newPosition > 0) {
            long skipped = IOUtils.skip(currentStream, newPosition, SKIP_BUFFER);
            if (skipped != newPosition) {
                throw new IOException("Failed to seek to position " + newPosition +
                        ", only skipped " + skipped + " bytes");
            }
        }
        this.position = newPosition;
    }

    @Override
    public boolean hasPath() {
        return true;
    }

    @Override
    public Path getPath(TemporaryResources tmp, String suffix) throws IOException {
        // Already file-backed, just return the path
        return path;
    }

    @Override
    public long getLength() {
        return length;
    }

    @Override
    public void close() throws IOException {
        if (currentStream != null) {
            currentStream.close();
        }
    }
}
