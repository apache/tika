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
import java.nio.file.Path;

/**
 * Package-private interface for TikaInputStream backing strategies.
 * Encapsulates read, seek, and file access behaviors for different
 * input source types (byte arrays, files, streams).
 */
interface InputStreamBackingStrategy extends Closeable {

    /**
     * Reads a single byte.
     * @return the byte read, or -1 if end of stream
     */
    int read() throws IOException;

    /**
     * Reads bytes into a buffer.
     * @return number of bytes read, or -1 if end of stream
     */
    int read(byte[] b, int off, int len) throws IOException;

    /**
     * Skips bytes using read operations (not the underlying stream's skip).
     * This ensures bytes are properly cached for strategies that need it.
     * @param n number of bytes to skip
     * @param skipBuffer buffer to use for reading during skip
     * @return actual number of bytes skipped
     */
    long skip(long n, byte[] skipBuffer) throws IOException;

    /**
     * Returns an estimate of available bytes.
     */
    int available() throws IOException;

    /**
     * Seeks to a specific position in the stream.
     * Can only seek to positions that have already been read (for stream-backed)
     * or any valid position (for byte array and file-backed).
     */
    void seekTo(long position) throws IOException;

    /**
     * Returns true if this strategy has a file path available.
     */
    boolean hasPath();

    /**
     * Gets the file path, potentially spilling to a temp file if needed.
     * @param tmp temporary resources for creating temp files
     * @param suffix file suffix for temp files
     * @return the file path
     */
    Path getPath(TemporaryResources tmp, String suffix) throws IOException;

    /**
     * Returns the length of the content, or -1 if unknown.
     */
    long getLength();
}
