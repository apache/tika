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

package org.apache.tika;

import static org.junit.Assert.fail;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.apache.tika.exception.TikaException;
import org.apache.tika.extractor.EmbeddedDocumentExtractor;
import org.apache.tika.io.IOUtils;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

/**
 * This can be used to test parsers against corrupted/fuzzed files.
 * This aims to ignore SAXExceptions, TikaException and IOException.
 * However, if there is another exception, or parsing takes longer than
 * {@link #MAX_ALLOWABLE_TIME_MILLIS}, then the tmp file that triggered
 * the problem is reported, the seed is reported, and the stacktrace is printed out.
 * <p>
 * This should not be part of the regular unit tests because it will likely
 * unearth a large number of bugs.
 * </p>
 */
@Ignore
public class TestCorruptedFiles extends TikaTest {

    //I did the per_10000, because I wasn't able to reproduce
    //with the seed if I used Random's nextFloat()...may have been
    //user error....

    /**
     *  per 10,000 bytes, how many should be corrupted
     */
    private static final int PER_10000_CORRUPTED = 10;

    /**
     * per 10,000 iterations, how many should be truncated instead of corrupted
     */
    private static final double PER_10000_TRUNCATED = 0;

    /**
     * how much time to allow for the parse
     */
    private static final int MAX_ALLOWABLE_TIME_MILLIS = 60000;

    /**
     * how many times to corrupt and then try to parse the file
     */
    private static final int NUM_ITERATIONS = 1000;

    private static boolean HANDLE_EMBEDDED_DOCS_INDIVIDUALLY = true;

    private static Random randomSeedGenerator = new Random();
    private static Path CORRUPTED;
    private static boolean FAILED;

    @BeforeClass
    public static void setUp() throws IOException {
        CORRUPTED = Files.createTempFile("tika-corrupted-",".tmp");
    }

    @AfterClass
    public static void tearDown() throws IOException {
        if (! FAILED) {
            Files.delete(CORRUPTED);
        } else {
            System.out.println("TRIGGERING FILE:"+CORRUPTED.toAbsolutePath().toString());
        }
    }

    @Test
    public void testSingle() throws Exception {
        String fileName = "testEXCEL_embeddedPDF_windows.xls";
        debug(getRecursiveMetadata("testEXCEL_embeddedPDF_windows.xls"));
        long seed = 0;
        try {
            for (int i = 0; i < 1000; i++) {
                seed = randomSeedGenerator.nextLong();
                FAILED = true;
                long start = new Date().getTime();
                testSingleFile(getBytes(fileName), new Random(seed));
                FAILED = false;
            }
        } catch (Throwable t) {
            t.printStackTrace();
            fail("error "+fileName + " seed: "+seed);
        }
    }

    @Test
    public void testEmbeddedOnly() throws Exception {
        String fileName = "testEXCEL_embeddedPDF_windows.xls";
        Map<String, byte[]> embedded = extract(getBytes(fileName));
        long seed = 0;
        for (int i = 0; i < 1000; i++) {
            for (Map.Entry<String, byte[]> e : embedded.entrySet()) {
                seed = randomSeedGenerator.nextLong();
                try{
                    FAILED = true;
                    testSingleFile(e.getValue(), new Random(seed));
                    FAILED = false;
                } catch (Throwable t) {
                    t.printStackTrace();
                    fail("error fileName "+fileName + " "+e.getKey() + " seed: " + seed);
                }
            }
        }
    }


    @Test
    public void reproduce() throws Throwable {
        long seed = -3351614222367486714L;
        String fileName = "testEXCEL_embeddedPDF_windows.xls";
        try {
            FAILED = true;
            testSingleFile(getBytes(fileName), new Random(seed));
            FAILED = false;
        } finally {

        }
    }

    public void testSingleFile(byte[] bytes, Random random) throws Throwable {
        ExecutorService executorService = Executors.newSingleThreadExecutor();
        ExecutorCompletionService executorCompletionService = new ExecutorCompletionService(executorService);
        executorCompletionService.submit(new ParseTask(bytes, random));
        Future<Boolean> future = executorCompletionService.poll(MAX_ALLOWABLE_TIME_MILLIS, TimeUnit.MILLISECONDS);
        if (future == null) {
            throw new TimeoutException("timed out");
        }

        //if the exception isn't caught, it will be thrown here
        Boolean result = future.get(1, TimeUnit.SECONDS);
        if (result == null) {
            throw new TimeoutException("timed out");
        }
    }

    private class ParseTask implements Callable<Boolean> {
        private byte[] corrupted = null;
        ParseTask(byte[] original, Random random) throws IOException {
            corrupted = corrupt(new ByteArrayInputStream(original), random);
            Files.delete(CORRUPTED);
            OutputStream os = Files.newOutputStream(CORRUPTED, StandardOpenOption.CREATE);
            IOUtils.copy(new ByteArrayInputStream(corrupted), os);
            os.flush();
            os.close();
        }


        @Override
        public Boolean call() throws Exception {
            Parser p = new AutoDetectParser();
            try {
                p.parse(new ByteArrayInputStream(corrupted), new DefaultHandler(),
                        new Metadata(), new ParseContext());
            } catch (SAXException|TikaException|IOException e) {

            }
            return true;
        }
    }

    private byte[] corrupt(InputStream is, Random random) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        IOUtils.copy(is, bos);
        byte[] bytes = bos.toByteArray();
        if (bytes.length == 0) {
            return bytes;
        }

        if (random.nextInt(10000) <= PER_10000_TRUNCATED) {
            int truncatedLength = random.nextInt(bytes.length-1);
            byte[] corrupted = new byte[truncatedLength];
            System.arraycopy(bytes, 0, corrupted, 0, truncatedLength);
            return corrupted;
        } else {
            byte[] corrupted = new byte[bytes.length];
            for (int i = 0; i < bytes.length; i++) {
                byte c = (random.nextInt(10000) < PER_10000_CORRUPTED) ?
                        (byte) random.nextInt(255) : bytes[i];
                corrupted[i] = c;
            }
            return corrupted;
        }
    }


    private byte[] getBytes(String testFile) throws Exception {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        IOUtils.copy(getResourceAsStream("/test-documents/"+testFile), bos);
        return bos.toByteArray();
    }

    private Map<String, byte[]> extract(byte[] bytes) throws Exception {
        Parser p = new AutoDetectParser();
        ParseContext parseContext = new ParseContext();
        Map<String, byte[]> map = new HashMap<>();
        parseContext.set(EmbeddedDocumentExtractor.class, new MyEmbeddedDocumentExtractor(map));
        try (InputStream is = TikaInputStream.get(bytes)){
            p.parse(is, new DefaultHandler(), new Metadata(), parseContext);
        }
        return map;
    }


    private class MyEmbeddedDocumentExtractor implements EmbeddedDocumentExtractor {
        private final Map<String, byte[]> zout;
        private int cnt = 0;

        MyEmbeddedDocumentExtractor(Map<String, byte[]> zout) {
            this.zout = zout;
        }

        public boolean shouldParseEmbedded(Metadata metadata) {
            return true;
        }

        public void parseEmbedded(InputStream inputStream, ContentHandler contentHandler, Metadata metadata, boolean b) throws SAXException, IOException {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            IOUtils.copy(inputStream, bos);
            byte[] data = bos.toByteArray();
            zout.put("embedded-" + Integer.toString(cnt++), data);
        }
    }
}
