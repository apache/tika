/**
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

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

import org.apache.tika.utils.RereadableInputStream;

import junit.framework.TestCase;

public class TestRereadableInputStream extends TestCase {

    private final int TEST_SIZE = 3;

    private final int MEMORY_THRESHOLD = 1;

    private final int NUM_PASSES = 4;

    public void test() throws IOException {

        InputStream is = createTestInputStream();
        RereadableInputStream ris = new RereadableInputStream(is,
                MEMORY_THRESHOLD, true, true);
        try {
            for (int pass = 0; pass < NUM_PASSES; pass++) {
                for (int byteNum = 0; byteNum < TEST_SIZE; byteNum++) {
                    int byteRead = ris.read();
                    assertEquals("Pass = " + pass + ", byte num should be "
                            + byteNum + " but is " + byteRead + ".", byteNum,
                            byteRead);
                }
                ris.rewind();
            }
        } finally {
            // The RereadableInputStream should close the original input
            // stream (if it hasn't already).
            ris.close();
        }
    }

    /**
     * Test that the constructor's readToEndOfStreamOnFirstRewind parameter
     * correctly determines the behavior.
     * 
     * @throws IOException
     */
    public void testRewind() throws IOException {
        doTestRewind(true);
        doTestRewind(false);
    }

    private void doTestRewind(boolean readToEndOnRewind) throws IOException {

        RereadableInputStream ris = null;

        try {
            InputStream s1 = createTestInputStream();
            ris = new RereadableInputStream(s1, 5, readToEndOnRewind, true);
            ris.read();
            assertEquals(1, ris.getSize());
            ris.rewind();
            boolean moreBytesWereRead = (ris.getSize() > 1);
            assertEquals(readToEndOnRewind, moreBytesWereRead);
        } finally {
            if (ris != null) {
                ris.close();
            }
        }

    }

    private TestInputStream createTestInputStream() throws IOException {
        return new TestInputStream(
                new BufferedInputStream(
                        new FileInputStream(createTestFile())));
    }

    private File createTestFile() throws IOException {
        File testfile = File.createTempFile("TIKA_ris_test", ".tmp");
        testfile.deleteOnExit();
        FileOutputStream fos = new FileOutputStream(testfile);
        for (int i = 0; i < TEST_SIZE; i++) {
            fos.write(i);
        }
        fos.close();
        return testfile;
    }


    public void testCloseBehavior() throws IOException {
        doACloseBehaviorTest(true);
        doACloseBehaviorTest(false);
    }

    private void doACloseBehaviorTest(boolean wantToClose) throws IOException {

        TestInputStream tis = createTestInputStream();
        RereadableInputStream ris =
                new RereadableInputStream(tis, 5, true, wantToClose);
        ris.close();
        assertEquals(wantToClose, tis.isClosed());

        if (! tis.isClosed()) {
            tis.close();
        }
    }


    /**
     * Adds isClosed() to a BufferedInputStream.
     */
    class TestInputStream extends BufferedInputStream {

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
