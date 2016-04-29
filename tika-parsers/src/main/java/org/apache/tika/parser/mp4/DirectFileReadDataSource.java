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
package org.apache.tika.parser.mp4;

import static com.googlecode.mp4parser.util.CastUtils.l2i;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.channels.WritableByteChannel;

import com.googlecode.mp4parser.DataSource;

/**
 * A {@link DataSource} implementation that relies on direct reads from a {@link RandomAccessFile}.
 * It should be slower than {@link com.googlecode.mp4parser.FileDataSourceImpl} but does not incur the implicit file locks of
 * memory mapped I/O on some JVMs. This implementation allows for a more controlled deletion of files
 * and might be preferred when working with temporary files.
 * @see <a href="http://bugs.java.com/view_bug.do?bug_id=4724038">JDK-4724038 : (fs) Add unmap method to MappedByteBuffer</a>
 * @see <a href="http://bugs.java.com/view_bug.do?bug_id=6359560">JDK-6359560 : (fs) File.deleteOnExit() doesn't work when MappedByteBuffer exists (win)</a>
 */
public class DirectFileReadDataSource implements DataSource {

    private static final int TRANSFER_SIZE = 8192;

    private RandomAccessFile raf;

    public DirectFileReadDataSource(File f) throws IOException {
        this.raf = new RandomAccessFile(f, "r");
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

    public int readAllInOnce(ByteBuffer byteBuffer) throws IOException {
        if (byteBuffer.remaining() > raf.length()) {
            throw new IOException("trying to readAllInOnce past end of stream");
        }
        byte[] buf = new byte[byteBuffer.remaining()];
        int read = raf.read(buf);
        byteBuffer.put(buf, 0, read);
        return read;
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

    public long transferTo(long position, long count, WritableByteChannel target) throws IOException {
        return target.write(map(position, count));
    }

    public ByteBuffer map(long startPosition, long size) throws IOException {
        if (startPosition < 0 || size < 0) {
            throw new IOException("startPosition and size must both be >= 0");
        }
        //make sure that start+size aren't greater than avail size
        //in raf.
        BigInteger end = BigInteger.valueOf(startPosition);
        end = end.add(BigInteger.valueOf(size));
        if (end.compareTo(BigInteger.valueOf(raf.length())) > 0) {
            throw new IOException("requesting read past end of stream");
        }

        raf.seek(startPosition);
        int payLoadSize = l2i(size);
        //hack to check for potential overflow
        if (Long.MAX_VALUE-payLoadSize < startPosition ||
                Long.MAX_VALUE-payLoadSize > raf.length()) {
            throw new IOException("requesting read past end of stream");
        }
        byte[] payload = new byte[payLoadSize];
        raf.readFully(payload);
        return ByteBuffer.wrap(payload);
    }

    @Override
    public void close() throws IOException {
        raf.close();
    }


}
