/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.tika;

import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.parser.RecursiveParserWrapper;
import org.apache.tika.sax.BasicContentHandlerFactory;
import org.junit.Test;
import org.xml.sax.helpers.DefaultHandler;

import java.io.FileFilter;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;

public class MultiThreadedTikaTest extends TikaTest {
    //TODO: figure out how to make failures reproducible a la Lucene/Solr with a seed
    //TODO: Consider randomizing the Locale and timezone, like Lucene/Solr...

    /**
     * This calls {@link #testEach(Path[], int, int)} and then {@link #testAll(Path[], int, int)}
     *
     * @param numThreads number of threads to use
     * @param numIterations number of iterations per thread
     * @param filter file filter to select files from "/test-documents"; if <code>null</code>,
     *               all files will be used
     * @throws Exception
     */
    protected void testMultiThreaded(int numThreads, int numIterations, FileFilter filter) throws Exception {
        Path[] allFiles = getTestFiles(filter);
        //testEach(allFiles, numThreads, numIterations);
        testAll(allFiles, numThreads, numIterations);
    }

    /**
     *  Test each file, one at a time in multiple threads.
     *  This was required to test TIKA-2519 in a reasonable
     *  amount of time.  This forced the parser to use the
     *  same underlying memory structurees because it was the same file.
     *  This is stricter than I think our agreement with clients is
     *  because this run tests on literally the same file and
     *  not a copy of the file per thread.  Let's leave this as is
     *  unless there's a good reason to create a separate copy per thread.
     *
     * @param files files to test, one at a time
     * @param numThreads number of threads to use
     * @param numIterations number of iterations per thread
     */
    protected void testEach(Path[] files, int numThreads, int numIterations) {
        for (Path p : files) {
            Path[] toTest = new Path[1];
            toTest[0] = p;
            testAll(toTest, numThreads, numIterations);
        }
    }

    /**
     * This tests all files together.  Each parser randomly selects
     * a file from the array.  Two parsers could wind up parsing the
     * same file at the same time.  Good.
     *
     * In the current implementation, this gets ground truth only
     * from files that do not throw exceptions.  This will ignore
     * files that cause exceptions.
     *
     * @param files files to parse
     * @param numThreads number of parser threads
     * @param numIterations number of iterations per parser
     */
    protected void testAll(Path[] files, int numThreads, int numIterations) {

        Map<Path, Extract> truth = getBaseline(files);
        //if all files caused an exception
        if (truth.size() == 0) {
            return;
        }
        //only those that parsed without exception
        Path[] testFiles = new Path[truth.size()];
        int j = 0;
        for (Path testFile : truth.keySet()) {
            testFiles[j++] = testFile;
        }

        ExecutorService ex = Executors.newFixedThreadPool(numThreads);
        try {
            _testAll(testFiles, numThreads, numIterations, truth, ex);
        } finally {
            ex.shutdown();
            ex.shutdownNow();
        }
    }

    private void _testAll(Path[] testFiles, int numThreads, int numIterations,
                          Map<Path, Extract> truth, ExecutorService ex) {

        ExecutorCompletionService<Integer> executorCompletionService = new ExecutorCompletionService<>(ex);

        //use the same parser in all threads
        Parser parser = new AutoDetectParser();
        for (int i = 0; i < numThreads; i++) {
            executorCompletionService.submit(new TikaRunner(parser, numIterations, testFiles, truth));
        }

        int completed = 0;
        while (completed < numThreads) {
            //TODO: add a maximum timeout threshold

            Future<Integer> future = null;
            try {
                future = executorCompletionService.poll(1000, TimeUnit.MILLISECONDS);
                if (future != null) {
                    future.get();//trigger exceptions from thread
                    completed++;
                }
            } catch (InterruptedException|ExecutionException e) {
                throw new RuntimeException(e);
            }
        }
    }

    private static Path[] getTestFiles(final FileFilter fileFilter) throws URISyntaxException, IOException {
        Path root = Paths.get(
                MultiThreadedTikaTest.class.getResource("/test-documents").toURI());
        final List<Path> files = new ArrayList<>();
        Files.walkFileTree(root, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                if (fileFilter != null && ! fileFilter.accept(file.toFile())) {
                    return FileVisitResult.CONTINUE;
                }
                if (!attrs.isDirectory()) {
                    if (files.size() < 20) {
                        files.add(file);
                    }
                }
                return FileVisitResult.CONTINUE;
            }
        });
        return files.toArray(new Path[files.size()]);
    }

    private static ConcurrentHashMap<Path, Extract> getBaseline(Path[] files) {
        ConcurrentHashMap<Path, Extract> baseline = new ConcurrentHashMap<>();
        for (Path f : files) {

            try {
                Parser p = new AutoDetectParser();
                RecursiveParserWrapper wrapper = new RecursiveParserWrapper(p,
                        new BasicContentHandlerFactory(BasicContentHandlerFactory.HANDLER_TYPE.TEXT, -1));
                try (InputStream is = Files.newInputStream(f)) {
                    wrapper.parse(is, new DefaultHandler(), new Metadata(), new ParseContext());
                }
                List<Metadata> metadataList = wrapper.getMetadata();
                baseline.put(f, new Extract(metadataList));
            } catch (Exception e) {
                //swallow
            }
        }
        return baseline;
    }

    private static List<Metadata> getRecursiveMetadata(InputStream is, Parser p) throws Exception {
        //different from parent TikaTest in that this extracts text.
        //can't extract xhtml because "tmp" file names wind up in
        //content's metadata and they'll differ by file.
        RecursiveParserWrapper wrapper = new RecursiveParserWrapper(p,
                new BasicContentHandlerFactory(BasicContentHandlerFactory.HANDLER_TYPE.TEXT, -1));
        wrapper.parse(is, new DefaultHandler(), new Metadata(), new ParseContext());
        return wrapper.getMetadata();
    }

    //TODO: make this return something useful besides an integer
    private class TikaRunner implements Callable<Integer> {
        private final Parser parser;
        private final int iterations;
        private final Path[] files;
        private final Map<Path, Extract> truth;

        private final Random random = new Random();

        private TikaRunner(Parser parser, int iterations, Path[] files, Map<Path, Extract> truth) {
            this.parser = parser;
            this.iterations = iterations;
            this.files = files;
            this.truth = truth;
        }

        @Override
        public Integer call() throws Exception {
            for (int i = 0; i < iterations; i++) {
                int randIndex = random.nextInt(files.length);
                Path testFile = files[randIndex];
                try (InputStream is = Files.newInputStream(testFile)) {
                    List<Metadata> metadataList = getRecursiveMetadata(is, parser);
                    assertExtractEquals(truth.get(testFile), new Extract(metadataList));
                } catch (Exception e) {
                    throw new RuntimeException(testFile+" triggered this exception", e);
                }
            }
            return 1;
        }

    }

    private void assertExtractEquals(Extract extractA, Extract extractB) {
        //this currently only checks the basics
        //might want to add more checks

        assertEquals("number of embedded files", extractA.metadataList.size(),
                extractB.metadataList.size());

        for (int i = 0; i < extractA.metadataList.size(); i++) {
            assertEquals("number of metadata elements in attachment: " + i,
                    extractA.metadataList.get(i).size(), extractB.metadataList.get(i).size());

            assertEquals("content in attachment: " + i,
                    extractA.metadataList.get(i).get(RecursiveParserWrapper.TIKA_CONTENT),
                    extractB.metadataList.get(i).get(RecursiveParserWrapper.TIKA_CONTENT));
        }
    }

    private static class Extract {
        final List<Metadata> metadataList;

        private Extract(List<Metadata> metadataList) {
            this.metadataList = metadataList;
        }
    }
}
