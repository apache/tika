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

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.apache.tika.metadata.Metadata;
import org.junit.Test;

public class TikaInputStreamTest {

    @Test
    public void testFileBased() throws IOException {
        Path path = createTempFile("Hello, World!");
        InputStream stream = TikaInputStream.get(path);

        assertEquals(
                "The file returned by the getFile() method should"
                + " be the file used to instantiate a TikaInputStream",
                path, TikaInputStream.get(stream).getPath());

        assertEquals(
                "The contents of the TikaInputStream should equal the"
                + " contents of the underlying file",
                "Hello, World!", readStream(stream));

        stream.close();
        assertTrue(
                "The close() method must not remove the file used to"
                        + " instantiate a TikaInputStream",
                Files.exists(path));

        Files.delete(path);
    }

    @Test
    public void testStreamBased() throws IOException {
        InputStream input = IOUtils.toInputStream("Hello, World!", UTF_8.name());
        InputStream stream = TikaInputStream.get(input);

        Path file = TikaInputStream.get(stream).getPath();
        assertTrue(file != null && Files.isRegularFile(file));

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
                Files.exists(file));
    }

    private Path createTempFile(String data) throws IOException {
        Path file = Files.createTempFile("tika-", ".tmp");
        Files.write(file, data.getBytes(UTF_8));
        return file;
    }

    private String readFile(Path file) throws IOException {
        return new String(Files.readAllBytes(file), UTF_8);
    }

    private String readStream(InputStream stream) throws IOException {
        return IOUtils.toString(stream, UTF_8.name());
    }

    @Test
    public void testGetMetadata() throws Exception {
        URL url = TikaInputStreamTest.class.getResource("test.txt");
        Metadata metadata = new Metadata();
        TikaInputStream.get(url, metadata).close();
        assertEquals("test.txt", metadata.get(Metadata.RESOURCE_NAME_KEY));
        assertEquals(
                Long.toString(Files.size(Paths.get(url.toURI()))),
                metadata.get(Metadata.CONTENT_LENGTH));
    }

}
