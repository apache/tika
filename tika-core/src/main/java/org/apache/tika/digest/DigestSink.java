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
 * value(s) reach the metadata only if the producer calls {@link #commit()}.
 * <p>
 * Commit is explicit because the alternative -- publish on close unless something remembered
 * to cancel -- makes every unanticipated exit (a checked exception, an {@link Error}, a
 * producer that closes the sink itself) publish a digest of whatever bytes happened to arrive.
 * A digest of a partial write is worse than no digest: it is wrong, and it is stably wrong,
 * so every failure of the same shape produces the same plausible value.
 * <p>
 * Contract:
 * <ul>
 *   <li>Write the content, then {@link #commit()}, then {@link #close()} -- close in a
 *       {@code finally} or via try-with-resources.</li>
 *   <li>{@link #close()} releases resources either way, and publishes only if
 *       {@code commit()} was called first. It is idempotent.</li>
 *   <li>{@link #commit()} after {@code close()} throws {@link IllegalStateException}: the
 *       chance to publish is gone, and failing loudly beats a silently missing digest.</li>
 *   <li>Writing after {@code close()} throws {@link IOException}.</li>
 *   <li>All methods must be called from the producing thread.</li>
 * </ul>
 */
public abstract class DigestSink extends OutputStream {

    private boolean committed;
    private boolean closed;

    /**
     * Marks the content complete, so {@link #close()} publishes it. Idempotent.
     *
     * @throws IllegalStateException if this sink is already closed
     */
    public final void commit() {
        if (closed) {
            throw new IllegalStateException("cannot commit a closed digest sink");
        }
        committed = true;
    }

    @Override
    public final void close() throws IOException {
        if (closed) {
            return;
        }
        closed = true;
        finish(committed);
    }

    /**
     * Called once, from the first {@link #close()}. Release resources either way; set the
     * metadata only when {@code publish} is true.
     */
    protected abstract void finish(boolean publish) throws IOException;

    /** For subclasses' write methods. */
    protected final void ensureOpen() throws IOException {
        if (closed) {
            throw new IOException("digest sink is closed");
        }
    }
}
