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

import static org.junit.Assert.assertEquals;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

import org.junit.Test;

import org.apache.tika.utils.RereadableInputStream;

public class TestRereadableInputStream {

    private final int DEFAULT_TEST_SIZE = 3;

    private final int MEMORY_THRESHOLD = 10;

    private final int NUM_PASSES = 4;

    // This size of data keeps us in memory
    private final int TEST_SIZE_MEMORY = 7;

    // This size of data exceeds memory threshold and gets us in a file
    private final int TEST_SIZE_FILE = 15;

    // This size of data exactly equals memory threshold
    private final int TEST_SIZE_MAX = MEMORY_THRESHOLD;

    @Test
    public void testInMemory() throws IOException {
        readEntireStream((TEST_SIZE_MEMORY));
    }

//    @Test
//    public void testInFile() throws IOException {
//        readData(TEST_SIZE_FILE);
//    }
//
//    @Test
//    public void testMemoryThreshold() throws IOException {
//        readData(TEST_SIZE_MAX);
//    }
//
//    @Test
//    public void testInMemory2() throws IOException {
//        readData2((TEST_SIZE_MEMORY));
//    }
//
//    @Test
//    public void testInFile2() throws IOException {
//        readData2(TEST_SIZE_FILE);
//    }

    @Test
    public void testMemoryThreshold2() throws IOException {
        readPartialStream(TEST_SIZE_MAX);
    }

    /**
     * Read entire stream of various sizes
     */
    private void readEntireStream(int testSize) throws IOException {
        InputStream is = createTestInputStream(testSize);
        try (RereadableInputStream ris = new RereadableInputStream(is, MEMORY_THRESHOLD, true)) {
            for (int pass = 0; pass < NUM_PASSES; pass++) {
                for (int byteNum = 0; byteNum < testSize; byteNum++) {
                    int byteRead = ris.read();
                    assertEquals("Pass = " + pass + ", byte num should be " + byteNum + " but is " +
                            byteRead + ".", byteNum, byteRead);
                }
                int eof = ris.read();
                assertEquals(
                        "Pass = " + pass + ", byte num should be " + -1 + " but is " + eof + ".",
                        -1, eof);
                ris.rewind();
            }
        }
    }

    /**
     * Read increasingly more of the stream, but not all, with each pass before rewinding to
     * make sure we pick up at the correct point
     */
    private void readPartialStream(int testSize) throws IOException {
        InputStream is = createTestInputStream(20);
        try (RereadableInputStream ris = new RereadableInputStream(is, MEMORY_THRESHOLD, true)) {

            int iterations = testSize;
            for (int pass = 0; pass < NUM_PASSES; pass++) {
                for (int byteNum = 0; byteNum < iterations; byteNum++) {
                    int byteRead = ris.read();
                    assertEquals("Pass = " + pass + ", byte num should be " + byteNum + " but is " +
                            byteRead + ".", byteNum, byteRead);
                }
                ris.rewind();
                iterations++;
            }
        }
    }


    @Test
    public void testRewind() throws IOException {
        InputStream is = createTestInputStream(DEFAULT_TEST_SIZE);
        try (RereadableInputStream ris = new RereadableInputStream(is, MEMORY_THRESHOLD, true)) {
            ris.rewind();  // rewind before we've done anything
            for (int byteNum = 0; byteNum < 1; byteNum++) {
                int byteRead = ris.read();
                assertEquals("Byte num should be " + byteNum + " but is " + byteRead + ".", byteNum,
                        byteRead);
            }
        }
    }


    private TestInputStream createTestInputStream(int testSize) throws IOException {
        return new TestInputStream(
                new BufferedInputStream(new FileInputStream(createTestFile(testSize))));
    }

    private File createTestFile(int testSize) throws IOException {
        File testfile = File.createTempFile("TIKA_ris_test", ".tmp");
        testfile.deleteOnExit();
        FileOutputStream fos = new FileOutputStream(testfile);
        for (int i = 0; i < testSize; i++) {
            fos.write(i);
        }
        fos.close();
        return testfile;
    }

    @Test
    public void testCloseBehavior() throws IOException {
        doACloseBehaviorTest(true);
        doACloseBehaviorTest(false);
    }

    private void doACloseBehaviorTest(boolean wantToClose) throws IOException {

        TestInputStream tis = createTestInputStream(DEFAULT_TEST_SIZE);
        RereadableInputStream ris = new RereadableInputStream(tis, MEMORY_THRESHOLD, wantToClose);
        ris.close();
        assertEquals(wantToClose, tis.isClosed());

        if (!tis.isClosed()) {
            tis.close();
        }
    }

    @Test(expected = IOException.class)
    public void doReadAfterCloseTest() throws IOException {

        TestInputStream tis = createTestInputStream(DEFAULT_TEST_SIZE);
        RereadableInputStream ris = new RereadableInputStream(tis, DEFAULT_TEST_SIZE);
        ris.close();
        ris.read();
    }


    /**
     * Adds isClosed() to a BufferedInputStream.
     */
    static class TestInputStream extends BufferedInputStream {

        private boolean closed;

        public TestInputStream(InputStream inputStream) {
            super(inputStream);
        }

        public void close() throws IOException {
            super.close();
            closed = true;
        }

        public boolean isClosed() {
            return closed;
        }
    }
}
