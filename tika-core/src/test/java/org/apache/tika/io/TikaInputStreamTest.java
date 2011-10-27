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
package org.apache.tika.io;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;

import org.apache.tika.metadata.Metadata;

import junit.framework.TestCase;

public class TikaInputStreamTest extends TestCase {

    public void testFileBased() throws IOException {
        File file = createTempFile("Hello, World!");
        InputStream stream = TikaInputStream.get(file);

        assertEquals(
                "The file returned by the getFile() method should"
                + " be the file used to instantiate a TikaInputStream",
                file, TikaInputStream.get(stream).getFile());

        assertEquals(
                "The contents of the TikaInputStream should equal the"
                + " contents of the underlying file",
                "Hello, World!", readStream(stream));

        stream.close();
        assertTrue(
                "The close() method must not remove the file used to"
                + " instantiate a TikaInputStream",
                file.exists());

        file.delete();
    }

    public void testStreamBased() throws IOException {
        InputStream input =
            new ByteArrayInputStream("Hello, World!".getBytes("UTF-8"));
        InputStream stream = TikaInputStream.get(input);

        File file = TikaInputStream.get(stream).getFile();
        assertTrue(file != null && file.isFile());

        assertEquals(
                "The contents of the file returned by the getFile method"
                + " should equal the contents of the TikaInputStream",
                "Hello, World!", readFile(file));

        assertEquals(
                "The contents of the TikaInputStream should not get modified"
                + " by reading the file first",
                "Hello, World!", readStream(stream));

        stream.close();
        assertFalse(
                "The close() method must remove the temporary file created"
                + " by a TikaInputStream",
                file.exists());
    }

    private File createTempFile(String data) throws IOException {
        File file = File.createTempFile("tika-", ".tmp");
        OutputStream stream = new FileOutputStream(file);
        try {
            stream.write(data.getBytes("UTF-8"));
        } finally {
            stream.close();
        }
        return file;
    }

    private String readFile(File file) throws IOException {
        InputStream stream = new FileInputStream(file);
        try {
            return readStream(stream);
        } finally {
            stream.close();
        }
    }

    private String readStream(InputStream stream) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        IOUtils.copy(stream, buffer);
        return buffer.toString("UTF-8");
    }

    public void testGetMetadata() throws Exception {
        URL url = TikaInputStreamTest.class.getResource("test.txt");
        Metadata metadata = new Metadata();
        TikaInputStream.get(url, metadata).close();
        assertEquals("test.txt", metadata.get(Metadata.RESOURCE_NAME_KEY));
        assertEquals(
                Long.toString(new File(url.toURI()).length()),
                metadata.get(Metadata.CONTENT_LENGTH));
    }

}
