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
package org.apache.tika.detect.microsoft;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import org.apache.poi.poifs.filesystem.POIFSFileSystem;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import org.apache.tika.TikaTest;
import org.apache.tika.io.CacheMemoryBudget;
import org.apache.tika.io.TemporaryResources;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.ParseContext;

/**
 * Detection used to spool every in-memory OLE2 object to a temp file to read its
 * top-level entry names; it must now open the container from memory.
 */
public class POIFSContainerDetectorNoTempFileTest extends TikaTest {

    @TempDir
    Path tempDir;

    @Test
    public void testNoTempFileForInMemoryInput() throws Exception {
        byte[] bytes;
        try (InputStream is = getResourceAsStream("/test-documents/testWORD.doc")) {
            bytes = is.readAllBytes();
        }
        ParseContext context = new ParseContext();
        context.set(CacheMemoryBudget.class, new CacheMemoryBudget(64L * 1024 * 1024));
        Metadata metadata = new Metadata();
        try (TemporaryResources tmp = new TemporaryResources()) {
            tmp.setTemporaryFileDirectory(tempDir);
            TikaInputStream tis = TikaInputStream.get(new ByteArrayInputStream(bytes), tmp, metadata);
            MediaType type = new POIFSContainerDetector().detect(tis, metadata, context);
            assertEquals(MediaType.application("msword"), type);
            try (Stream<Path> files = Files.list(tempDir)) {
                assertEquals(0, files.count(), "detector spooled an in-memory OLE2 object to disk");
            }
            assertTrue(tis.getOpenContainer() instanceof POIFSFileSystem, "open container kept for the parser");
            assertEquals(0, tis.getPosition(), "detection must not move the stream");
            assertEquals(0xd0, tis.read(), "stream still readable from the start");
        }
    }
}
