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

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.apache.commons.io.FileUtils;
import org.apache.tika.TikaTest;
import org.apache.tika.batch.BatchProcess;
import org.apache.tika.batch.BatchProcessDriverCLI;
import org.apache.tika.batch.ParallelFileProcessingResult;
import org.apache.tika.batch.builders.BatchProcessBuilder;
import org.apache.tika.io.IOUtils;
import org.junit.AfterClass;
import org.junit.BeforeClass;

/**
 * This is the base class for file-system batch tests.
 * <p/>
 * There are a few areas for improvement in this test suite.
 * <ol>
 *     <li>For the heavy load tests, the test cases leave behind files that
 *     cannot be deleted from within the same jvm.  A thread is still actively writing to an
 *     OutputStream when tearDown() is called.  The current solution is to create
 *     the temp dir within the target/tika-batch/test-classes so that they will at least
 *     be removed during each maven &quot;clean&quot;</li>
 *     <li>The &quot;mock&quot; tests are time-based.  This is not
 *     extremely reliable across different machines with different number/power of cpus.
 *     </li>
 * </ol>
 */
public abstract class FSBatchTestBase extends TikaTest {

    private static File outputRoot = null;

    @BeforeClass
    public static void setUp() throws Exception {

        File testOutput = new File("target/test-classes/test-output");
        testOutput.mkdirs();
        outputRoot = File.createTempFile("tika-batch-output-root-", "", testOutput);
        outputRoot.delete();
        outputRoot.mkdirs();

    }

    @AfterClass
    public static void tearDown() throws Exception {
        //not ideal, but should be ok for testing
        //see caveat in TikaCLITest's textExtract

        try {
            FileUtils.deleteDirectory(outputRoot);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    protected void destroyProcess(Process p) {
        if (p == null)
            return;

        try {
            p.exitValue();
        } catch (IllegalThreadStateException e) {
            p.destroy();
        }
    }

    File getNewOutputDir(String subdirPrefix) throws IOException {
        File outputDir = File.createTempFile(subdirPrefix, "", outputRoot);
        outputDir.delete();
        outputDir.mkdirs();
        return outputDir;
    }

    Map<String, String> getDefaultArgs(String inputSubDir, File outputDir) throws Exception {
        Map<String, String> args = new HashMap<String, String>();
        args.put("inputDir", "\""+getInputRoot(inputSubDir).getAbsolutePath()+"\"");
        if (outputDir != null) {
            args.put("outputDir", "\""+outputDir.getAbsolutePath()+"\"");
        }
        return args;
    }

    public String[] getDefaultCommandLineArgsArr(String inputSubDir, File outputDir, Map<String, String> commandLine) throws Exception {
        List<String> args = new ArrayList<String>();
        //need to include "-" because these are going to the commandline!
        if (inputSubDir != null) {
            args.add("-inputDir");
            args.add(getInputRoot(inputSubDir).getAbsolutePath());
        }
        if (outputDir != null) {
            args.add("-outputDir");
            args.add(outputDir.getAbsolutePath());
        }
        if (commandLine != null) {
            for (Map.Entry<String, String> e : commandLine.entrySet()) {
                args.add(e.getKey());
                args.add(e.getValue());
            }
        }
        return args.toArray(new String[args.size()]);
    }


    public File getInputRoot(String subdir) throws Exception {
        String path = (subdir == null || subdir.length() == 0) ? "/test-input" : "/test-input/"+subdir;
        return new File(this.getClass().getResource(path).toURI());
    }

    BatchProcess getNewBatchRunner(String testConfig,
                                  Map<String, String> args) throws IOException {
        InputStream is = this.getClass().getResourceAsStream(testConfig);
        BatchProcessBuilder b = new BatchProcessBuilder();
        BatchProcess runner = b.build(is, args);

        IOUtils.closeQuietly(is);
        return runner;
    }

    public ProcessBuilder getNewBatchRunnerProcess(String testConfig, Map<String, String> args) {
        List<String> argList = new ArrayList<String>();
        for (Map.Entry<String, String> e : args.entrySet()) {
            argList.add("-"+e.getKey());
            argList.add(e.getValue());
        }

        String[] fullCommandLine = commandLine(testConfig, argList.toArray(new String[argList.size()]));
        return new ProcessBuilder(fullCommandLine);
    }

    private String[] commandLine(String testConfig, String[] args) {
        List<String> commandLine = new ArrayList<String>();
        commandLine.add("java");
        commandLine.add("-Dlog4j.configuration=file:"+
            this.getClass().getResource("/log4j_process.properties").getFile());
        commandLine.add("-Xmx128m");
        commandLine.add("-cp");
        String cp = System.getProperty("java.class.path");
        //need to test for " " on *nix, can't just add double quotes
        //across platforms.
        if (cp.contains(" ")){
            cp = "\""+cp+"\"";
        }
        commandLine.add(cp);
        commandLine.add("org.apache.tika.batch.fs.FSBatchProcessCLI");

        String configFile = this.getClass().getResource(testConfig).getFile();
        commandLine.add("-bc");

        commandLine.add(configFile);

        for (String s : args) {
            commandLine.add(s);
        }
        return commandLine.toArray(new String[commandLine.size()]);
    }

    public BatchProcessDriverCLI getNewDriver(String testConfig,
                                              String[] args) throws Exception {
        List<String> commandLine = new ArrayList<String>();
        commandLine.add("java");
        commandLine.add("-Xmx128m");
        commandLine.add("-cp");
        String cp = System.getProperty("java.class.path");
        //need to test for " " on *nix, can't just add double quotes
        //across platforms.
        if (cp.contains(" ")){
            cp = "\""+cp+"\"";
        }
        commandLine.add(cp);
        commandLine.add("org.apache.tika.batch.fs.FSBatchProcessCLI");

        String configFile = this.getClass().getResource(testConfig).getFile();
        commandLine.add("-bc");

        commandLine.add(configFile);

        for (String s : args) {
            commandLine.add(s);
        }

        BatchProcessDriverCLI driver = new BatchProcessDriverCLI(
          commandLine.toArray(new String[commandLine.size()]));
        driver.setRedirectChildProcessToStdOut(false);
        return driver;
    }

    protected ParallelFileProcessingResult run(BatchProcess process) throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        Future<ParallelFileProcessingResult> futureResult = executor.submit(process);
        return futureResult.get(10, TimeUnit.SECONDS);
    }
}
