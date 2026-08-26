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
import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.commons.io.output.DeferredFileOutputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.tika.io.TemporaryResources;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;

/**
 * The {@link Digester#digestSink} default for digesters that only implement
 * {@link Digester#digest}: buffers the written bytes (memory below the threshold, a temp file
 * above) and runs the pull-style digest over them on close.
 */
class BufferingDigestSink extends DigestSink {

    private static final Logger LOG = LoggerFactory.getLogger(BufferingDigestSink.class);

    static final int MEMORY_THRESHOLD = 1024 * 1024;

    private final Digester digester;
    private final Metadata metadata;
    private final ParseContext context;
    // the DeferredFileOutputStream is eager; the temp file behind it is created only if
    // the content crosses the threshold
    private final DeferredFileOutputStream buffer = DeferredFileOutputStream.builder()
            .setThreshold(MEMORY_THRESHOLD)
            .setPrefix("apache-tika-")
            .setSuffix(".tmp")
            .get();

    BufferingDigestSink(Digester digester, Metadata metadata, ParseContext context) {
        this.digester = digester;
        this.metadata = metadata;
        this.context = context;
    }

    @Override
    public void write(int b) throws IOException {
        ensureOpen();
        buffer.write(b);
    }

    @Override
    public void write(byte[] b, int off, int len) throws IOException {
        ensureOpen();
        buffer.write(b, off, len);
    }

    @Override
    protected void finish(boolean publish) throws IOException {
        // read before close(): getPath() is non-null exactly when a file was created, and
        // unlike isInMemory() it is a field read that cannot throw and cannot be stale
        Path spilled = buffer.getPath();
        try {
            buffer.close();
            if (!publish) {
                return;
            }
            // A re-openable source: the pull digester's enableRewind()/rewind() re-open
            // instead of copying the content into a second cache.
            Path path = spilled;
            try (TemporaryResources tmp = new TemporaryResources();
                 TikaInputStream tis = path == null ?
                         TikaInputStream.get(buffer::toInputStream, tmp, null) :
                         TikaInputStream.get(() -> Files.newInputStream(path), tmp, null)) {
                digester.digest(tis, metadata, context);
            }
        } finally {
            deleteQuietly(spilled);
        }
    }

    // a failed delete must not mask a digest that succeeded
    private static void deleteQuietly(Path spilled) {
        if (spilled == null) {
            return;
        }
        try {
            Files.deleteIfExists(spilled);
        } catch (IOException | RuntimeException e) {
            LOG.warn("could not delete {}; will delete on exit", spilled, e);
            spilled.toFile().deleteOnExit();
        }
    }
}
