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
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
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
import org.apache.tika.parser.ParseContext;
import org.junit.AfterClass;
import org.junit.BeforeClass;
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
//@Ignore
public class TestCorruptedFiles extends TikaTest {

    //I did the per_10000, because I wasn't able to reproduce
    //with the seed if I used Random's nextFloat()...may have been
    //user error....

    /**
     *  per 10,000 bytes, how many should be corrupted
     */
    private static final int PER_10000_CORRUPTED = 1000;

    /**
     * per 10,000 iterations, how many should be truncated instead of corrupted
     */
    private static final double PER_10000_TRUNCATED = 1000;

    /**
     * per 10,000 iterations, how many should have random bytes concatenated
     */
    private static final double PER_10000_AUGMENTED = 1000;

    /**
     * per 10,000 iterations, how many should have segments swapped
     */
    private static final double PER_10000_SWAPPED = 1000;
    /**
     * how much time to allow for the parse
     */
    private static final int MAX_ALLOWABLE_TIME_MILLIS = 20000;

    /**
     * how many times to corrupt and then try to parse the file
     */
    private static final int NUM_ITERATIONS = 1000;

    private static boolean HANDLE_EMBEDDED_DOCS_INDIVIDUALLY = true;

    private static Random RANDOM_SEED_GENERATOR = new Random();
    private static Path CORRUPTED;
    private static boolean FAILED;

    @BeforeClass
    public static void setUp() throws IOException {
        CORRUPTED = Files.createTempFile("tika-corrupted-",".tmp");
        System.out.println("corrupted file: " + CORRUPTED);
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
    public void testExtension() throws Throwable {
        Random r = new Random();
        long seed = r.nextLong();
        Random rand = new Random(seed);
        List<File> files = Arrays.asList(getResourceAsFile("/test-documents").listFiles());
        Collections.shuffle(files);
        for (File f : files) {
            if (! f.isDirectory()) {//&& f.getName().endsWith(".one")) {
                for (int i = 0; i < NUM_ITERATIONS; i++) {
                    try {
                        FAILED = true;
                        System.out.println("testing: "+f + " : "+i);
                        testSingleFile(getBytes(f.getName()), rand);
                        FAILED = false;
                    } catch (Throwable t) {
                        t.printStackTrace();
                        fail("error "+f.getName()+ " seed: "+seed + " : "+CORRUPTED);
                    }
                }
            }
        }
    }

    @Test
    public void testSingle() throws Exception {
        String fileName = "test.hdf";
        long seed = 7850890625037579255l;
        try {
            for (int i = 0; i < NUM_ITERATIONS; i++) {
                seed = RANDOM_SEED_GENERATOR.nextLong();
                FAILED = true;
                testSingleFile(getBytes(fileName), new Random(seed));
                FAILED = false;
            }
        } catch (Throwable t) {
            t.printStackTrace();
            fail("error "+fileName + " seed: "+seed + " : "+CORRUPTED);
        }
    }

    @Test
    public void testEmbeddedOnly() throws Exception {
        String fileName = "testEXCEL_embeddedPDF_windows.xls";
        Map<String, byte[]> embedded = extract(getBytes(fileName));
        long seed = 0;
        for (int i = 0; i < NUM_ITERATIONS; i++) {
            for (Map.Entry<String, byte[]> e : embedded.entrySet()) {
                seed = RANDOM_SEED_GENERATOR.nextLong();
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

    @Test
    public void testAllTruncated() throws Throwable {
        Random r = new Random();
        for (String fileName : new String[] {
                "testOneNote1.one", "testOneNote2.one", "testOneNote3.one",
                "testOneNote2007OrEarlier1.one", "testOneNote2007OrEarlier2.one"
        }) {
            byte[] bytes = getBytes(fileName);
            int len = bytes.length;
            for (int i = len; i > -1; i -= r.nextInt(1000)) {
                if (i < 0) {
                    break;
                }
                byte[] truncated = new byte[i];
                System.arraycopy(bytes, 0, truncated, 0, i);
                System.out.println("testing length: "+truncated.length + ": "+fileName);
                try {
                    FAILED = true;
                    testSingleFile(truncated);
                    FAILED = false;
                } finally {

                }

            }
        }
    }

    public void testSingleFile(byte[] bytes) throws Throwable {
        ExecutorService executorService = Executors.newSingleThreadExecutor();
        ExecutorCompletionService executorCompletionService = new ExecutorCompletionService(executorService);
        executorCompletionService.submit(new ParseTask(bytes));
        Future<Boolean> future = executorCompletionService.poll(MAX_ALLOWABLE_TIME_MILLIS, TimeUnit.MILLISECONDS);
        if (future == null) {
            throw new TimeoutException("timed out: "+CORRUPTED);
        }

        //if the exception isn't caught, it will be thrown here
        Boolean result = future.get(1, TimeUnit.SECONDS);
        if (result == null) {
            throw new TimeoutException("timed out: " + CORRUPTED);
        }
    }

    public void testSingleFile(byte[] bytes, Random random) throws Throwable {
        ExecutorService executorService = Executors.newSingleThreadExecutor();
        ExecutorCompletionService executorCompletionService = new ExecutorCompletionService(executorService);
        executorCompletionService.submit(new CorruptAndParseTask(bytes, random));
        Future<Boolean> future = executorCompletionService.poll(MAX_ALLOWABLE_TIME_MILLIS, TimeUnit.MILLISECONDS);
        if (future == null) {
            throw new TimeoutException("timed out: "+CORRUPTED);
        }

        //if the exception isn't caught, it will be thrown here
        Boolean result = future.get(1, TimeUnit.SECONDS);
        if (result == null) {
            throw new TimeoutException("timed out: " + CORRUPTED);
        }
    }

    private class ParseTask implements Callable<Boolean> {
        protected byte[] bytes;
        ParseTask(byte[] bytes) {
            this.bytes = bytes;
        }

        @Override
        public Boolean call() throws Exception {
            try {
                AUTO_DETECT_PARSER.parse(new ByteArrayInputStream(bytes), new DefaultHandler(),
                        new Metadata(), new ParseContext());
                //TODO: what else do we want to ignore?
            } catch (SAXException|TikaException|IOException|AssertionError|IllegalArgumentException e) {
            }
            return true;
        }

    }

    private class CorruptAndParseTask extends ParseTask {
        CorruptAndParseTask(byte[] original, Random random) throws IOException {
            super(corrupt(new ByteArrayInputStream(original), random));
            Files.delete(CORRUPTED);
            OutputStream os = Files.newOutputStream(CORRUPTED, StandardOpenOption.CREATE);
            IOUtils.copy(new ByteArrayInputStream(bytes), os);
            os.flush();
            os.close();
        }
    }

    private static byte[] corrupt(InputStream is, Random random) throws IOException {
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
        } else if (random.nextInt(10000) <= PER_10000_AUGMENTED) {
            int len = random.nextInt(10000);
            byte[] corrupted = new byte[bytes.length+len];
            System.arraycopy(bytes, 0, corrupted, 0, bytes.length);
            for (int i = bytes.length; i < corrupted.length; i++) {
                corrupted[i] = (byte) random.nextInt(255);
            }
            return corrupted;
        } else if (random.nextInt(10000) <= PER_10000_SWAPPED) {
            int srcStart = random.nextInt(bytes.length);
            int destStart = random.nextInt(bytes.length);
            int len = random.nextInt((int)((double)bytes.length/(double)4));
            len = Math.max(srcStart, destStart) + len >= bytes.length ?
                    bytes.length-Math.max(srcStart, destStart)-1 : len;

            byte[] corrupted = new byte[bytes.length];
            //first copy everything
            System.arraycopy(bytes, 0, corrupted, 0, bytes.length);
            System.arraycopy(bytes, srcStart, corrupted, destStart, len);
            if (Arrays.equals(bytes, corrupted)) {
                System.err.println("tried to swap, but bytes are identical");
            }
            return corrupted;
        }
        byte[] corrupted = new byte[bytes.length];
        for (int i = 0; i < bytes.length; i++) {
            byte c = (random.nextInt(10000) < PER_10000_CORRUPTED) ?
                    (byte) random.nextInt(255) : bytes[i];
            corrupted[i] = c;
        }
        return corrupted;

    }


    private byte[] getBytes(String testFile) throws Exception {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        IOUtils.copy(getResourceAsStream("/test-documents/"+testFile), bos);
        return bos.toByteArray();
    }

    private Map<String, byte[]> extract(byte[] bytes) throws Exception {
        ParseContext parseContext = new ParseContext();
        Map<String, byte[]> map = new HashMap<>();
        parseContext.set(EmbeddedDocumentExtractor.class, new MyEmbeddedDocumentExtractor(map));
        try (InputStream is = TikaInputStream.get(bytes)){
            AUTO_DETECT_PARSER.parse(is, new DefaultHandler(), new Metadata(), parseContext);
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
