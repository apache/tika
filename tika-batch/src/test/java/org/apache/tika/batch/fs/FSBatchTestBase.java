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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.nio.charset.Charset;
import java.nio.file.DirectoryStream;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.tika.TikaTest;
import org.apache.tika.batch.BatchProcess;
import org.apache.tika.batch.BatchProcessDriverCLI;
import org.apache.tika.batch.ParallelFileProcessingResult;
import org.apache.tika.batch.builders.BatchProcessBuilder;
import org.apache.tika.utils.ProcessUtils;
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

    private static Path outputRoot = null;

    @BeforeClass
    public static void setUp() throws Exception {
        Path testOutput = Paths.get("target/test-classes/test-output");
        Files.createDirectories(testOutput);
        outputRoot = Files.createTempDirectory(testOutput, "tika-batch-output-root-");
    }

    @AfterClass
    public static void tearDown() throws Exception {
        //not ideal, but should be ok for testing
        //see caveat in TikaCLITest's textExtract

        try {

            FileUtils.deleteDirectory(outputRoot.toFile());
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

    Path getNewOutputDir(String subdirPrefix) throws IOException {
        Path outputDir = Files.createTempDirectory(outputRoot, subdirPrefix);
        assert(countChildren(outputDir) == 0);
        return outputDir;
    }

    Map<String, String> getDefaultArgs(String inputSubDir, Path outputDir) throws Exception {
        Map<String, String> args = new HashMap<>();

        args.put("inputDir", "\""+getInputRoot(inputSubDir).toString()+"\"");
        if (outputDir != null) {
            args.put("outputDir", "\""+outputDir.toString()+"\"");
        }
        return args;
    }

    public String[] getDefaultCommandLineArgsArr(String inputSubDir,
                                                 Path outputDir, Map<String, String> commandLine) throws Exception {
        List<String> args = new ArrayList<>();
        //need to include "-" because these are going to the commandline!
        if (inputSubDir != null) {
            args.add("-inputDir");
            args.add(getInputRoot(inputSubDir).toAbsolutePath().toString());
        }
        if (outputDir != null) {
            args.add("-outputDir");
            args.add(outputDir.toAbsolutePath().toString());
        }
        if (commandLine != null) {
            for (Map.Entry<String, String> e : commandLine.entrySet()) {
                args.add(e.getKey());
                args.add(e.getValue());
            }
        }
        return args.toArray(new String[args.size()]);
    }


    public Path getInputRoot(String subdir) throws Exception {
        String path = (subdir == null || subdir.length() == 0) ? "/test-input" : "/test-input/"+subdir;
        return Paths.get(this.getClass().getResource(path).toURI());
    }

    BatchProcess getNewBatchRunner(String testConfig,
                                  Map<String, String> args) throws IOException {
        InputStream is = this.getClass().getResourceAsStream(testConfig);
        BatchProcessBuilder b = new BatchProcessBuilder();
        BatchProcess runner = b.build(is, args);

        IOUtils.closeQuietly(is);
        return runner;
    }

    public ProcessBuilder getNewBatchRunnerProcess(String testConfig, String loggerProps,
                                                   Map<String, String> args) {
        List<String> argList = new ArrayList<>();

        for (Map.Entry<String, String> e : args.entrySet()) {
            argList.add("-"+e.getKey());
            argList.add(e.getValue());
        }

        String[] fullCommandLine = commandLine(testConfig, loggerProps,
                argList.toArray(new String[argList.size()]));
        return new ProcessBuilder(fullCommandLine);
    }

    private String[] commandLine(String testConfig, String loggerProps, String[] args) {
        List<String> commandLine = new ArrayList<>();
        commandLine.add("java");
        commandLine.add("-Djava.awt.headless=true");
        commandLine.add("-Dlog4j.configuration=file:"+
            this.getClass().getResource(loggerProps).getFile());
        commandLine.add("-Xmx128m");
        commandLine.add("-cp");
        String cp = System.getProperty("java.class.path");
        cp = ProcessUtils.escapeCommandLine(cp);

        commandLine.add(cp);
        commandLine.add("org.apache.tika.batch.fs.FSBatchProcessCLI");

        String configFile = null;
        try {
            configFile = Paths.get(this.getClass().getResource(testConfig).toURI()).toAbsolutePath().toString();
        } catch (URISyntaxException e) {
            e.printStackTrace();
        }

        commandLine.add("-bc");
        commandLine.add(configFile);

        for (String s : args) {
            commandLine.add(s);
        }
        return commandLine.toArray(new String[commandLine.size()]);
    }

    public BatchProcessDriverCLI getNewDriver(String testConfig,
                                              String[] args) throws Exception {
        List<String> commandLine = new ArrayList<>();
        commandLine.add("java");
        commandLine.add("-Djava.awt.headless=true");
        commandLine.add("-Xmx128m");
        commandLine.add("-cp");
        String cp = System.getProperty("java.class.path");
        //need to test for " " on *nix, can't just add double quotes
        //across platforms.
        cp = ProcessUtils.escapeCommandLine(cp);

        commandLine.add(cp);
        commandLine.add("org.apache.tika.batch.fs.FSBatchProcessCLI");

        String configFile = Paths.get(
                this.getClass().getResource(testConfig).toURI()).toAbsolutePath().toString();
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

    /**
     * Counts immediate children only, does not work recursively
     * @param p
     * @return
     * @throws IOException
     */
    public static int countChildren(Path p) throws IOException {
        int i = 0;
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(p)) {
            Iterator<Path> it = ds.iterator();
            while (it.hasNext()) {
                i++;
                it.next();
            }
        }
        return i;
    }

    //REMOVE THIS AND USE FileUtils, once a java 7 option has been added.
    public static String readFileToString(Path p, Charset cs) throws IOException {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader r = Files.newBufferedReader(p, cs)) {
            String line = r.readLine();
            while (line != null) {
                sb.append(line).append("\n");
                line = r.readLine();
            }
        }
        return sb.toString();
    }

    /**
     * helper method equivalent to File#listFiles()
     * grabs children only, does not walk recursively
     * @param p
     * @return
     */
    public static List<Path> listPaths(Path p) throws IOException {
        List<Path> list = new ArrayList<>();
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(p)) {
            Iterator<Path> it = ds.iterator();
            while (it.hasNext()) {
                list.add(it.next());
            }
        }
        return list;
    }
}
