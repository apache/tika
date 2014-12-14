/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.tika.io;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Test;

public class FilenameUtilsTest {

    /**
     * Different filesystems and operating systems have different restrictions
     * on the name that can be used for files and directories.
     * FilenameUtils.normalize() returns a cross platform file name that turns
     * special characters in a HEX based code convention. This is %<code>.
     * For example why?.zip will be converted into why%3F.zip
     *
     * @see http://en.wikipedia.org/wiki/Filename#Comparison_of_filename_limitations
     *
     * Reserved chars are the ones in FilenameUtils.RESERVED_FILENAME_CHARACTERS:
     */
    @Test
    public void normalizeNothingTodo() throws Exception {
        final String TEST_NAME = "test.zip";

        assertEquals(TEST_NAME, FilenameUtils.normalize(TEST_NAME));
    }

    @Test
    public void normalizeWithNull() throws Exception {
        try {
            FilenameUtils.normalize(null);
            fail("missing check for null parameters");
        } catch (IllegalArgumentException x) {
            assertTrue(x.getMessage().contains("name"));
            assertTrue(x.getMessage().contains("not be null"));
        }
    }

    @Test
    public void normalizeWithReservedChar() throws Exception {
        final String[] TEST_NAMES = {
            "test?.txt", "?test.txt", "test.txt?", "?test?txt?"
        };
        final String[] EXPECTED_NAMES = {
            "test%3F.txt", "%3Ftest.txt", "test.txt%3F", "%3Ftest%3Ftxt%3F"
        };

        for (int i=0; i<TEST_NAMES.length; ++i) {
            //System.out.println("checking " + TEST_NAMES[i]);
            assertEquals(EXPECTED_NAMES[i], FilenameUtils.normalize(TEST_NAMES[i]));
        }
    }

    @Test
    public void normalizeWithReservedChars() throws Exception {
        final String TEST_NAME =
            "?a/b\nc\td\re*f\\g:h<i>j.txt|";
        final String EXPECTED_NAME =
            "%3Fa/b%0Ac%09d%0De%2Af\\g%3Ah%3Ci%3Ej.txt%7C";

        assertEquals(EXPECTED_NAME, FilenameUtils.normalize(TEST_NAME));
    }

    @Test
    public void normalizeWithNotPrintableChars() throws Exception {
        final String TEST_NAME = new String(
            new char[] {
                0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15,
                '.',
                16, 17, 18, 19, 20, 21, 22, 23, 24, 25, 26, 27, 28, 29, 30, 31
            }
        );
        final String EXPECTED_NAME =
            "%00%01%02%03%04%05%06%07%08%09%0A%0B%0C%0D%0E%0F" +
            "." +
            "%10%11%12%13%14%15%16%17%18%19%1A%1B%1C%1D%1E%1F";

        assertEquals(EXPECTED_NAME, FilenameUtils.normalize(TEST_NAME));
    }

    @Test
    public void testGetName() throws Exception {
        testFilenameEquality("quick.ppt", "C:\\the\\quick.ppt");
        testFilenameEquality("quick.ppt", "/the/quick.ppt");
        testFilenameEquality("", "/the/quick/");
        testFilenameEquality("", "~/the/quick////\\\\//");
        testFilenameEquality("~~quick", "~~quick");
        testFilenameEquality("quick.ppt", "quick.ppt");
        testFilenameEquality("", "////");
        testFilenameEquality("", "C:////");
        testFilenameEquality("", "..");
        testFilenameEquality("quick", "C:////../the/D:/quick");
        testFilenameEquality("file.ppt", "path:to:file.ppt" );
        testFilenameEquality("HW.txt", "_1457338542/HW.txt" );
    }

    private void testFilenameEquality(String expected, String path) {
        assertEquals(expected, FilenameUtils.getName(path));
    }

}
