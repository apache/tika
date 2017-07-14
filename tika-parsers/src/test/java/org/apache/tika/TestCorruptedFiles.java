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
import java.util.Random;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.apache.tika.exception.TikaException;
import org.apache.tika.io.IOUtils;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

/**
 * This can be used to test parsers against corrupted files.
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
        long seed = 0;
        try {
            for (int i = 0; i < 1000; i++) {
                seed = randomSeedGenerator.nextLong();
                FAILED = true;
                long start = new Date().getTime();
                testSingleFile(fileName, new Random(seed));
                FAILED = false;
            }
        } catch (Throwable t) {
            t.printStackTrace();
            fail("error "+fileName + " seed: "+seed);
        }
    }

    @Test
    public void reproduce() throws Throwable {
        long seed = -3351614222367486714L;
        String fileName = "testEXCEL_embeddedPDF_windows.xls";
        try {
            FAILED = true;
            testSingleFile(fileName, new Random(seed));
            FAILED = false;
        } finally {

        }
    }

    public void testSingleFile(String fileName, Random random) throws Throwable {
        ExecutorService executorService = Executors.newSingleThreadExecutor();
        ExecutorCompletionService executorCompletionService = new ExecutorCompletionService(executorService);
        executorCompletionService.submit(new ParseTask(fileName, random));
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
        ParseTask(String testFileName, Random random) throws IOException {
            try(InputStream is = getResourceAsStream("/test-documents/"+testFileName)) {
                corrupted = corrupt(is, random);
                Files.delete(CORRUPTED);
                OutputStream os = Files.newOutputStream(CORRUPTED, StandardOpenOption.CREATE);
                IOUtils.copy(new ByteArrayInputStream(corrupted), os);
                os.flush();
                os.close();
            }

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
}
