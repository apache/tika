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
import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.commons.io.output.UnsynchronizedByteArrayOutputStream;

import org.apache.tika.io.TemporaryResources;
import org.apache.tika.io.TikaInputStream;

/**
 * Sink for a translated embedded stream: bytes stay in memory up to {@code threshold}
 * and spill to a temp file (owned by {@code tmp}) past it, so the common small object is
 * digested without touching disk.
 */
class TranslatedBytes extends OutputStream {

    private final TemporaryResources tmp;
    private final long threshold;
    private UnsynchronizedByteArrayOutputStream memory =
            UnsynchronizedByteArrayOutputStream.builder().get();
    private long size;
    private Path spillFile;
    private OutputStream spill;

    TranslatedBytes(TemporaryResources tmp, long threshold) {
        this.tmp = tmp;
        this.threshold = threshold;
    }

    @Override
    public void write(int b) throws IOException {
        write(new byte[]{(byte) b}, 0, 1);
    }

    @Override
    public void write(byte[] b, int off, int len) throws IOException {
        if (spill == null && size + len > threshold) {
            spillFile = tmp.createTempFile();
            spill = Files.newOutputStream(spillFile);
            memory.writeTo(spill);
            memory = null;
        }
        if (spill != null) {
            spill.write(b, off, len);
        } else {
            memory.write(b, off, len);
        }
        size += len;
    }

    @Override
    public void close() throws IOException {
        if (spill != null) {
            spill.close();
        }
    }

    boolean isInMemory() {
        return spill == null;
    }

    /** The translated content; the caller closes it. */
    TikaInputStream toTikaInputStream() throws IOException {
        return spill == null ? TikaInputStream.get(memory.toByteArray()) : TikaInputStream.get(spillFile);
    }
}
