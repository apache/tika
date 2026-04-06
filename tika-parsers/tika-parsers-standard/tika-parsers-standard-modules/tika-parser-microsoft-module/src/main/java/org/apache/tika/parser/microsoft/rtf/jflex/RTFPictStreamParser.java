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
package org.apache.tika.parser.microsoft.rtf.jflex;

import java.io.BufferedOutputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.tika.exception.TikaException;
import org.apache.tika.exception.TikaMemoryLimitException;

/**
 * Streams decoded bytes from an RTF {@code \pict} group to a temp file.
 *
 * <p>Pict data is raw image bytes (after hex-pair decoding). There is no
 * header to parse — bytes are written directly to a temp file. On
 * {@link #onComplete()}, the caller retrieves the temp file path and
 * hands it to the embedded document extractor.</p>
 */
public class RTFPictStreamParser implements Closeable {

    private final long maxBytes;
    private Path tempFile;
    private OutputStream out;
    private long bytesWritten;

    /**
     * @param maxBytes maximum number of bytes to accept (-1 for unlimited)
     */
    public RTFPictStreamParser(long maxBytes) throws IOException {
        this.maxBytes = maxBytes;
        this.tempFile = Files.createTempFile("tika-rtf-pict-", ".bin");
        this.out = new BufferedOutputStream(Files.newOutputStream(tempFile));
    }

    /**
     * Receive a single decoded byte from the pict hex stream.
     */
    public void onByte(int b) throws IOException, TikaException {
        if (maxBytes > 0 && bytesWritten >= maxBytes) {
            throw new TikaMemoryLimitException(bytesWritten + 1, maxBytes);
        }
        out.write(b);
        bytesWritten++;
    }

    /**
     * Called when the pict group closes. Flushes and closes the output stream.
     *
     * @return the path to the temp file containing the image data,
     *         or null if no bytes were written
     */
    public Path onComplete() throws IOException {
        out.close();
        out = null;
        if (bytesWritten == 0) {
            cleanup();
            return null;
        }
        return tempFile;
    }

    /** Returns the number of bytes written so far. */
    public long getBytesWritten() {
        return bytesWritten;
    }

    @Override
    public void close() throws IOException {
        if (out != null) {
            out.close();
            out = null;
        }
        cleanup();
    }

    private void cleanup() {
        if (tempFile != null) {
            try {
                Files.deleteIfExists(tempFile);
            } catch (IOException ignored) {
                // best effort
            }
            tempFile = null;
        }
    }
}
