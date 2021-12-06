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

package org.apache.tika.parser.microsoft.onenote;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;

/**
 * This is copied mostly from the the
 * former org.apache.tika.parser.mp4.DirectFileReadDataSource
 * <p>
 * Implements a simple way to encapsulate a {@link org.apache.tika.io.TikaInputStream} that you
 * will have to seek,read,repeat while parsing OneNote contents.
 */
class OneNoteDirectFileResource implements Closeable {

    private static final int TRANSFER_SIZE = 8192;

    private final RandomAccessFile raf;

    public OneNoteDirectFileResource(File f) throws IOException {
        this.raf = new RandomAccessFile(f, "r");
    }

    public int read() throws IOException {
        return raf.read();
    }

    public int read(ByteBuffer byteBuffer) throws IOException {
        int len = byteBuffer.remaining();
        int totalRead = 0;
        int bytesRead = 0;
        byte[] buf = new byte[TRANSFER_SIZE];
        while (totalRead < len) {
            int bytesToRead = Math.min((len - totalRead), TRANSFER_SIZE);
            bytesRead = raf.read(buf, 0, bytesToRead);
            if (bytesRead < 0) {
                break;
            } else {
                totalRead += bytesRead;
            }
            byteBuffer.put(buf, 0, bytesRead);
        }
        if (bytesRead < 0 && position() == size() && byteBuffer.hasRemaining()) {
            throw new IOException("End of stream reached earlier than expected");
        }
        return ((bytesRead < 0) && (totalRead == 0)) ? -1 : totalRead;
    }

    public long size() throws IOException {
        return raf.length();
    }

    public long position() throws IOException {
        return raf.getFilePointer();
    }

    public void position(long nuPos) throws IOException {
        if (nuPos > raf.length()) {
            throw new IOException("requesting seek past end of stream");
        }
        raf.seek(nuPos);
    }

    @Override
    public void close() throws IOException {
        raf.close();
    }

}
