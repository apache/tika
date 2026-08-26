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
package org.apache.tika.pipes.core.fetcher;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.tika.io.BoundedInputStream;
import org.apache.tika.io.TikaInputStream;

/**
 * Decides how a document reaches the forked worker: as bytes on the wire, or as a file the
 * worker opens itself.
 * <p>
 * Every host that hands pipes an already-open stream needs this same decision, so it lives here
 * rather than in each of them -- three copies would be three thresholds that drift apart.
 */
public final class PayloadRouter {

    private static final Logger LOG = LoggerFactory.getLogger(PayloadRouter.class);

    public enum Route {
        /** Already on disk; the worker opens that file and nothing is copied. */
        EXISTING_FILE,
        /** Small enough to travel inside the request. */
        INLINE,
        /** Too large to inline; written once to a file the worker can reach. */
        SPOOLED
    }

    /** Creates the file a SPOOLED payload is written to. */
    @FunctionalInterface
    public interface SpoolTarget {
        Path create() throws IOException;
    }

    private PayloadRouter() {
    }

    /**
     * Routes {@code tis}, reading no more than it must to decide.
     * <p>
     * A stream that already has a file keeps it: reading an on-disk document into heap to push it
     * through a socket is strictly worse than letting the worker open the file. Otherwise the
     * decision is made from bytes actually read -- a declared length is absent under chunked
     * transfer encoding and client-supplied besides.
     *
     * @param tis            the content; not closed here
     * @param maxInlineBytes largest payload carried inline; 0 disables inlining
     * @param spoolTarget    invoked only if the content exceeds {@code maxInlineBytes}
     */
    public static Routed route(TikaInputStream tis, int maxInlineBytes, SpoolTarget spoolTarget)
            throws IOException {
        if (tis.hasFile()) {
            return new Routed(Route.EXISTING_FILE, null, tis.getPath(), false);
        }
        return route((InputStream) tis, maxInlineBytes, spoolTarget);
    }

    /**
     * The outcome. Closing deletes the spool file if one was created; an EXISTING_FILE path
     * belongs to the caller's stream and is left alone.
     */
    public static final class Routed implements Closeable {

        private final Route route;
        private final InlineBytes inlineBytes;
        private final Path path;
        private final boolean ownsPath;

        private Routed(Route route, InlineBytes inlineBytes, Path path, boolean ownsPath) {
            this.route = route;
            this.inlineBytes = inlineBytes;
            this.path = path;
            this.ownsPath = ownsPath;
        }

        public Route route() {
            return route;
        }

        public boolean isInline() {
            return route == Route.INLINE;
        }

        /** Non-null exactly when {@link #isInline()}. */
        public InlineBytes inlineBytes() {
            return inlineBytes;
        }

        /** Non-null for EXISTING_FILE and SPOOLED. */
        public Path path() {
            return path;
        }

        @Override
        public void close() {
            if (!ownsPath || path == null) {
                return;
            }
            try {
                Files.deleteIfExists(path);
            } catch (IOException e) {
                LOG.warn("Failed to delete spooled input: {}", path, e);
            }
        }
    }

    /** Reads a stream the same way {@link #route} does; for callers holding a plain stream. */
    public static Routed route(InputStream is, int maxInlineBytes, SpoolTarget spoolTarget)
            throws IOException {
        byte[] head = IOUtils.toByteArray(new BoundedInputStream((long) maxInlineBytes + 1, is));
        if (head.length <= maxInlineBytes) {
            return new Routed(Route.INLINE, new InlineBytes(head), null, false);
        }
        Path target = spoolTarget.create();
        try (OutputStream out = Files.newOutputStream(target)) {
            // head was already consumed off the stream; writing it back first is what keeps
            // the spooled file the whole document rather than everything past the threshold.
            out.write(head);
            IOUtils.copy(is, out);
        } catch (IOException e) {
            // No Routed exists yet, so nobody else can delete the partial file.
            try {
                Files.deleteIfExists(target);
            } catch (IOException suppressed) {
                e.addSuppressed(suppressed);
            }
            throw e;
        }
        return new Routed(Route.SPOOLED, null, target, true);
    }
}
