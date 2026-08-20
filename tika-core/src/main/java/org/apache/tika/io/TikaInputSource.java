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

import java.io.Closeable;
import java.io.IOException;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.Path;

/**
 * Package-private interface for TikaInputStream input sources.
 * <p>
 * Implementations must also extend {@link java.io.InputStream} (or a subclass).
 * This interface defines the additional methods beyond InputStream that
 * TikaInputStream needs.
 */
interface TikaInputSource extends Closeable {

    /**
     * Seeks to a specific position in the stream.
     * Can only seek to positions that have already been read (for CachingSource)
     * or any valid position (for ByteArraySource and FileSource).
     */
    void seekTo(long position) throws IOException;

    /**
     * Returns true if this source has a file path available.
     */
    boolean hasPath();

    /**
     * Gets the file path, potentially spilling to a temp file if needed.
     * @param suffix file suffix for temp files
     * @return the file path
     */
    Path getPath(String suffix) throws IOException;

    /**
     * Returns the length of the content, or -1 if unknown.
     */
    long getLength();

    /**
     * Enables full rewind capability.
     * <p>
     * For ByteArraySource and FileSource, this is a no-op (always rewindable).
     * For CachingSource, this switches from passthrough mode to caching mode,
     * enabling subsequent {@link #seekTo(long)} and rewind operations.
     * <p>
     * Must be called when position is 0, otherwise throws IOException.
     *
     * @throws IOException if position is not 0
     */
    void enableRewind() throws IOException;

    /**
     * Like {@link #enableRewind()}, but supplies a shared {@link CacheMemoryBudget} that governs
     * how much may be held in memory before spilling to disk. Only sources that cache
     * (CachingSource) use it; sources that are inherently rewindable ignore it.
     *
     * @param budget shared memory budget, or {@code null} for the historic per-object default
     */
    default void enableRewind(CacheMemoryBudget budget) throws IOException {
        enableRewind();
    }

    /**
     * Returns a read-only random-access channel over this source's full content. Always
     * succeeds: sources whose content is already in memory (byte[], unspilled cache) serve it
     * from memory; file-backed or spilled sources serve a file channel; stream-backed sources
     * drain into their cache first, which decides memory-vs-disk during the drain (the expanded
     * size of a stream is unknowable up front). Callers own closing the returned channel. Does
     * not change this source's read position.
     */
    SeekableByteChannel getSeekableByteChannel() throws IOException;
}
