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
package org.apache.tika.parser.image;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.xml.sax.helpers.DefaultHandler;

import org.apache.tika.TikaTest;
import org.apache.tika.io.CacheMemoryBudget;
import org.apache.tika.io.TemporaryResources;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TIFF;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;

/**
 * Embedded images usually arrive already in memory; the image parsers must not spool
 * them to disk just to read metadata (they used to call getFile() for every pass).
 */
public class ImageParsersNoTempFileTest extends TikaTest {

    @TempDir
    Path tempDir;

    @Test
    public void testJpeg() throws Exception {
        assertNoTempFile(new JpegParser(), "/test-documents/testJPEG_EXIF.jpg", TIFF.IMAGE_WIDTH.getName());
    }

    @Test
    public void testTiff() throws Exception {
        assertNoTempFile(new TiffParser(), "/test-documents/testTIFF.tif", TIFF.IMAGE_WIDTH.getName());
    }

    @Test
    public void testWebP() throws Exception {
        assertNoTempFile(new WebPParser(), "/test-documents/testWebp_Alpha_Lossless.webp",
                ImageMetadataExtractor.UNKNOWN_IMG_NS + "Image Width");
    }

    private void assertNoTempFile(Parser parser, String resource, String widthKey) throws Exception {
        byte[] bytes;
        try (InputStream is = getResourceAsStream(resource)) {
            bytes = is.readAllBytes();
        }
        ParseContext context = new ParseContext();
        context.set(CacheMemoryBudget.class, new CacheMemoryBudget(64L * 1024 * 1024));
        Metadata metadata = new Metadata();
        try (TemporaryResources tmp = new TemporaryResources()) {
            tmp.setTemporaryFileDirectory(tempDir);
            // a stream-backed, non-file TikaInputStream whose only spill target is tempDir
            TikaInputStream tis = TikaInputStream.get(new ByteArrayInputStream(bytes), tmp, metadata);
            parser.parse(tis, new DefaultHandler(), metadata, context);
            // temp files live until tmp closes, so any spool would be visible right here
            try (Stream<Path> files = Files.list(tempDir)) {
                assertEquals(0, files.count(), "parser spooled an in-memory image to disk");
            }
        }
        assertNotNull(metadata.get(widthKey), "metadata was extracted");
    }
}
