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

import static junit.framework.Assert.assertEquals;
import static junit.framework.TestCase.assertNotNull;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.io.FileUtils;
import org.apache.tika.batch.BatchProcessDriverCLI;
import org.apache.tika.io.IOUtils;
import org.junit.Test;


public class BatchDriverTest extends FSBatchTestBase {

    //for debugging, turn logging off/on via resources/log4j.properties for the driver
    //and log4j_process.properties for the process.

    @Test(timeout = 15000)
    public void oneHeavyHangTest() throws Exception {
        //batch runner hits one heavy hang file, keep going
        File outputDir = getNewOutputDir("daemon-");
        assertNotNull(outputDir.listFiles());
        //make sure output directory is empty!
        assertEquals(0, outputDir.listFiles().length);

        String[] args = getDefaultCommandLineArgsArr("one_heavy_hang", outputDir, null);
        BatchProcessDriverCLI driver = getNewDriver("/tika-batch-config-test.xml", args);
        driver.execute();
        assertEquals(0, driver.getNumRestarts());
        assertFalse(driver.getUserInterrupted());
        assertEquals(5, outputDir.listFiles().length);
        assertContains("first test file",
                FileUtils.readFileToString(new File(outputDir, "test2_ok.xml.xml"),
                        IOUtils.UTF_8.toString()));


    }

    @Test(timeout = 30000)
    public void restartOnFullHangTest() throws Exception {
        //batch runner hits more heavy hangs than threads; needs to restart
        File outputDir = getNewOutputDir("daemon-");

        //make sure output directory is empty!
        assertEquals(0, outputDir.listFiles().length);

        String[] args = getDefaultCommandLineArgsArr("heavy_heavy_hangs", outputDir, null);
        BatchProcessDriverCLI driver = getNewDriver("/tika-batch-config-test.xml", args);
        driver.execute();
        //could be one or two depending on timing
        assertTrue(driver.getNumRestarts() > 0);
        assertFalse(driver.getUserInterrupted());
        assertContains("first test file",
                FileUtils.readFileToString(new File(outputDir, "test6_ok.xml.xml"),
                        IOUtils.UTF_8.toString()));
    }

    @Test(timeout = 15000)
    public void noRestartTest() throws Exception {
        File outputDir = getNewOutputDir("daemon-");

        //make sure output directory is empty!
        assertEquals(0, outputDir.listFiles().length);

        String[] args = getDefaultCommandLineArgsArr("no_restart", outputDir, null);
        String[] mod = Arrays.copyOf(args, args.length + 2);
        mod[args.length] = "-numConsumers";
        mod[args.length+1] = "1";

        BatchProcessDriverCLI driver = getNewDriver("/tika-batch-config-test.xml", mod);
        driver.execute();
        assertEquals(0, driver.getNumRestarts());
        assertFalse(driver.getUserInterrupted());
        File[] files = outputDir.listFiles();
        assertEquals(2, files.length);
        File test2 = new File(outputDir, "test2_norestart.xml.xml");
        assertTrue("test2_norestart.xml", test2.exists());
        File test3 = new File(outputDir, "test3_ok.xml.xml");
        assertFalse("test3_ok.xml", test3.exists());
        assertEquals(0, test3.length());
    }

    @Test(timeout = 15000)
    public void restartOnOOMTest() throws Exception {
        //batch runner hits more heavy hangs than threads; needs to restart
        File outputDir = getNewOutputDir("daemon-");

        //make sure output directory is empty!
        assertEquals(0, outputDir.listFiles().length);

        String[] args = getDefaultCommandLineArgsArr("oom", outputDir, null);
        BatchProcessDriverCLI driver = getNewDriver("/tika-batch-config-test.xml", args);
        driver.execute();
        assertEquals(1, driver.getNumRestarts());
        assertFalse(driver.getUserInterrupted());
        assertContains("first test file",
                FileUtils.readFileToString(new File(outputDir, "test2_ok.xml.xml"),
                        IOUtils.UTF_8.toString()));
    }

    @Test(timeout = 30000)
    public void allHeavyHangsTestWithStarvedCrawler() throws Exception {
        //this tests that if all consumers are hung and the crawler is
        //waiting to add to the queue, there isn't deadlock.  The BatchProcess should
        //just shutdown, and the driver should restart
        File outputDir = getNewOutputDir("allHeavyHangsStarvedCrawler-");
        Map<String, String> args = new HashMap<String,String>();
        args.put("-numConsumers", "2");
        args.put("-maxQueueSize", "2");
        String[] commandLine = getDefaultCommandLineArgsArr("heavy_heavy_hangs", outputDir, args);
        BatchProcessDriverCLI driver = getNewDriver("/tika-batch-config-test.xml", commandLine);
        driver.execute();
        assertEquals(3, driver.getNumRestarts());
        assertFalse(driver.getUserInterrupted());
        assertContains("first test file",
                FileUtils.readFileToString(new File(outputDir, "test6_ok.xml.xml"),
                        IOUtils.UTF_8.toString()));
    }

    @Test(timeout = 30000)
    public void maxRestarts() throws Exception {
        //tests that maxRestarts works
        //if -maxRestarts is not correctly removed from the commandline,
        //FSBatchProcessCLI's cli parser will throw an Unrecognized option exception

        File outputDir = getNewOutputDir("allHeavyHangsStarvedCrawler-");
        Map<String, String> args = new HashMap<String,String>();
        args.put("-numConsumers", "1");
        args.put("-maxQueueSize", "10");
        args.put("-maxRestarts", "2");

        String[] commandLine = getDefaultCommandLineArgsArr("max_restarts", outputDir, args);

        BatchProcessDriverCLI driver = getNewDriver("/tika-batch-config-test.xml", commandLine);
        driver.execute();
        assertEquals(2, driver.getNumRestarts());
        assertFalse(driver.getUserInterrupted());
        assertEquals(3, outputDir.listFiles().length);
    }

    @Test(timeout = 30000)
    public void maxRestartsBadParameter() throws Exception {
        //tests that maxRestarts must be followed by an Integer
        File outputDir = getNewOutputDir("allHeavyHangsStarvedCrawler-");
        Map<String, String> args = new HashMap<String,String>();
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
        File outputDir = getNewOutputDir("nostart-norestart-");
        Map<String, String> args = new HashMap<String,String>();
        args.put("-numConsumers", "1");
        args.put("-maxQueueSize", "10");

        String[] commandLine = getDefaultCommandLineArgsArr("basic", outputDir, args);
        BatchProcessDriverCLI driver = getNewDriver("/tika-batch-config-broken.xml", commandLine);
        driver.execute();
        assertEquals(0, outputDir.listFiles().length);
        assertEquals(0, driver.getNumRestarts());
    }

    @Test(timeout = 30000)
    public void testNoRestartIfProcessFailsTake2() throws Exception {
        File outputDir = getNewOutputDir("nostart-norestart-");
        Map<String, String> args = new HashMap<String,String>();
        args.put("-numConsumers", "1");
        args.put("-maxQueueSize", "10");
        args.put("-somethingOrOther", "I don't Know");

        String[] commandLine = getDefaultCommandLineArgsArr("basic", outputDir, args);
        BatchProcessDriverCLI driver = getNewDriver("/tika-batch-config-test.xml", commandLine);
        driver.execute();
        assertEquals(0, outputDir.listFiles().length);
        assertEquals(0, driver.getNumRestarts());
    }


}
