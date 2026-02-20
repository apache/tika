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
package org.apache.tika.parser.ocr.tess4j;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.InputStream;
import java.util.Collections;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.BodyContentHandler;

public class Tess4JParserTest {

    private static Tess4JParser parser;
    private static boolean tess4jAvailable;

    @BeforeAll
    static void setUp() throws Exception {
        Tess4JConfig config = new Tess4JConfig();
        config.setPoolSize(2);
        config.setDataPath(getTessDataPath());
        config.setNativeLibPath(getNativeLibPath());
        parser = new Tess4JParser(config);
        tess4jAvailable = parser.isInitialized();
    }

    /**
     * Returns the tessdata path, checking system property first, then common locations.
     */
    private static String getTessDataPath() {
        String prop = System.getProperty("tess4j.datapath");
        if (prop != null && !prop.isEmpty()) {
            return prop;
        }
        // Common Homebrew location on macOS
        java.io.File homebrew = new java.io.File("/opt/homebrew/share/tessdata");
        if (homebrew.isDirectory()) {
            return homebrew.getAbsolutePath();
        }
        // Common Linux locations
        java.io.File usrShare = new java.io.File("/usr/share/tesseract-ocr/5/tessdata");
        if (usrShare.isDirectory()) {
            return usrShare.getAbsolutePath();
        }
        java.io.File usrShareAlt = new java.io.File("/usr/share/tessdata");
        if (usrShareAlt.isDirectory()) {
            return usrShareAlt.getAbsolutePath();
        }
        return "";
    }

    /**
     * Returns the native library path, checking system property first, then common locations.
     */
    private static String getNativeLibPath() {
        String prop = System.getProperty("tess4j.native.lib.path");
        if (prop != null && !prop.isEmpty()) {
            return prop;
        }
        // Common Homebrew location on macOS
        java.io.File homebrewLib = new java.io.File("/opt/homebrew/lib");
        if (homebrewLib.isDirectory()) {
            return homebrewLib.getAbsolutePath();
        }
        return "";
    }

    @Test
    public void testDelegatingGettersSetters() throws Exception {
        Tess4JConfig config = new Tess4JConfig();
        config.setPoolSize(1);
        config.setSkipOcr(true);
        Tess4JParser p = new Tess4JParser(config);

        assertEquals("eng", p.getLanguage());
        p.setLanguage("fra");
        assertEquals("fra", p.getLanguage());

        assertEquals(1, p.getPageSegMode());
        p.setPageSegMode(3);
        assertEquals(3, p.getPageSegMode());

        assertEquals(3, p.getOcrEngineMode());
        p.setOcrEngineMode(1);
        assertEquals(1, p.getOcrEngineMode());

        assertEquals(120, p.getTimeoutSeconds());
        p.setTimeoutSeconds(60);
        assertEquals(60, p.getTimeoutSeconds());

        assertEquals(300, p.getDpi());
        p.setDpi(150);
        assertEquals(150, p.getDpi());

        assertTrue(p.isSkipOcr());
    }

    @Test
    public void testSkipOcrReturnEmptyTypes() throws Exception {
        assumeTrue(tess4jAvailable, "Tess4J not available");

        ParseContext context = new ParseContext();
        Tess4JConfig ctxConfig = new Tess4JConfig();
        ctxConfig.setSkipOcr(true);
        context.set(Tess4JConfig.class, ctxConfig);
        assertEquals(Collections.emptySet(), parser.getSupportedTypes(context));
    }

    @Test
    public void testSupportedTypesWhenInitialized() {
        assumeTrue(tess4jAvailable, "Tess4J not available");

        ParseContext context = new ParseContext();
        assertFalse(parser.getSupportedTypes(context).isEmpty());
        assertTrue(parser.getSupportedTypes(context)
                .contains(MediaType.image("ocr-png")));
        assertTrue(parser.getSupportedTypes(context)
                .contains(MediaType.image("ocr-jpeg")));
    }

    @Test
    public void testOcrJpeg() throws Exception {
        assumeTrue(tess4jAvailable, "Tess4J not available");

        BodyContentHandler handler = new BodyContentHandler();
        Metadata metadata = new Metadata();
        ParseContext context = new ParseContext();

        try (InputStream is = getClass().getResourceAsStream("/test-documents/testOCR.jpg");
             TikaInputStream tis = TikaInputStream.get(is)) {
            parser.parse(tis, handler, metadata, context);
        }

        String content = handler.toString();
        assertTrue(content.contains("OCR") || content.contains("Testing"),
                "Expected OCR output to contain recognizable text, got: " + content);
    }

    @Test
    public void testSkipOcrReturnsNoContent() throws Exception {
        assumeTrue(tess4jAvailable, "Tess4J not available");

        Tess4JConfig skipConfig = new Tess4JConfig();
        skipConfig.setPoolSize(1);
        skipConfig.setDataPath(getTessDataPath());
        skipConfig.setNativeLibPath(getNativeLibPath());
        skipConfig.setSkipOcr(true);
        Tess4JParser skipParser = new Tess4JParser(skipConfig);

        BodyContentHandler handler = new BodyContentHandler();
        Metadata metadata = new Metadata();
        ParseContext context = new ParseContext();

        try (InputStream is = getClass().getResourceAsStream("/test-documents/testOCR.jpg");
             TikaInputStream tis = TikaInputStream.get(is)) {
            skipParser.parse(tis, handler, metadata, context);
        }

        assertEquals("", handler.toString().trim());
    }

    @Test
    public void testFileSizeFilter() throws Exception {
        assumeTrue(tess4jAvailable, "Tess4J not available");

        // Set maxFileSizeToOcr to 1 byte so the image is skipped
        ParseContext context = new ParseContext();
        Tess4JConfig smallConfig = new Tess4JConfig();
        smallConfig.setMaxFileSizeToOcr(1);
        context.set(Tess4JConfig.class, smallConfig);

        BodyContentHandler handler = new BodyContentHandler();
        Metadata metadata = new Metadata();

        try (InputStream is = getClass().getResourceAsStream("/test-documents/testOCR.jpg");
             TikaInputStream tis = TikaInputStream.get(is)) {
            parser.parse(tis, handler, metadata, context);
        }

        assertEquals("", handler.toString().trim());
    }

    @Test
    public void testConcurrentOcr() throws Exception {
        assumeTrue(tess4jAvailable, "Tess4J not available");

        int numThreads = 4;
        ExecutorService executor = Executors.newFixedThreadPool(numThreads);
        CountDownLatch latch = new CountDownLatch(numThreads);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        for (int i = 0; i < numThreads; i++) {
            executor.submit(() -> {
                try {
                    BodyContentHandler handler = new BodyContentHandler();
                    Metadata metadata = new Metadata();
                    ParseContext context = new ParseContext();

                    try (InputStream is = getClass()
                            .getResourceAsStream("/test-documents/testOCR.jpg");
                         TikaInputStream tis = TikaInputStream.get(is)) {
                        parser.parse(tis, handler, metadata, context);
                    }

                    String content = handler.toString();
                    if (content != null && !content.trim().isEmpty()) {
                        successCount.incrementAndGet();
                    } else {
                        failCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    failCount.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }

        assertTrue(latch.await(120, TimeUnit.SECONDS), "Timed out waiting for threads");
        executor.shutdown();

        assertEquals(numThreads, successCount.get(),
                "All threads should have succeeded; failures=" + failCount.get());
    }
}
