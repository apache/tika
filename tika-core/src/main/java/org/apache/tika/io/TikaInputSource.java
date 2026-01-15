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
}
