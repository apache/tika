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
import static org.junit.jupiter.api.Assertions.assertNull;

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
 * Embedded images usually arrive already in memory; the image parsers must not spool them to
 * disk just to read metadata. The file-system tags are the visible consequence: they describe
 * whatever file was read, so in-memory input must not carry them and real files must.
 */
public class ImageParsersNoTempFileTest extends TikaTest {

    private static final String FILE_NAME_KEY = ImageMetadataExtractor.UNKNOWN_IMG_NS + "File Name";
    private static final String FILE_SIZE_KEY = ImageMetadataExtractor.UNKNOWN_IMG_NS + "File Size";
    private static final String FILE_MODIFIED_KEY =
            ImageMetadataExtractor.UNKNOWN_IMG_NS + "File Modified Date";

    @TempDir
    Path tempDir;

    private static final String JPEG = "/test-documents/testJPEG_EXIF.jpg";
    private static final String TIFF_RES = "/test-documents/testTIFF.tif";
    private static final String WEBP = "/test-documents/testWebp_Alpha_Lossless.webp";
    private static final String WEBP_WIDTH = ImageMetadataExtractor.UNKNOWN_IMG_NS + "Image Width";

    private static ParseContext context() {
        ParseContext context = new ParseContext();
        context.set(CacheMemoryBudget.class, new CacheMemoryBudget(64L * 1024 * 1024));
        return context;
    }

    private byte[] bytes(String resource) throws Exception {
        try (InputStream is = getResourceAsStream(resource)) {
            return is.readAllBytes();
        }
    }

    @Test
    public void testJpegInMemory() throws Exception {
        assertNotSpooled(new JpegParser(), JPEG, TIFF.IMAGE_WIDTH.getName());
    }

    @Test
    public void testTiffInMemory() throws Exception {
        assertNotSpooled(new TiffParser(), TIFF_RES, TIFF.IMAGE_WIDTH.getName());
    }

    @Test
    public void testWebPInMemory() throws Exception {
        assertNotSpooled(new WebPParser(), WEBP, WEBP_WIDTH);
    }

    @Test
    public void testJpegFromFile() throws Exception {
        assertKeepsFileTags(new JpegParser(), JPEG, TIFF.IMAGE_WIDTH.getName(), "jpg");
    }

    @Test
    public void testTiffFromFile() throws Exception {
        assertKeepsFileTags(new TiffParser(), TIFF_RES, TIFF.IMAGE_WIDTH.getName(), "tif");
    }

    @Test
    public void testWebPFromFile() throws Exception {
        assertKeepsFileTags(new WebPParser(), WEBP, WEBP_WIDTH, "webp");
    }

    private void assertNotSpooled(Parser parser, String resource, String widthKey)
            throws Exception {
        byte[] bytes = bytes(resource);
        Metadata metadata = new Metadata();
        try (TemporaryResources tmp = new TemporaryResources()) {
            tmp.setTemporaryFileDirectory(tempDir);
            // a stream-backed, non-file TikaInputStream whose only spill target is tempDir
            TikaInputStream tis = TikaInputStream.get(new ByteArrayInputStream(bytes), tmp, metadata);
            parser.parse(tis, new DefaultHandler(), metadata, context());
            // temp files live until tmp closes, so any spool would be visible right here
            try (Stream<Path> files = Files.list(tempDir)) {
                assertEquals(0, files.count(), "parser spooled an in-memory image to disk");
            }
        }
        assertNotNull(metadata.get(widthKey), "metadata was extracted");
        assertNull(metadata.get(FILE_NAME_KEY), "in-memory input must not carry file tags");
        assertNull(metadata.get(FILE_SIZE_KEY), "in-memory input must not carry file tags");
        assertNull(metadata.get(FILE_MODIFIED_KEY), "in-memory input must not carry file tags");
    }

    private void assertKeepsFileTags(Parser parser, String resource, String widthKey, String ext)
            throws Exception {
        byte[] bytes = bytes(resource);
        Path image = tempDir.resolve("image." + ext);
        Files.write(image, bytes);
        Metadata metadata = new Metadata();
        try (TikaInputStream tis = TikaInputStream.get(image, metadata)) {
            parser.parse(tis, new DefaultHandler(), metadata, context());
        }
        assertNotNull(metadata.get(widthKey), "metadata was extracted");
        assertEquals(image.getFileName().toString(), metadata.get(FILE_NAME_KEY),
                "a real file keeps metadata-extractor's file-system tags");
        assertNotNull(metadata.get(FILE_SIZE_KEY), "a real file keeps its size tag");
    }
}
