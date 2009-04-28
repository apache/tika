/**
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
package org.apache.tika.utils;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;


/**
 * Wraps an input stream, reading it only once, but making it available
 * for rereading an arbitrary number of times.  The stream's bytes are
 * stored in memory up to a user specified maximum, and then stored in a
 * temporary file which is deleted when this class' close() method is called.
 */
public class RereadableInputStream extends InputStream {


    /**
     * Input stream originally passed to the constructor.
     */
    private InputStream originalInputStream;

    /**
     * The inputStream currently being used by this object to read contents;
     * may be the original stream passed in, or a stream that reads
     * the saved copy.
     */
    private InputStream inputStream;

    /**
     * Maximum number of bytes that can be stored in memory before
     * storage will be moved to a temporary file.
     */
    private int maxBytesInMemory;

    /**
     * True when the original stream is being read; set to false when
     * reading is set to use the stored data instead.
     */
    private boolean firstPass = true;

    /**
     * Whether or not the stream's contents are being stored in a file
     * as opposed to memory.
     */
    private boolean bufferIsInFile;

    /**
     * The buffer used to store the stream's content; this storage is moved
     * to a file when the stored data's size exceeds maxBytesInMemory.
     */
    private byte[] byteBuffer;

    /**
     * The total number of bytes read from the original stream at the time.
     */
    private int size;

    /**
     * File used to store the stream's contents; is null until the stored
     * content's size exceeds maxBytesInMemory.
     */
    private File storeFile;

    /**
     * OutputStream used to save the content of the input stream in a
     * temporary file.
     */
    private OutputStream storeOutputStream;


    /**
     * Specifies whether or not to read to the end of stream on first
     * rewind.  This defaults to true.  If this is set to false,
     * then the first time when rewind() is called, only those bytes
     * already read from the original stream will be available from then on.
     */
    private boolean readToEndOfStreamOnFirstRewind = true;


    /**
     * Specifies whether or not to close the original input stream
     * when close() is called.  Defaults to true.
     */
    private boolean closeOriginalStreamOnClose = true;


    // TODO: At some point it would be better to replace the current approach
    // (specifying the above) with more automated behavior.  The stream could
    // keep the original stream open until EOF was reached.  For example, if:
    //
    // the original stream is 10 bytes, and
    // only 2 bytes are read on the first pass
    // rewind() is called
    // 5 bytes are read
    //
    // In this case, this instance gets the first 2 from its store,
    // and the next 3 from the original stream, saving those additional 3
    // bytes in the store.  In this way, only the maximum number of bytes
    // ever needed must be saved in the store; unused bytes are never read.
    // The original stream is closed when EOF is reached, or when close()
    // is called, whichever comes first.  Using this approach eliminates
    // the need to specify the flag (though makes implementation more complex).
    


    /**
     * Creates a rereadable input stream.
     *
     * @param inputStream stream containing the source of data
     * @param maxBytesInMemory maximum number of bytes to use to store
     *     the stream's contents in memory before switching to disk; note that
     *     the instance will preallocate a byte array whose size is
     *     maxBytesInMemory.  This byte array will be made available for
     *     garbage collection (i.e. its reference set to null) when the
     *     content size exceeds the array's size, when close() is called, or
     *     when there are no more references to the instance.
     * @param readToEndOfStreamOnFirstRewind Specifies whether or not to
     *     read to the end of stream on first rewind.  If this is set to false,
     *     then when rewind() is first called, only those bytes already read
     *     from the original stream will be available from then on.
     */
    public RereadableInputStream(InputStream inputStream, int maxBytesInMemory,
            boolean readToEndOfStreamOnFirstRewind,
            boolean closeOriginalStreamOnClose) {
        this.inputStream = inputStream;
        this.originalInputStream = inputStream;
        this.maxBytesInMemory = maxBytesInMemory;
        byteBuffer = new byte[maxBytesInMemory];
        this.readToEndOfStreamOnFirstRewind = readToEndOfStreamOnFirstRewind;
        this.closeOriginalStreamOnClose = closeOriginalStreamOnClose;
    }

    /**
     * Reads a byte from the stream, saving it in the store if it is being
     * read from the original stream.  Implements the abstract
     * InputStream.read().
     *
     * @return the read byte, or -1 on end of stream.
     * @throws IOException
     */
    public int read() throws IOException {
        int inputByte = inputStream.read();
        if (firstPass) {
            saveByte(inputByte);
        }
        return inputByte;
    }

    /**
     * "Rewinds" the stream to the beginning for rereading.
     * @throws IOException
     */
    public void rewind() throws IOException {

        if (firstPass && readToEndOfStreamOnFirstRewind) {
            // Force read to end of stream to fill store with any
            // remaining bytes from original stream.
            while(read() != -1) {
                // empty loop
            }
        }

        closeStream();
        if (storeOutputStream != null) {
            storeOutputStream.close();
            storeOutputStream = null;
        }
        firstPass = false;
        boolean newStreamIsInMemory = (size < maxBytesInMemory);
        inputStream = newStreamIsInMemory
                ? new ByteArrayInputStream(byteBuffer)
                : new BufferedInputStream(new FileInputStream(storeFile));
    }

    /**
     * Closes the input stream currently used for reading (may either be
     * the original stream or a memory or file stream after the first pass).
     *
     * @throws IOException
     */
    // Does anyone need/want for this to be public?
    private void closeStream() throws IOException {
        if (inputStream != null
                &&
                (inputStream != originalInputStream
                        || closeOriginalStreamOnClose)) {
            inputStream.close();
            inputStream = null;
        }
    }

    /**
     * Closes the input stream and removes the temporary file if one was
     * created.
     * 
     * @throws IOException
     */
    public void close() throws IOException {
        closeStream();
        super.close();
        if (storeFile != null) {
            storeFile.delete();
        }
    }

    /**
     * Returns the number of bytes read from the original stream.
     *
     * @return number of bytes read
     */
    public int getSize() {
        return size;
    }

    /**
     * Saves the byte read from the original stream to the store.
     *
     * @param inputByte byte read from original stream
     * @throws IOException
     */
    private void saveByte(int inputByte) throws IOException {

        if (!bufferIsInFile) {
            boolean switchToFile = (size == (maxBytesInMemory));
            if (switchToFile) {
                storeFile = File.createTempFile("TIKA_streamstore_", ".tmp");
                bufferIsInFile = true;
                storeOutputStream = new BufferedOutputStream(
                        new FileOutputStream(storeFile));
                storeOutputStream.write(byteBuffer, 0, size);
                storeOutputStream.write(inputByte);
                byteBuffer = null; // release for garbage collection
            } else {
                byteBuffer[size] = (byte) inputByte;
            }
        } else {
            storeOutputStream.write(inputByte);
        }
        ++size;
    }
}
