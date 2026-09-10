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
package org.apache.tika.parser.hook;

import java.io.IOException;
import java.nio.file.Path;

import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.ParseContext;

/**
 * Sees every document a parse touches, at the entry point that already sees each one once:
 * the auto-detect parser, after detection. A hook is built at config load, carried in the
 * {@link ParseContext} for the duration of the top-level parse, and may ask for a document's
 * bytes after its own parser has run. Tika core knows nothing about what a hook does with
 * them; an inference module does.
 *
 * @since Apache Tika 4.1
 */
public interface ParseHook {

    /** Top of the top-level parse; a hook may refuse the request. */
    default void start(Metadata root, ParseContext context) throws TikaException {
    }

    /** Whether this hook wants the bytes of a document of this detected type. */
    default boolean wants(MediaType type, Metadata metadata, ParseContext context)
            throws TikaException {
        return false;
    }

    /**
     * After the document's parser ran cleanly. {@code bytes} is the document pinned to a
     * file for this call only; {@code parent} is the document it is embedded in, null at top
     * level.
     */
    default void offer(MediaType type, Metadata metadata, Metadata parent, Path bytes,
                       ParseContext context) throws IOException, TikaException {
    }

    /** End of the top-level parse; {@code failed} when the parse threw. */
    default void end(Metadata root, boolean failed, ParseContext context) {
    }
}
