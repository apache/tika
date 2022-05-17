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
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;

public class TikaInputStreamTest {

    @TempDir
    Path tempDir;

    @Test
    public void testFileBased() throws IOException {
        Path path = createTempFile("Hello, World!");
        TikaInputStream stream = TikaInputStream.get(path);
        assertTrue(stream.hasFile());
        assertNull(stream.getOpenContainer());
        assertNull(stream.getInputStreamFactory());

        assertEquals(path, TikaInputStream.get(stream).getPath(),
                "The file returned by the getFile() method should" +
                                " be the file used to instantiate a TikaInputStream");

        assertEquals("Hello, World!", readStream(stream),
                "The contents of the TikaInputStream should equal the" +
                        " contents of the underlying file");

        stream.close();
        assertTrue(Files.exists(path),
                "The close() method must not remove the file used to" +
                " instantiate a TikaInputStream");

    }

    @Test
    public void testStreamBased() throws IOException {
        InputStream input = IOUtils.toInputStream("Hello, World!", UTF_8);
        TikaInputStream stream = TikaInputStream.get(input);
        assertFalse(stream.hasFile());
        assertNull(stream.getOpenContainer());
        assertNull(stream.getInputStreamFactory());

        Path file = TikaInputStream.get(stream).getPath();
        assertTrue(file != null && Files.isRegularFile(file));
        assertTrue(stream.hasFile());
        assertNull(stream.getOpenContainer());
        assertNull(stream.getInputStreamFactory());

        assertEquals("Hello, World!", readFile(file),
                "The contents of the file returned by the getFile method" +
                        " should equal the contents of the TikaInputStream");

        assertEquals("Hello, World!", readStream(stream),
                "The contents of the TikaInputStream should not get modified" +
                        " by reading the file first");

        stream.close();
        assertFalse(Files.exists(file),
                "The close() method must remove the temporary file created by a TikaInputStream");
    }

    @Test
    public void testInputStreamFactoryBased() throws IOException {
        TikaInputStream stream = TikaInputStream.get(() -> IOUtils.toInputStream("Hello, World!", UTF_8));
        assertFalse(stream.hasFile());
        assertNull(stream.getOpenContainer());
        assertNotNull(stream.getInputStreamFactory());

        assertEquals("Hello, World!", readStream(stream),
                "The contents of the TikaInputStream should not get modified" +
                        " by reading the file first");
        stream.close();
    }

    private Path createTempFile(String data) throws IOException {
        Path file = Files.createTempFile(tempDir, "tika-", ".tmp");
        Files.write(file, data.getBytes(UTF_8));
        return file;
    }

    private String readFile(Path file) throws IOException {
        return new String(Files.readAllBytes(file), UTF_8);
    }

    private String readStream(InputStream stream) throws IOException {
        return IOUtils.toString(stream, UTF_8);
    }

    @Test
    public void testGetMetadata() throws Exception {
        URL url = TikaInputStreamTest.class.getResource("test.txt");
        Metadata metadata = new Metadata();
        TikaInputStream.get(url, metadata).close();
        assertEquals("test.txt", metadata.get(TikaCoreProperties.RESOURCE_NAME_KEY));
        assertEquals(Long.toString(Files.size(Paths.get(url.toURI()))),
                metadata.get(Metadata.CONTENT_LENGTH));
    }

}
