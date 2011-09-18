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

package org.apache.tika.parser.chm;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import junit.framework.TestCase;

import org.apache.tika.metadata.Metadata;

public class TestChmExtraction extends TestCase {

    private List<String> files = new ArrayList<String>();

    public void setUp() {
        files.add("/test-documents/testChm.chm");
        files.add("/test-documents/testChm3.chm");
    }

    public void testMultiThreadedChmExtraction() throws InterruptedException {
        ExecutorService executor = Executors
                .newFixedThreadPool(TestParameters.NTHREADS);
        for (int i = 0; i < TestParameters.NTHREADS; i++) {
            executor.execute(new Runnable() {
                public void run() {
                    Lock mutex = new ReentrantLock();
                    for (String fileName : files) {
                        InputStream stream = null;
                        try {
                            stream = TestChmExtraction.class.getResourceAsStream(fileName);

                            CHMDocumentInformation chmDocInfo = CHMDocumentInformation
                                    .load(stream);
                            Metadata md = new Metadata();
                            mutex.lock();
                            String text = chmDocInfo.getText();
                            chmDocInfo.getCHMDocInformation(md);
                            assertEquals(TestParameters.VP_CHM_MIME_TYPE, md.toString().trim());
                            assertTrue(text.length() > 0);
                        } catch (Exception e) {
                            e.printStackTrace();
                        } finally {
                            try {
                                stream.close();
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                            mutex.unlock();
                        }
                    }
                }
            });
        }
        executor.shutdown();
        // Waits until all threads will have finished
        while (!executor.isTerminated()) {
            Thread.sleep(500);
        }
    }
}
