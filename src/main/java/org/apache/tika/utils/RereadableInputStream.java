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

public class RereadableInputStream extends InputStream {

    private InputStream inputStream;

    private int maxBytesInMemory;

    private boolean firstPass = true;

    private boolean bufferIsInFile;

    private byte[] byteBuffer;

    private int size;

    private File storeFile;

    private OutputStream storeOutputStream;

    public RereadableInputStream(InputStream inputStream, int maxBytesInMemory) {
        this.inputStream = inputStream;
        this.maxBytesInMemory = maxBytesInMemory;
        byteBuffer = new byte[maxBytesInMemory];
    }

    public int read() throws IOException {
        int inputByte = inputStream.read();
        if (firstPass) {
            saveByte(inputByte);
        }
        return inputByte;
    }

    private void saveByte(int inputByte) throws IOException {

        if (!bufferIsInFile) {
            boolean switchToFile = (size == (maxBytesInMemory));
            if (switchToFile) {
                storeFile = File.createTempFile("streamstore_", ".tmp");
                bufferIsInFile = true;
                storeOutputStream = new BufferedOutputStream(
                        new FileOutputStream(storeFile));
                storeOutputStream.write(byteBuffer, 0, size);
                storeOutputStream.write(inputByte);
            } else {
                byteBuffer[size] = (byte) inputByte;
            }
        } else {
            storeOutputStream.write(inputByte);
        }
        ++size;
    }

    public void rewind() throws IOException {
        closeStream();
        if (storeOutputStream != null) {
            storeOutputStream.close();
            storeOutputStream = null;
        }
        firstPass = false;
        boolean newStreamIsInMemory = (size < maxBytesInMemory);
        inputStream = newStreamIsInMemory ? new ByteArrayInputStream(byteBuffer)
        : new BufferedInputStream(new FileInputStream(storeFile));
    }

    public void closeStream() throws IOException {
        if (inputStream != null) {
            inputStream.close();
            inputStream = null;
        }
    }

    public void close() throws IOException {
        closeStream();
        super.close();
        if (storeFile != null) {
            storeFile.delete();
        }
    }
}
