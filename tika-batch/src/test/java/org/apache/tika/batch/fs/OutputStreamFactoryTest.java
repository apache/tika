package org.apache.tika.batch.fs;

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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.util.Map;
import java.util.concurrent.ExecutionException;

import org.apache.tika.batch.BatchProcess;
import org.apache.tika.batch.ParallelFileProcessingResult;
import org.junit.Test;

public class OutputStreamFactoryTest extends FSBatchTestBase {


    @Test
    public void testIllegalState() throws Exception {
        File outputDir = getNewOutputDir("os-factory-illegal-state-");
        Map<String, String> args = getDefaultArgs("basic", outputDir);
        BatchProcess runner = getNewBatchRunner("/tika-batch-config-test.xml", args);
        run(runner);
        assertEquals(1, outputDir.listFiles().length);

        boolean illegalState = false;
        try {
            ParallelFileProcessingResult result = run(runner);
        } catch (ExecutionException e) {
            if (e.getCause() instanceof IllegalStateException) {
                illegalState = true;
            }
        }
        assertTrue("Should have been an illegal state exception", illegalState);
    }

    @Test
    public void testSkip() throws Exception {
        File outputDir = getNewOutputDir("os-factory-skip-");
        Map<String, String> args = getDefaultArgs("basic", outputDir);
        args.put("handleExisting", "skip");
        BatchProcess runner = getNewBatchRunner("/tika-batch-config-test.xml", args);
        ParallelFileProcessingResult result = run(runner);
        assertEquals(1, outputDir.listFiles().length);

        runner = getNewBatchRunner("/tika-batch-config-test.xml", args);
        result = run(runner);
        assertEquals(1, outputDir.listFiles().length);
    }

    /* turn this back on if there is any need to add "handleExisting"
    @Test
    public void testRename() throws Exception {
        File outputDir = getNewOutputDir("os-factory-rename-");
        Map<String, String> args = getDefaultArgs("basic", outputDir);

        args.put("handleExisting", "rename");
        BatchProcess runner = getNewBatchRunner("/tika-batch-config-basic-test.xml", args);
        ParallelFileProcessingResult result = runner.execute();
        assertEquals(1, outputDir.listFiles().length);

        runner = getNewBatchRunner("/tika-batch-config-basic-test.xml", args);
        result = runner.execute();
        assertEquals(2, outputDir.listFiles().length);

        runner = getNewBatchRunner("/tika-batch-config-basic-test.xml", args);
        result = runner.execute();
        assertEquals(3, outputDir.listFiles().length);

        int hits = 0;
        for (File f : outputDir.listFiles()){
            String name = f.getName();
            if (name.equals("test2_ok.xml.xml")) {
                hits++;
            } else if (name.equals("test1(1).txt.xml")) {
                hits++;
            } else if (name.equals("test1(2).txt.xml")) {
                hits++;
            }
        }
        assertEquals(3, hits);
    }
    */

}
