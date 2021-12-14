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
package org.apache.tika.batch.fs;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import org.apache.tika.batch.BatchProcessDriverCLI;
import org.junit.Ignore;
import org.junit.Test;


public class BatchDriverTest extends FSBatchTestBase {

    //for debugging, turn logging off/on via resources/log4j2.properties for the driver
    //and log4j2_process.properties for the process.

    @Test(timeout = 15000)
    public void oneHeavyHangTest() throws Exception {
        //batch runner hits one heavy hang file, keep going
        Path outputDir = getNewOutputDir("daemon-");
        assertTrue(Files.isDirectory(outputDir));
        //make sure output directory is empty!
        assertEquals(0, countChildren(outputDir));

        String[] args = getDefaultCommandLineArgsArr("one_heavy_hang", outputDir, null);
        BatchProcessDriverCLI driver = getNewDriver("/tika-batch-config-test.xml", args);
        driver.execute();

        assertEquals(0, driver.getNumRestarts());
        assertFalse(driver.getUserInterrupted());
        assertEquals(5, countChildren(outputDir));

        assertContains("first test file",
                readFileToString(outputDir.resolve("test2_ok.xml.xml"), UTF_8));
    }

    @Test(timeout = 30000)
    public void restartOnFullHangTest() throws Exception {
        //batch runner hits more heavy hangs than threads; needs to restart
        Path outputDir = getNewOutputDir("daemon-");

        //make sure output directory is empty!
        assertEquals(0, countChildren(outputDir));

        String[] args = getDefaultCommandLineArgsArr("heavy_heavy_hangs", outputDir, null);
        BatchProcessDriverCLI driver = getNewDriver("/tika-batch-config-test.xml", args);
        driver.execute();
        //could be one or two depending on timing
        assertTrue(driver.getNumRestarts() > 0);
        assertFalse(driver.getUserInterrupted());
        assertContains("first test file",
                readFileToString(outputDir.resolve("test6_ok.xml.xml"), UTF_8));
    }

    @Test(timeout = 15000)
    public void noRestartTest() throws Exception {
        Path outputDir = getNewOutputDir("daemon-");

        //make sure output directory is empty!
        assertEquals(0, countChildren(outputDir));

        String[] args = getDefaultCommandLineArgsArr("no_restart", outputDir, null);
        String[] mod = Arrays.copyOf(args, args.length + 2);
        mod[args.length] = "-numConsumers";
        mod[args.length+1] = "1";

        BatchProcessDriverCLI driver = getNewDriver("/tika-batch-config-test.xml", mod);
        driver.execute();
        assertEquals(0, driver.getNumRestarts());
        assertFalse(driver.getUserInterrupted());
        assertEquals(2, countChildren(outputDir));
        Path test2 = outputDir.resolve("test2_norestart.xml.xml");
        assertTrue("test2_norestart.xml", Files.exists(test2));
        Path test3 = outputDir.resolve("test3_ok.xml.xml");
        assertFalse("test3_ok.xml", Files.exists(test3));
    }

    @Test(timeout = 15000)
    public void restartOnOOMTest() throws Exception {
        //batch runner hits more heavy hangs than threads; needs to restart
        Path outputDir = getNewOutputDir("daemon-");

        //make sure output directory is empty!
        assertEquals(0, countChildren(outputDir));

        String[] args = getDefaultCommandLineArgsArr("oom", outputDir, null);
        BatchProcessDriverCLI driver = getNewDriver("/tika-batch-config-test.xml", args);
        driver.execute();
        assertEquals(1, driver.getNumRestarts());
        assertFalse(driver.getUserInterrupted());
        assertContains("first test file",
                readFileToString(outputDir.resolve("test2_ok.xml.xml"), UTF_8));
    }

    @Test(timeout = 60000)
    public void allHeavyHangsTestWithStarvedCrawler() throws Exception {
        //this tests that if all consumers are hung and the crawler is
        //waiting to add to the queue, there isn't deadlock.  The BatchProcess should
        //just shutdown, and the driver should restart
        Path outputDir = getNewOutputDir("allHeavyHangsStarvedCrawler-");
        Map<String, String> args = new HashMap<>();
        args.put("-numConsumers", "2");
        args.put("-maxQueueSize", "2");
        String[] commandLine = getDefaultCommandLineArgsArr("heavy_heavy_hangs", outputDir, args);
        BatchProcessDriverCLI driver = getNewDriver("/tika-batch-config-test.xml", commandLine);
        driver.execute();
        assertEquals(3, driver.getNumRestarts());
        assertFalse(driver.getUserInterrupted());
        assertContains("first test file",
                readFileToString(outputDir.resolve("test6_ok.xml.xml"), UTF_8));
    }

    @Test(timeout = 30000)
    public void maxRestarts() throws Exception {
        //tests that maxRestarts works
        //if -maxRestarts is not correctly removed from the commandline,
        //FSBatchProcessCLI's cli parser will throw an Unrecognized option exception

        Path outputDir = getNewOutputDir("allHeavyHangsStarvedCrawler-");
        Map<String, String> args = new HashMap<>();
        args.put("-numConsumers", "1");
        args.put("-maxQueueSize", "10");
        args.put("-maxRestarts", "2");

        String[] commandLine = getDefaultCommandLineArgsArr("max_restarts", outputDir, args);

        BatchProcessDriverCLI driver = getNewDriver("/tika-batch-config-test.xml", commandLine);
        driver.execute();
        assertEquals(2, driver.getNumRestarts());
        assertFalse(driver.getUserInterrupted());
        assertEquals(3, countChildren(outputDir));
    }

    @Test(timeout = 30000)
    public void maxRestartsBadParameter() throws Exception {
        //tests that maxRestarts must be followed by an Integer
        Path outputDir = getNewOutputDir("allHeavyHangsStarvedCrawler-");
        Map<String, String> args = new HashMap<>();
        args.put("-numConsumers", "1");
        args.put("-maxQueueSize", "10");
        args.put("-maxRestarts", "zebra");

        String[] commandLine = getDefaultCommandLineArgsArr("max_restarts", outputDir, args);
        boolean ex = false;
        try {
            BatchProcessDriverCLI driver = getNewDriver("/tika-batch-config-test.xml", commandLine);
            driver.execute();
        } catch (IllegalArgumentException e) {
            ex = true;
        }
        assertTrue("IllegalArgumentException should have been thrown", ex);
    }

    @Test(timeout = 30000)
    public void testNoRestartIfProcessFails() throws Exception {
        //tests that if something goes horribly wrong with FSBatchProcessCLI
        //the driver will not restart it again and again
        //this calls a bad xml file which should trigger a no restart exit.
        Path outputDir = getNewOutputDir("nostart-norestart-");
        Map<String, String> args = new HashMap<>();
        args.put("-numConsumers", "1");
        args.put("-maxQueueSize", "10");

        String[] commandLine = getDefaultCommandLineArgsArr("basic", outputDir, args);
        BatchProcessDriverCLI driver = getNewDriver("/tika-batch-config-broken.xml", commandLine);
        driver.execute();
        assertEquals(0, countChildren(outputDir));
        assertEquals(0, driver.getNumRestarts());
    }

    @Test(timeout = 30000)
    public void testNoRestartIfProcessFailsTake2() throws Exception {
        Path outputDir = getNewOutputDir("nostart-norestart-");
        Map<String, String> args = new HashMap<>();
        args.put("-numConsumers", "1");
        args.put("-maxQueueSize", "10");
        args.put("-somethingOrOther", "I don't Know");

        String[] commandLine = getDefaultCommandLineArgsArr("basic", outputDir, args);
        BatchProcessDriverCLI driver = getNewDriver("/tika-batch-config-test.xml", commandLine);
        driver.execute();
        assertEquals(0, countChildren(outputDir));
        assertEquals(0, driver.getNumRestarts());
    }

    @Test(timeout = 60000)
    public void testSystemExit() throws Exception {
        Path outputDir = getNewOutputDir("system-exit");
        Map<String, String> args = new HashMap<>();
        args.put("-numConsumers", "1");

        String[] commandLine = getDefaultCommandLineArgsArr("system_exit", outputDir, args);
        BatchProcessDriverCLI driver = getNewDriver("/tika-batch-config-test.xml", commandLine);
        driver.execute();
        assertEquals(6, countChildren(outputDir));
        assertTrue(driver.getNumRestarts() > 1);
        for (int i = 0; i < 3; i++) {
            assertEquals("problem with "+i, 0, Files.size(outputDir.resolve("test"+i+"_system_exit.xml.xml")));
        }
        //sys exit may prevent test3 from running successfully
        for (int i = 5; i < 6; i++) {
            assertContains("first test file",
                    readFileToString(outputDir.resolve("test"+i+"_ok.xml.xml"), UTF_8));
        }
    }

    @Test(timeout = 60000)
    @Ignore("Java 11-ea+23 makes outputstreams uninterruptible")
    public void testThreadInterrupt() throws Exception {
        Path outputDir = getNewOutputDir("thread-interrupt");
        Map<String, String> args = new HashMap<>();
        args.put("-numConsumers", "1");

        String[] commandLine = getDefaultCommandLineArgsArr("thread_interrupt", outputDir, args);
        BatchProcessDriverCLI driver = getNewDriver("/tika-batch-config-test.xml", commandLine);
        driver.execute();
        assertEquals(6, countChildren(outputDir));

        for (int i = 0; i < 3; i++) {
            assertEquals("problem with "+i, 0, Files.size(outputDir.resolve("test"+i+"_thread_interrupt.xml.xml")));
        }
        //sys exit may prevent test3 from running successfully
        for (int i = 5; i < 6; i++) {
            assertContains("first test file",
                    readFileToString(outputDir.resolve("test"+i+"_ok.xml.xml"), UTF_8));
        }
    }

}
