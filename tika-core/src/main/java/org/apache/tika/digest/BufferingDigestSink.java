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
import java.io.InputStream;
import java.io.OutputStream;

import org.apache.commons.io.output.DeferredFileOutputStream;

import org.apache.tika.io.TemporaryResources;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;

/**
 * The {@link Digester#digestSink} default for digesters that only implement
 * {@link Digester#digest}: buffers the written bytes (memory below the threshold, a temp file
 * above) and runs the pull-style digest over them on close.
 */
class BufferingDigestSink extends OutputStream {

    static final int MEMORY_THRESHOLD = 1024 * 1024;

    private final Digester digester;
    private final Metadata metadata;
    private final ParseContext context;
    private final TemporaryResources tmp = new TemporaryResources();
    private final DeferredFileOutputStream buffer;
    private boolean closed;

    BufferingDigestSink(Digester digester, Metadata metadata, ParseContext context)
            throws IOException {
        this.digester = digester;
        this.metadata = metadata;
        this.context = context;
        this.buffer = DeferredFileOutputStream.builder()
                .setThreshold(MEMORY_THRESHOLD)
                .setOutputFile(tmp.createTempFile().toFile())
                .get();
    }

    @Override
    public void write(int b) throws IOException {
        buffer.write(b);
    }

    @Override
    public void write(byte[] b, int off, int len) throws IOException {
        buffer.write(b, off, len);
    }

    @Override
    public void close() throws IOException {
        if (closed) {
            return;
        }
        closed = true;
        try {
            buffer.close();
            // toInputStream() serves memory without copying and the file otherwise
            try (InputStream in = buffer.toInputStream();
                 TikaInputStream tis = TikaInputStream.get(in, new TemporaryResources(), null)) {
                digester.digest(tis, metadata, context);
            }
        } finally {
            tmp.close();
        }
    }
}
