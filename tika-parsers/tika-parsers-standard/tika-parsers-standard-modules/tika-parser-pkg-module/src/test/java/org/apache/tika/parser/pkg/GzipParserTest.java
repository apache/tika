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
package org.apache.tika.parser.pkg;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream;
import org.apache.commons.compress.compressors.gzip.GzipParameters;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import org.apache.tika.TikaTest;
import org.apache.tika.metadata.FileSystem;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.parser.ParseContext;

/**
 * Test case for parsing gzip files.
 */
public class GzipParserTest extends TikaTest {

    @Test
    public void testHeaderMtimeIsFileSystemModified(@TempDir Path tempDir) throws Exception {
        assertEquals("2012-02-20T16:44:22Z", entryModified(tempDir, Instant.parse("2012-02-20T16:44:22Z")));
        assertNull(entryModified(tempDir, Instant.EPOCH));   // MTIME 0: no timestamp
    }

    private String entryModified(Path tempDir, Instant mtime) throws Exception {
        Path gz = tempDir.resolve("mtime-" + mtime.getEpochSecond() + ".gz");
        GzipParameters params = new GzipParameters();
        params.setFileName("a.txt");
        params.setModificationInstant(mtime);
        try (GzipCompressorOutputStream out = new GzipCompressorOutputStream(Files.newOutputStream(gz), params)) {
            out.write("hello".getBytes(StandardCharsets.US_ASCII));
        }
        List<Metadata> list = getRecursiveMetadata(gz, new ParseContext(), false);
        assertNull(list.get(1).get(TikaCoreProperties.MODIFIED));
        return list.get(1).get(FileSystem.MODIFIED);
    }

    /**
     * Tests that the ParseContext parser is correctly
     * fired for all the embedded entries.
     */
    @Test
    public void testEmbedded() throws Exception {
        List<Metadata> metadataList = getRecursiveMetadata("test-documents.tgz");

        // Container plus embedded tar contents
        assertTrue(metadataList.size() > 1);

        // Embedded documents should have path through the tar file
        String embeddedPath = metadataList.get(1).get(TikaCoreProperties.EMBEDDED_RESOURCE_PATH);
        assertTrue(embeddedPath.contains("test-documents.tar"));
    }

    @Test
    public void testGzipInternalFileName() throws Exception {
        List<Metadata> metadataList = getRecursiveMetadata("bob.gz");
        assertEquals(2, metadataList.size());

        Metadata m1 = metadataList.get(1);
        assertEquals("alice.txt", m1.get(TikaCoreProperties.RESOURCE_NAME_KEY));
        assertEquals("alice.txt", m1.get(TikaCoreProperties.INTERNAL_PATH));
    }
}
