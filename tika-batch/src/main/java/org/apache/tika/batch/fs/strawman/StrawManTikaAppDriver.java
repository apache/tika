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

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
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

import org.apache.commons.io.IOUtils;
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

    private static AtomicInteger threadCount = new AtomicInteger(0);
    private final int totalThreads;
    private final int threadNum;
    private Path inputRoot = null;
    private Path outputRoot = null;
    private String[] args = null;
    private Logger logger = LoggerFactory.getLogger(StrawManTikaAppDriver.class);


    public StrawManTikaAppDriver(Path inputRoot, Path outputRoot,
                                 int totalThreads, String[] args) {
        this.inputRoot = inputRoot;
        this.outputRoot = outputRoot;
        this.args = args;
        threadNum = threadCount.getAndIncrement();
        this.totalThreads = totalThreads;
    }


    private class TikaVisitor extends SimpleFileVisitor<Path> {
        private int processed = 0;

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
            assert(file.startsWith(inputRoot));
            Path relPath = inputRoot.relativize(file);
            Path outputFile = Paths.get(outputRoot.toAbsolutePath().toString(),
                    relPath.toString() + ".txt");
            try {
                Files.createDirectories(outputFile.getParent());
            } catch (IOException e) {
                logger.error(MarkerFactory.getMarker("FATAL"),
                        "parent directory for "+ outputFile + " was not made!");
                throw new RuntimeException("couldn't make parent file for " + outputFile);
            }
            List<String> commandLine = new ArrayList<>();
            for (String arg : args) {
                commandLine.add(arg);
            }
            commandLine.add("-t");
            commandLine.add("\""+outputFile.toAbsolutePath()+"\"");
            ProcessBuilder builder = new ProcessBuilder(commandLine.toArray(new String[commandLine.size()]));
            logger.info("about to process: "+file.toAbsolutePath());
            Process proc = null;
            RedirectGobbler gobbler = null;
            Thread gobblerThread = null;
            try {
                OutputStream os = Files.newOutputStream(outputFile);
                proc = builder.start();
                gobbler = new RedirectGobbler(proc.getInputStream(), os);
                gobblerThread = new Thread(gobbler);
                gobblerThread.start();
            } catch (IOException e) {
                logger.error(e.getMessage());
                return FileVisitResult.CONTINUE;
            }

            boolean finished = false;
            long totalTime = 180000;//3 minutes
            long pulse = 100;
            for (int i = 0; i < totalTime; i += pulse) {
                try {
                    Thread.currentThread().sleep(pulse);
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
                logger.warn("Had to kill process working on: " + file.toAbsolutePath());
                proc.destroy();
            }
            gobbler.close();
            gobblerThread.interrupt();
            processed++;
            return FileVisitResult.CONTINUE;
        }

    }



    @Override
    public Integer call() throws Exception {
        long start = new Date().getTime();
        TikaVisitor v = new TikaVisitor();
        Files.walkFileTree(inputRoot, v);
        int processed = v.getProcessed();
        double elapsedSecs = ((double)new Date().getTime()-(double)start)/(double)1000;
        logger.info("Finished processing " + processed + " files in " + elapsedSecs + " seconds.");
        return processed;
    }

    private class RedirectGobbler implements Runnable {
        private OutputStream redirectOs = null;
        private InputStream redirectIs = null;

        private RedirectGobbler(InputStream is, OutputStream os) {
            this.redirectIs = is;
            this.redirectOs = os;
        }

        private void close() {
            if (redirectOs != null) {
                try {
                    redirectOs.flush();
                } catch (IOException e) {
                    logger.error("can't flush");
                }
                try {
                    redirectIs.close();
                } catch (IOException e) {
                    logger.error("can't close input in redirect gobbler");
                }
                try {
                    redirectOs.close();
                } catch (IOException e) {
                    logger.error("can't close output in redirect gobbler");
                }
            }
        }

        @Override
        public void run() {
            try {
                IOUtils.copy(redirectIs, redirectOs);
            } catch (IOException e) {
                logger.error("IOException while gobbling");
            }
        }
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

        List<String> commandLine = new ArrayList<String>();
        commandLine.addAll(Arrays.asList(args).subList(3, args.length));
        totalThreads = (totalThreads < 1) ? 1 : totalThreads;
        ExecutorService ex = Executors.newFixedThreadPool(totalThreads);
        ExecutorCompletionService<Integer> completionService =
                new ExecutorCompletionService<Integer>(ex);

        for (int i = 0; i < totalThreads; i++) {
            StrawManTikaAppDriver driver =
                    new StrawManTikaAppDriver(inputDir, outputDir, totalThreads, commandLine.toArray(new String[commandLine.size()]));
            completionService.submit(driver);
        }

        int totalFilesProcessed = 0;
        for (int i = 0; i < totalThreads; i++) {
            try {
                Future<Integer> future = completionService.take();
                if (future != null) {
                    totalFilesProcessed += future.get();
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
            } catch (ExecutionException e) {
                e.printStackTrace();
            }
        }
        double elapsedSeconds = (double)(new Date().getTime()-start)/(double)1000;
        System.out.println("Processed "+totalFilesProcessed + " in " + elapsedSeconds + " seconds");
    }
}
