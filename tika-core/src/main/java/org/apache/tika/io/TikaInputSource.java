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
     * The file this source is already associated with, or {@code null}. Never creates one
     * and never reads from the source, so it is safe where {@link #getPath(String)} is not
     * -- logging, diagnostics, {@code toString()}. For diagnostics only: the file may since
     * have been deleted, and for a stream cache the last bytes may still be buffered until
     * {@link #getPath(String)} completes it.
     */
    Path materializedPath();

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
     * Whether this source stands in for content that is never extracted. Spooling one
     * measures nothing, so its unknown length must not cost a temp file to confirm.
     */
    default boolean isPlaceholder() {
        return false;
    }

    /**
     * Enables full rewind capability.
     * <p>
     * For ByteArraySource and FileSource, this is a no-op (always rewindable).
     * For CachingSource, this switches from passthrough mode to caching mode,
     * enabling subsequent {@link #seekTo(long)} and rewind operations.
     * <p>
     * Must be called when position is 0, otherwise throws IOException.
     *
     * @param budget shared memory budget governing how much a caching source may hold in
     *               memory before spilling, or {@code null} for the per-object default;
     *               inherently rewindable sources ignore it
     * @throws IOException if position is not 0
     */
    void enableRewind(CacheMemoryBudget budget) throws IOException;

    /**
     * Returns a read-only random-access channel over this source's full content: content
     * already in memory (byte[], unspilled cache) is served from memory; file-backed or
     * spilled content from a file channel; unread stream content is drained through the
     * cache, which decides memory-vs-disk during the drain. Fails for a stream-backed
     * source that has been partially read without rewind enabled. Callers own closing the
     * returned channel. Does not change this source's read position.
     *
     * @throws IOException if the source is partially read and cannot be rewound
     */
    SeekableByteChannel getSeekableByteChannel() throws IOException;
}
