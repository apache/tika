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
package org.apache.tika.digest;

import java.io.IOException;
import java.io.OutputStream;

/**
 * A sink returned by {@link Digester#digestSink}: bytes written to it are digested, and the
 * value(s) are set in the metadata when it is closed.
 * <p>
 * Contract, for implementors and callers alike:
 * <ul>
 *   <li>{@link #close()} publishes the digest exactly once; further closes are no-ops.</li>
 *   <li>{@link #abort()} discards everything: a later {@code close()} releases resources
 *       but publishes nothing. Call it when the producer failed part-way, or the metadata
 *       gets a digest of whatever fragment was written.</li>
 *   <li>Writing after {@code close()} or {@code abort()} throws {@link IOException}.</li>
 *   <li>Single producer thread; not safe for concurrent writes.</li>
 * </ul>
 */
public abstract class DigestSink extends OutputStream {

    private boolean closed;
    private boolean aborted;

    /** Discards what was written; {@link #close()} then publishes nothing. Idempotent. */
    public final void abort() {
        aborted = true;
    }

    @Override
    public final void close() throws IOException {
        if (closed) {
            return;
        }
        closed = true;
        finish(!aborted);
    }

    /**
     * Called once, from the first {@link #close()}. Release resources either way; set the
     * metadata only when {@code publish} is true.
     */
    protected abstract void finish(boolean publish) throws IOException;

    /** For subclasses' write methods. */
    protected final void ensureOpen() throws IOException {
        if (closed || aborted) {
            throw new IOException("digest sink is " + (aborted ? "aborted" : "closed"));
        }
    }
}
