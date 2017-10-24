package org.apache.tika.batch.fs.strawman;

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
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MarkerFactory;

/**
 * Simple single-threaded class that calls tika-app against every file in a directory.
 *
 * This is exceedingly robust.  One file per process.
 *
 * However, you can use this to compare performance against tika-batch fs code.
 *
 *
 */
public class StrawManTikaAppDriver implements Callable<Integer> {
    private static final Logger LOG = LoggerFactory.getLogger(StrawManTikaAppDriver.class);

    private static AtomicInteger threadCount = new AtomicInteger(0);
    private final int totalThreads;
    private final int threadNum;
    private Path inputRoot = null;
    private Path outputRoot = null;
    private Path fileList = null;
    private String[] args = null;

    public StrawManTikaAppDriver(Path inputRoot, Path outputRoot,
                                 int totalThreads, Path fileList, String[] args) {
        this.inputRoot = inputRoot;
        this.outputRoot = outputRoot;
        this.fileList = fileList;
        this.args = args;
        threadNum = threadCount.getAndIncrement();
        this.totalThreads = totalThreads;
    }


    private class TikaVisitor extends SimpleFileVisitor<Path> {
        private volatile int processed = 0;

        int getProcessed() {
            return processed;
        }
        @Override
        public FileVisitResult visitFile(Path file,
                                         BasicFileAttributes attr) {
            if (totalThreads > 1) {
                int hashCode = file.toAbsolutePath().toString().hashCode();
                if (Math.abs(hashCode % totalThreads) != threadNum) {
                    return FileVisitResult.CONTINUE;
                }
            }
            if (! file.startsWith(inputRoot)) {
                LOG.warn("File ("+file.toAbsolutePath()+
                        ") doesn't start with input root ("+inputRoot.toAbsolutePath()+")");
                return FileVisitResult.CONTINUE;
            }
            Path relPath = inputRoot.relativize(file);
            String suffix = ".txt";
            List<String> commandLine = new ArrayList<>();
            for (String arg : args) {
                commandLine.add(arg);
                if (arg.equals("-J")) {
                    suffix = ".json";
                } else if (arg.contains("-x")) {
                    suffix = ".html";
                }
            }
            String fullPath = file.toAbsolutePath().toString();
            if (fullPath.contains(" ")) {
                fullPath = "\""+fullPath+"\"";
            }
            commandLine.add(fullPath);


            Path outputFile = Paths.get(outputRoot.toAbsolutePath().toString(),
                    relPath.toString() + suffix);
            try {
                Files.createDirectories(outputFile.getParent());
            } catch (IOException e) {
                LOG.error(MarkerFactory.getMarker("FATAL"),
                        "parent directory for {} was not made!", outputFile);
                throw new RuntimeException("couldn't make parent file for " + outputFile);
            }
            ProcessBuilder builder = new ProcessBuilder();
            builder.command(commandLine);
            LOG.info("about to process: {}", file.toAbsolutePath());
            builder.redirectOutput(outputFile.toFile());
            builder.redirectError(ProcessBuilder.Redirect.INHERIT);

            Process proc = null;
            try {
                proc = builder.start();
            } catch (IOException e) {
                LOG.error(e.getMessage(), e);
                return FileVisitResult.CONTINUE;
            }

            boolean finished = false;
            long totalTime = 180000;//3 minutes
            long pulse = 100;
            for (int i = 0; i < totalTime; i += pulse) {
                try {
                    Thread.sleep(pulse);
                } catch (InterruptedException e) {
                    //swallow
                }
                try {
                    int exit = proc.exitValue();
                    finished = true;
                    break;
                } catch (IllegalThreadStateException e) {
                    //swallow
                }
            }
            if (!finished) {
                LOG.warn("Had to kill process working on: {}", file.toAbsolutePath());
                proc.destroy();
            }
            try {
                proc.getOutputStream().flush();
                proc.getOutputStream().close();
            } catch (IOException e) {
                LOG.warn("couldn't close process outputstream", e);
            }
            processed++;
            return FileVisitResult.CONTINUE;
        }

    }



    @Override
    public Integer call() throws Exception {
        long start = new Date().getTime();
        TikaVisitor v = new TikaVisitor();
        if (fileList != null) {
            TikaVisitor tikaVisitor = new TikaVisitor();
            try (BufferedReader reader = Files.newBufferedReader(fileList, StandardCharsets.UTF_8)) {
                String line = reader.readLine();
                while (line != null) {
                    Path inputFile = inputRoot.resolve(line.trim());
                    if (Files.isReadable(inputFile)) {
                        try {
                            tikaVisitor.visitFile(inputFile, Files.readAttributes(inputFile, BasicFileAttributes.class));
                        } catch (IOException e) {
                            LOG.warn("Problem with: "+inputFile, e);
                        }
                    } else {
                        LOG.warn("Not readable: "+inputFile);
                    }
                    line = reader.readLine();
                }
            }
        } else {
            Files.walkFileTree(inputRoot, v);
        }
        int processed = v.getProcessed();
        double elapsedSecs = ((double)new Date().getTime()-(double)start)/(double)1000;
        LOG.info("Finished processing {} files in {} seconds.", processed, elapsedSecs);
        return processed;
    }


    public static String usage() {
        StringBuilder sb = new StringBuilder();
        sb.append("Example usage:\n");
        sb.append("java -cp <CP> org.apache.batch.fs.strawman.StrawManTikaAppDriver ");
        sb.append("<inputDir> <outputDir> <numThreads> ");
        sb.append("java -jar tika-app-X.Xjar <...commandline arguments for tika-app>\n\n");
        return sb.toString();
    }

    public static void main(String[] args) {
        long start = new Date().getTime();
        if (args.length < 6) {
            System.err.println(StrawManTikaAppDriver.usage());
        }
        Path inputDir = Paths.get(args[0]);
        Path outputDir = Paths.get(args[1]);
        int totalThreads = Integer.parseInt(args[2]);
        Path fileList = null;
        if (args.length > 3) {
            fileList = Paths.get(args[3]);
            if (! Files.isReadable(fileList)) {
                fileList = null;
            }
        }

        List<String> commandLine = new ArrayList<>();

        int initialParams = (fileList == null) ? 3 : 4;
        commandLine.addAll(Arrays.asList(args).subList(initialParams, args.length));
        totalThreads = (totalThreads < 1) ? 1 : totalThreads;
        ExecutorService ex = Executors.newFixedThreadPool(totalThreads);
        ExecutorCompletionService<Integer> completionService =
                new ExecutorCompletionService<>(ex);

        for (int i = 0; i < totalThreads; i++) {
            StrawManTikaAppDriver driver =
                    new StrawManTikaAppDriver(inputDir, outputDir, totalThreads, fileList,
                            commandLine.toArray(new String[commandLine.size()]));
            completionService.submit(driver);
        }

        int totalFilesProcessed = 0;
        for (int i = 0; i < totalThreads; i++) {
            try {
                Future<Integer> future = completionService.take();
                if (future != null) {
                    totalFilesProcessed += future.get();
                }
            } catch (InterruptedException | ExecutionException e) {
                LOG.error(e.getMessage(), e);
            }
        }
        double elapsedSeconds = (double)(new Date().getTime() - start) / (double)1000;
        LOG.info("Processed {} in {} seconds", totalFilesProcessed, elapsedSeconds);
        ex.shutdownNow();
    }
}
