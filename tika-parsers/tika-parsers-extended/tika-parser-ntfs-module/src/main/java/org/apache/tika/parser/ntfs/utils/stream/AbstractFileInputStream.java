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
package org.apache.tika.parser.ntfs.utils.stream;

import java.io.IOException;
import java.io.InputStream;

import org.jetbrains.annotations.NotNull;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.TskCoreException;

public class AbstractFileInputStream extends InputStream {

    /** File to be read by stream. */
    private final AbstractFile abstractFile;

    /**
     * Creates a new input stream for the given SleuthKit abstract file.
     *
     * @param file the abstract file to read from
     */
    public AbstractFileInputStream(final AbstractFile file) {
        this.abstractFile = file;
    }

    /** Byte mask. */
    private static final int BYTE_MASK = 0xFF;

    /**
     * Reads a single byte from the abstract file.
     *
     * @return the byte read, or -1 if end of file
     * @throws IOException if an I/O error occurs
     */
    @Override
    public int read() throws IOException {
        byte[] buf = new byte[1];
        int bytesRead = read(buf, 0, 1);
        return (bytesRead == -1) ? -1 : buf[0] & BYTE_MASK;
    }

    /**
     * Reads up to len bytes of data into an array of bytes
     * from the abstract file.
     *
     * @param buffer the buffer into which the data is read
     * @param off the start offset in the destination array
     * @param len the maximum number of bytes to read
     * @return the number of bytes read, or -1 if end of file
     * @throws IOException if an I/O error occurs
     */
    @Override
    public int read(final byte @NotNull [] buffer, final int off, final int len)
            throws IOException {
        try {
            return abstractFile.read(buffer, off, len);
        } catch (TskCoreException e) {
            throw new IOException("Error reading from abstract file", e);
        }
    }
}
