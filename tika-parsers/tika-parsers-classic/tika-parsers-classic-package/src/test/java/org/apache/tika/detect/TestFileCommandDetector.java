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
package org.apache.tika.detect;

import static org.junit.Assume.assumeTrue;

import java.io.File;
import java.io.FileFilter;
import java.util.Random;

import org.junit.Test;

import org.apache.tika.MultiThreadedTikaTest;
import org.apache.tika.utils.XMLReaderUtils;

public class TestFileCommandDetector extends MultiThreadedTikaTest {

    @Test
    public void testFileDetectorMultiThreaded() throws Exception {
        assumeTrue(FileCommandDetector.checkHasFile());
        Detector detector = new FileCommandDetector();
        FileFilter filter = new FileFilter() {
            //TODO: create proper randomized framework that will record seed, etc...
            private final Random random = new Random();
            //increase this to the number of files for a true smoke test
            //for now, randomly pick 20 files.
            int toProcess = 20;
            int processed = 0;

            @Override
            public boolean accept(File pathname) {
                if (processed >= toProcess) {
                    return false;
                } else if (random.nextBoolean()) {
                    processed++;
                    return true;
                }
                return false;
            }
        };
        int numThreads = 5;
        XMLReaderUtils.setPoolSize(numThreads);

        testDetector(detector, numThreads, 20, filter, numThreads * 3);

    }
}
