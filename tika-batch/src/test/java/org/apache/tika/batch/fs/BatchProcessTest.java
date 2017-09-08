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


import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;

import org.apache.tika.batch.BatchProcess;
import org.apache.tika.batch.BatchProcessDriverCLI;
import org.junit.Test;

public class BatchProcessTest extends FSBatchTestBase {

    @Test(timeout = 15000)
    public void oneHeavyHangTest() throws Exception {

        Path outputDir = getNewOutputDir("one_heavy_hang-");

        Map<String, String> args = getDefaultArgs("one_heavy_hang", outputDir);
        BatchProcessTestExecutor ex = new BatchProcessTestExecutor(args);
        StreamStrings streamStrings = ex.execute();
        assertEquals(5, countChildren(outputDir));
        Path hvyHang = outputDir.resolve("test0_heavy_hang.xml.xml");
        assertTrue(Files.exists(hvyHang));
        assertEquals(0, Files.size(hvyHang));
        assertNotContained(BatchProcess.BATCH_CONSTANTS.BATCH_PROCESS_FATAL_MUST_RESTART.toString(),
                streamStrings.getErrString());
    }


    @Test(timeout = 15000)
    public void allHeavyHangsTest() throws Exception {
        //each of the three threads hits a heavy hang.  The BatchProcess runs into
        //all timedouts and shuts down.
        Path outputDir = getNewOutputDir("allHeavyHangs-");
        Map<String, String> args = getDefaultArgs("heavy_heavy_hangs", outputDir);
        BatchProcessTestExecutor ex = new BatchProcessTestExecutor(args);
        StreamStrings streamStrings = ex.execute();

        assertEquals(3, countChildren(outputDir));
        for (Path hvyHang : listPaths(outputDir)){
            assertTrue(Files.exists(hvyHang));
            assertEquals("file length for "+hvyHang.getFileName()+" should be 0, but is: " +
                            Files.size(hvyHang),
                    0, Files.size(hvyHang));
        }
        assertContains(BatchProcess.BATCH_CONSTANTS.BATCH_PROCESS_FATAL_MUST_RESTART.toString(),
                streamStrings.getErrString());
    }

    @Test(timeout = 30000)
    public void allHeavyHangsTestWithCrazyNumberConsumersTest() throws Exception {
        Path outputDir = getNewOutputDir("allHeavyHangsCrazyNumberConsumers-");
        Map<String, String> args = getDefaultArgs("heavy_heavy_hangs", outputDir);
        args.put("numConsumers", "100");
        BatchProcessTestExecutor ex = new BatchProcessTestExecutor(args);
        StreamStrings streamStrings = ex.execute();
        assertEquals(7, countChildren(outputDir));

        for (int i = 0; i < 6; i++){
            Path hvyHang = outputDir.resolve("test"+i+"_heavy_hang.xml.xml");
            assertTrue(Files.exists(hvyHang));
            assertEquals(0, Files.size(hvyHang));
        }
        assertContains("This is tika-batch's first test file",
                readFileToString(outputDir.resolve("test6_ok.xml.xml"), UTF_8));

        //key that the process realize that there were no more processable files
        //in the queue and does not ask for a restart!
        assertNotContained(BatchProcess.BATCH_CONSTANTS.BATCH_PROCESS_FATAL_MUST_RESTART.toString(),
                streamStrings.getErrString());
    }

    @Test(timeout = 30000)
    public void allHeavyHangsTestWithStarvedCrawler() throws Exception {
        //this tests that if all consumers are hung and the crawler is
        //waiting to add to the queue, there isn't deadlock.  The batchrunner should
        //shutdown and ask to be restarted.
        Path outputDir = getNewOutputDir("allHeavyHangsStarvedCrawler-");
        Map<String, String> args = getDefaultArgs("heavy_heavy_hangs", outputDir);
        args.put("numConsumers", "2");
        args.put("maxQueueSize", "2");
        args.put("timeoutThresholdMillis", "100000000");//make sure that the batch process doesn't time out
        BatchProcessTestExecutor ex = new BatchProcessTestExecutor(args);
        StreamStrings streamStrings = ex.execute();
        assertEquals(2, countChildren(outputDir));

        for (int i = 0; i < 2; i++){
            Path hvyHang = outputDir.resolve("test"+i+"_heavy_hang.xml.xml");
            assertTrue(Files.exists(hvyHang));
            assertEquals(0, Files.size(hvyHang));
        }
        assertContains(BatchProcess.BATCH_CONSTANTS.BATCH_PROCESS_FATAL_MUST_RESTART.toString(),
                streamStrings.getErrString());
        assertContains("Crawler timed out", streamStrings.getErrString());
    }

    @Test(timeout = 15000)
    public void outOfMemory() throws Exception {
        //the first consumer should sleep for 10 seconds
        //the second should be tied up in a heavy hang
        //the third one should hit the oom after processing test2_ok.xml
        //no consumers should process test2-4.txt!
        //i.e. the first consumer will finish in 10 seconds and
        //then otherwise would be looking for more, but the oom should prevent that
        Path outputDir = getNewOutputDir("oom-");

        Map<String, String> args = getDefaultArgs("oom", outputDir);
        args.put("numConsumers", "3");
        args.put("timeoutThresholdMillis", "30000");

        BatchProcessTestExecutor ex = new BatchProcessTestExecutor(args);
        StreamStrings streamStrings = ex.execute();

        assertEquals(4, countChildren(outputDir));
        assertContains("This is tika-batch's first test file",
                readFileToString(outputDir.resolve("test2_ok.xml.xml"), UTF_8));

        assertContains(BatchProcess.BATCH_CONSTANTS.BATCH_PROCESS_FATAL_MUST_RESTART.toString(),
                streamStrings.getErrString());
    }



    @Test(timeout = 15000)
    public void noRestart() throws Exception {
        Path outputDir = getNewOutputDir("no_restart");

        Map<String, String> args = getDefaultArgs("no_restart", outputDir);
        args.put("numConsumers", "1");

        BatchProcessTestExecutor ex = new BatchProcessTestExecutor(args);

        StreamStrings streamStrings = ex.execute();

        Path test2 = outputDir.resolve("test2_norestart.xml.xml");
        assertTrue("test2_norestart.xml", Files.exists(test2));
        Path test3 = outputDir.resolve("test3_ok.xml.xml");
        assertFalse("test3_ok.xml", Files.exists(test3));
        assertContains("exitStatus="+ BatchProcessDriverCLI.PROCESS_NO_RESTART_EXIT_CODE,
                streamStrings.getOutString());
        assertContains("causeForTermination='MAIN_LOOP_EXCEPTION_NO_RESTART'",
                streamStrings.getOutString());
    }

    /**
     * This tests to make sure that BatchProcess waits the appropriate
     * amount of time on an early termination before stopping.
     *
     * If this fails, then interruptible parsers (e.g. those with
     * nio channels) will be interrupted and there will be corrupted data.
     */
    @Test(timeout = 60000)
    public void testWaitAfterEarlyTermination() throws Exception {
        Path outputDir = getNewOutputDir("wait_after_early_termination");

        Map<String, String> args = getDefaultArgs("wait_after_early_termination", outputDir);
        args.put("numConsumers", "1");
        args.put("maxAliveTimeSeconds", "5");//main process loop should stop after 5 seconds
        args.put("timeoutThresholdMillis", "300000");//effectively never
        args.put("pauseOnEarlyTerminationMillis", "20000");//let the parser have up to 20 seconds

        BatchProcessTestExecutor ex = new BatchProcessTestExecutor(args);

        StreamStrings streamStrings = ex.execute();
        assertEquals(1, countChildren(outputDir));
        assertContains("<p>some content</p>",
                readFileToString(outputDir.resolve("test0_sleep.xml.xml"), UTF_8));

        assertContains("exitStatus="+BatchProcessDriverCLI.PROCESS_RESTART_EXIT_CODE,
                streamStrings.getOutString());
        assertContains("causeForTermination='BATCH_PROCESS_ALIVE_TOO_LONG'",
                streamStrings.getOutString());
    }

    @Test(timeout = 60000)
    public void testTimeOutAfterBeingAskedToShutdown() throws Exception {
        Path outputDir = getNewOutputDir("timeout_after_early_termination");

        Map<String, String> args = getDefaultArgs("timeout_after_early_termination", outputDir);
        args.put("numConsumers", "1");
        args.put("maxAliveTimeSeconds", "5");//main process loop should stop after 5 seconds
        args.put("timeoutThresholdMillis", "10000");
        args.put("pauseOnEarlyTerminationMillis", "20000");//let the parser have up to 20 seconds

        BatchProcessTestExecutor ex = new BatchProcessTestExecutor(args);
        StreamStrings streamStrings = ex.execute();
        List<Path> paths = listPaths(outputDir);
        assertEquals(1, paths.size());
        assertEquals(0, Files.size(paths.get(0)));
        assertContains("exitStatus="+BatchProcessDriverCLI.PROCESS_RESTART_EXIT_CODE, streamStrings.getOutString());
        assertContains("causeForTermination='BATCH_PROCESS_ALIVE_TOO_LONG'",
                streamStrings.getOutString());
    }

    @Test(timeout = 10000)
    public void testRedirectionOfStreams() throws Exception {
        //test redirection of system.err to system.out
        Path outputDir = getNewOutputDir("noisy_parsers");

        Map<String, String> args = getDefaultArgs("noisy_parsers", outputDir);
        args.put("numConsumers", "1");
        args.put("maxAliveTimeSeconds", "20");//main process loop should stop after 5 seconds
        String stderr = "writing something to System.err";
        BatchProcessTestExecutor ex = new BatchProcessTestExecutor(args);
        StreamStrings streamStrings = ex.execute();
        assertEquals(1, countChildren(outputDir));
        assertContains("System.out", streamStrings.getOutString());
        assertContains(stderr, streamStrings.getOutString());
        assertNotContained(stderr, streamStrings.getErrString());
    }

    @Test(timeout = 10000)
    public void testConsumersManagerInitHang() throws Exception {
        Path outputDir = getNewOutputDir("init_hang");

        Map<String, String> args = getDefaultArgs("noisy_parsers", outputDir);
        args.put("numConsumers", "1");
        args.put("hangOnInit", "true");
        BatchProcessTestExecutor ex = new BatchProcessTestExecutor(args, "/tika-batch-config-MockConsumersBuilder.xml");
        StreamStrings streamStrings = ex.execute();
        assertEquals(BatchProcessDriverCLI.PROCESS_NO_RESTART_EXIT_CODE, ex.getExitValue());
        assertContains("causeForTermination='CONSUMERS_MANAGER_DIDNT_INIT_IN_TIME_NO_RESTART'", streamStrings.getOutString());
    }

    @Test(timeout = 10000)
    public void testConsumersManagerShutdownHang() throws Exception {
        Path outputDir = getNewOutputDir("shutdown_hang");

        Map<String, String> args = getDefaultArgs("noisy_parsers", outputDir);
        args.put("numConsumers", "1");
        args.put("hangOnShutdown", "true");

        BatchProcessTestExecutor ex = new BatchProcessTestExecutor(args, "/tika-batch-config-MockConsumersBuilder.xml");
        StreamStrings streamStrings = ex.execute();
        assertEquals(BatchProcessDriverCLI.PROCESS_NO_RESTART_EXIT_CODE, ex.getExitValue());
        assertContains("ConsumersManager did not shutdown within", streamStrings.getOutString());
    }

    @Test
    public void testHierarchicalWFileList() throws Exception {
        //tests to make sure that hierarchy is maintained when reading from
        //file list
        //also tests that list actually works.
        Path outputDir = getNewOutputDir("hierarchical_file_list");

        Map<String, String> args = getDefaultArgs("hierarchical", outputDir);
        args.put("numConsumers", "1");
        args.put("fileList",
                Paths.get(this.getClass().getResource("/testFileList.txt").toURI()).toString());
        args.put("recursiveParserWrapper", "true");
        args.put("basicHandlerType", "text");
        BatchProcessTestExecutor ex = new BatchProcessTestExecutor(args, "/tika-batch-config-MockConsumersBuilder.xml");
        ex.execute();
        Path test1 = outputDir.resolve("test1.xml.json");
        Path test2 = outputDir.resolve("sub1a/test2.xml.json");
        Path test3 = outputDir.resolve("sub1a/sub2a/test3.xml.json");
        assertTrue("test1 exists", Files.exists(test1));
        assertTrue("test1 length > 10", Files.size(test1) > 10);
        assertTrue(Files.exists(test3) && Files.size(test3) > 10);
        Path test2Dir = outputDir.resolve("sub1a");
        //should be just the subdirectory, no actual test2 file
        assertEquals(1, countChildren(test2Dir));
        assertFalse(Files.exists(test2));
    }

    @Test
    public void testHandlingOfIllegalXMLCharsInException() throws Exception {
        //tests to make sure that hierarchy is maintained when reading from
        //file list
        //also tests that list actually works.
        Path outputDir = getNewOutputDir("illegal_xml_chars_in_exception");

        Map<String, String> args = getDefaultArgs("illegal_xml_chars_in_exception", outputDir);
        args.put("numConsumers", "1");
        args.put("recursiveParserWrapper", "true");
        args.put("basicHandlerType", "text");

        BatchProcessTestExecutor ex = new BatchProcessTestExecutor(args,
                "/tika-batch-config-MockConsumersBuilder.xml",
                "/log4j-on.properties");
        StreamStrings ss = ex.execute();
        assertFalse(ss.getOutString().contains("error writing xml stream for"));
        assertContains("parse_ex resourceId=\"test0_bad_chars.xml\"", ss.getOutString());
    }

    @Test
    public void testOverrideOutputSuffix() throws Exception {
        Path outputDir = getNewOutputDir("outputSuffixTest");

        Map<String, String> args = getDefaultArgs("basic", outputDir);
        args.put("numConsumers", "1");
        args.put("recursiveParserWrapper", "true");
        args.put("basicHandlerType", "text");

        BatchProcessTestExecutor ex = new BatchProcessTestExecutor(args,
                "/tika-batch-config-test-suffix-override.xml",
                "/log4j-on.properties");
        ex.execute();
        Path targ = outputDir.resolve("test0.xml.mysuffix");
        assertTrue(Files.isRegularFile(targ));
    }

    private class BatchProcessTestExecutor {
        private final Map<String, String> args;
        private final String configPath;
        private final String loggerProps;
        private int exitValue = Integer.MIN_VALUE;

        public BatchProcessTestExecutor(Map<String, String> args) {
            this(args, "/tika-batch-config-test.xml");
        }



        public BatchProcessTestExecutor(Map<String, String> args, String configPath) {
            this(args, configPath, "/log4j_process.properties");
        }

        public BatchProcessTestExecutor(Map<String, String> args, String configPath, String loggerProps) {
            this.args = args;
            this.configPath = configPath;
            this.loggerProps = loggerProps;
        }

        private StreamStrings execute() {
            Process p = null;
            try {
                ProcessBuilder b = getNewBatchRunnerProcess(configPath, loggerProps, args);
                p = b.start();
                StringStreamGobbler errorGobbler = new StringStreamGobbler(p.getErrorStream());
                StringStreamGobbler outGobbler = new StringStreamGobbler(p.getInputStream());
                Thread errorThread = new Thread(errorGobbler);
                Thread outThread = new Thread(outGobbler);
                errorThread.start();
                outThread.start();
                while (true) {
                    try {
                        exitValue = p.exitValue();
                        break;
                    } catch (IllegalThreadStateException e) {
                        //still going;
                    }
                }
                errorGobbler.stopGobblingAndDie();
                outGobbler.stopGobblingAndDie();
                errorThread.interrupt();
                outThread.interrupt();
                return new StreamStrings(outGobbler.toString(), errorGobbler.toString());
            } catch (IOException e) {
                fail();
            } finally {
                destroyProcess(p);
            }
            return null;
        }

        private int getExitValue() {
            return exitValue;
        }

    }

    private class StreamStrings {
        private final String outString;
        private final String errString;

        private StreamStrings(String outString, String errString) {
            this.outString = outString;
            this.errString = errString;
        }

        private String getOutString() {
            return outString;
        }

        private String getErrString() {
            return errString;
        }

        @Override
        public String toString() {
            return "OUT>>"+outString+"<<\n"+
                    "ERR>>"+errString+"<<\n";
        }
    }
}
