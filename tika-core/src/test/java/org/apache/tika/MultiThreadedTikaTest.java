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

import static org.junit.Assert.assertEquals;

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
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.tika.detect.Detector;
import org.apache.tika.detect.XmlRootExtractor;
import org.apache.tika.exception.TikaException;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.sax.BasicContentHandlerFactory;
import org.apache.tika.sax.RecursiveParserWrapperHandler;
import org.apache.tika.utils.XMLReaderUtils;

public class MultiThreadedTikaTest extends TikaTest {
    //TODO: figure out how to make failures reproducible a la Lucene/Solr with a seed
    //TODO: Consider randomizing the Locale and timezone, like Lucene/Solr...
    XmlRootExtractor ex = new XmlRootExtractor();

    public static Path[] getTestFiles(final FileFilter fileFilter)
            throws URISyntaxException, IOException {
        Path root = Paths.get(MultiThreadedTikaTest.class.getResource("/test-documents").toURI());
        final List<Path> files = new ArrayList<>();
        Files.walkFileTree(root, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs)
                    throws IOException {
                if (fileFilter != null && !fileFilter.accept(file.toFile())) {
                    return FileVisitResult.CONTINUE;
                }
                if (!attrs.isDirectory()) {
                    files.add(file);
                }
                return FileVisitResult.CONTINUE;
            }
        });
        return files.toArray(new Path[0]);
    }

    private static ConcurrentHashMap<Path, MediaType> getBaselineDetection(Detector detector,
                                                                           Path[] files) {

        ConcurrentHashMap<Path, MediaType> baseline = new ConcurrentHashMap<>();
        XmlRootExtractor extractor = new XmlRootExtractor();
        for (Path f : files) {
            Metadata metadata = new Metadata();
            try (TikaInputStream tis = TikaInputStream.get(f, metadata)) {
                baseline.put(f, detector.detect(tis, metadata));
                baseline.put(f, detector.detect(tis, metadata));
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return baseline;
    }

    private static ConcurrentHashMap<Path, Extract> getBaseline(Parser parser, Path[] files,
                                                                ParseContext parseContext) {
        ConcurrentHashMap<Path, Extract> baseline = new ConcurrentHashMap<>();

        for (Path f : files) {
            try (TikaInputStream is = TikaInputStream.get(f)) {

                List<Metadata> metadataList = getRecursiveMetadata(is, parser, parseContext);
                baseline.put(f, new Extract(metadataList));

            } catch (Exception e) {
                e.printStackTrace();
                //swallow
            }
        }
        return baseline;
    }

    private static List<Metadata> getRecursiveMetadata(InputStream is, Parser parser,
                                                       ParseContext parseContext) throws Exception {
        //different from parent TikaTest in that this extracts text.
        //can't extract xhtml because "tmp" file names wind up in
        //content's metadata and they'll differ by file.
        parseContext = new ParseContext();
        RecursiveParserWrapperHandler handler = new RecursiveParserWrapperHandler(
                new BasicContentHandlerFactory(BasicContentHandlerFactory.HANDLER_TYPE.TEXT, -1),
                -1);
        parser.parse(is, handler, new Metadata(), parseContext);
        return handler.getMetadataList();
    }

    private static void assertExtractEquals(Extract extractA, Extract extractB) {
        //this currently only checks the basics
        //might want to add more checks

        assertEquals("number of embedded files", extractA.metadataList.size(),
                extractB.metadataList.size());

        for (int i = 0; i < extractA.metadataList.size(); i++) {
            assertEquals("number of metadata elements in attachment: " + i,
                    extractA.metadataList.get(i).size(), extractB.metadataList.get(i).size());

            assertEquals("content in attachment: " + i,
                    extractA.metadataList.get(i).get(TikaCoreProperties.TIKA_CONTENT),
                    extractB.metadataList.get(i).get(TikaCoreProperties.TIKA_CONTENT));
        }
    }

    /**
     * This calls {@link #testEach(Parser parser, Path[], ParseContext[], int, int)} and
     * then {@link #testAll(Parser parser, Path[], ParseContext[], int, int)}
     *
     * @param numThreads    number of threads to use
     * @param numIterations number of iterations per thread
     * @param filter        file filter to select files from "/test-documents"; if
     *                      <code>null</code>,
     *                      all files will be used
     * @throws Exception
     */
    protected void testMultiThreaded(Parser parser, ParseContext[] parseContext, int numThreads,
                                     int numIterations, FileFilter filter) throws Exception {
        Path[] allFiles = getTestFiles(filter);
        testEach(parser, allFiles, parseContext, numThreads, numIterations);
        testAll(parser, allFiles, parseContext, numThreads, numIterations);
    }

    public void testDetector(Detector detector, int numThreads, int numIterations,
                             FileFilter filter, int randomlyResizeSAXPool) throws Exception {
        Path[] files = getTestFiles(filter);
        testDetectorEach(detector, files, numThreads, numIterations, randomlyResizeSAXPool);
        testDetectorOnAll(detector, files, numThreads, numIterations, randomlyResizeSAXPool);
    }

    void testDetectorEach(Detector detector, Path[] files, int numThreads, int numIterations,
                          int randomlyResizeSAXPool) {
        for (Path p : files) {
            Path[] toTest = new Path[1];
            toTest[0] = p;
            testDetectorOnAll(detector, toTest, numThreads, numIterations, randomlyResizeSAXPool);
        }
    }

    private void testDetectorOnAll(Detector detector, Path[] toTest, int numThreads,
                                   int numIterations, int randomlyResizeSAXPool) {
        Map<Path, MediaType> truth = getBaselineDetection(detector, toTest);
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
        int actualThreadCount = numThreads + Math.max(randomlyResizeSAXPool, 0);
        ExecutorService ex = Executors.newFixedThreadPool(actualThreadCount);
        try {
            _testDetectorOnAll(detector, testFiles, numThreads, numIterations, truth, ex,
                    randomlyResizeSAXPool);
        } finally {
            ex.shutdown();
            ex.shutdownNow();
        }
    }

    private void _testDetectorOnAll(Detector detector, Path[] testFiles, int numThreads,
                                    int numIterations, Map<Path, MediaType> truth,
                                    ExecutorService ex, int randomlyResizeSAXPool) {
        ExecutorCompletionService<Integer> executorCompletionService =
                new ExecutorCompletionService<>(ex);

        executorCompletionService.submit(new SAXPoolResizer(randomlyResizeSAXPool));
        for (int i = 0; i < numThreads; i++) {
            executorCompletionService
                    .submit(new TikaDetectorRunner(detector, numIterations, testFiles, truth));
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
            } catch (InterruptedException | ExecutionException e) {
                throw new RuntimeException(e);
            }
        }
        ex.shutdown();
        ex.shutdownNow();
    }

    /**
     * Test each file, one at a time in multiple threads.
     * This was required to test TIKA-2519 in a reasonable
     * amount of time.  This forced the parser to use the
     * same underlying memory structures because it was the same file.
     * This is stricter than I think our agreement with clients is
     * because this run tests on literally the same file and
     * not a copy of the file per thread.  Let's leave this as is
     * unless there's a good reason to create a separate copy per thread.
     *
     * @param files         files to test, one at a time
     * @param numThreads    number of threads to use
     * @param numIterations number of iterations per thread
     */
    protected void testEach(Parser parser, Path[] files, ParseContext[] parseContext,
                            int numThreads, int numIterations) {
        for (Path p : files) {
            Path[] toTest = new Path[1];
            toTest[0] = p;
            testAll(parser, toTest, parseContext, numThreads, numIterations);
        }
    }

    /**
     * This tests all files together.  Each parser randomly selects
     * a file from the array.  Two parsers could wind up parsing the
     * same file at the same time.  Good.
     * <p>
     * In the current implementation, this gets ground truth only
     * from files that do not throw exceptions.  This will ignore
     * files that cause exceptions.
     *
     * @param files         files to parse
     * @param numThreads    number of parser threads
     * @param numIterations number of iterations per parser
     */
    protected void testAll(Parser parser, Path[] files, ParseContext[] parseContext, int numThreads,
                           int numIterations) {

        Map<Path, Extract> truth = getBaseline(parser, files, parseContext[0]);
        //if all files caused an exception
        if (truth.size() == 0) {
            //return;
        }
        //only those that parsed without exception
        Path[] testFiles = new Path[truth.size()];
        int j = 0;
        for (Path testFile : truth.keySet()) {
            testFiles[j++] = testFile;
        }

        ExecutorService ex = Executors.newFixedThreadPool(numThreads);
        try {
            _testAll(parser, files, parseContext, numThreads, numIterations, truth, ex);
        } finally {
            ex.shutdown();
            ex.shutdownNow();
        }
    }

    private void _testAll(Parser parser, Path[] testFiles, ParseContext[] parseContext,
                          int numThreads, int numIterations, Map<Path, Extract> truth,
                          ExecutorService ex) {

        ExecutorCompletionService<Integer> executorCompletionService =
                new ExecutorCompletionService<>(ex);

        //use the same parser in all threads
        for (int i = 0; i < numThreads; i++) {
            executorCompletionService
                    .submit(new TikaRunner(parser, parseContext[i], numIterations, testFiles,
                            truth));
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
            } catch (InterruptedException | ExecutionException e) {
                throw new RuntimeException(e);
            }
        }
    }

    //TODO: make this return something useful besides an integer
    private static class TikaRunner implements Callable<Integer> {
        private static final AtomicInteger threadCount = new AtomicInteger(0);
        private final Parser parser;
        private final int iterations;
        private final Path[] files;
        private final Map<Path, Extract> truth;
        private final ParseContext parseContext;
        private final Random random = new Random();
        private final int threadNumber;

        private TikaRunner(Parser parser, ParseContext parseContext, int iterations, Path[] files,
                           Map<Path, Extract> truth) {
            this.parser = parser;
            this.iterations = iterations;
            this.files = files;
            this.truth = truth;
            this.parseContext = parseContext;
            threadNumber = threadCount.getAndIncrement();
        }

        @Override
        public Integer call() throws Exception {
            for (int i = 0; i < iterations; i++) {
                int randIndex = random.nextInt(files.length);
                Path testFile = files[randIndex];
                List<Metadata> metadataList = null;
                boolean success = false;
                try (InputStream is = Files.newInputStream(testFile)) {
                    metadataList = getRecursiveMetadata(is, parser, new ParseContext());
                    success = true;
                } catch (Exception e) {
                    //swallow
                    //throw new RuntimeException(testFile + " triggered this exception", e);
                }
                if (success) {
                    assertExtractEquals(truth.get(testFile), new Extract(metadataList));
                }
            }
            return 1;
        }

    }

    private static class Extract {
        final List<Metadata> metadataList;

        private Extract(List<Metadata> metadataList) {
            this.metadataList = metadataList;
        }
    }

    private class SAXPoolResizer implements Callable<Integer> {
        private final int maxResize;
        private final Random rand = new Random();

        SAXPoolResizer(int maxResize) {
            this.maxResize = maxResize;
        }

        public Integer call() throws TikaException {
            int resized = 0;
            while (true) {
                try {
                    Thread.yield();
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    return resized;
                }
                if (maxResize > 0 && rand.nextFloat() > 0.01) {
                    int sz = rand.nextInt(maxResize) + 1;
                    XMLReaderUtils.setPoolSize(sz);
                    resized++;
                }
            }
        }
    }

    private class TikaDetectorRunner implements Callable<Integer> {
        private final Detector detector;
        private final int iterations;
        private final Path[] files;
        private final Map<Path, MediaType> truth;
        private final Random random = new Random();

        private TikaDetectorRunner(Detector detector, int iterations, Path[] files,
                                   Map<Path, MediaType> truth) {
            this.detector = detector;
            this.iterations = iterations;
            this.files = files;
            this.truth = truth;
        }

        @Override
        public Integer call() throws Exception {
            for (int i = 0; i < iterations; i++) {
                int randIndex = random.nextInt(files.length);
                Path testFile = files[randIndex];
                Metadata metadata = new Metadata();
                try (TikaInputStream tis = TikaInputStream.get(testFile, metadata)) {
                    MediaType mediaType = detector.detect(tis, metadata);
                    assertEquals("failed on: " + testFile.getFileName(), truth.get(testFile),
                            mediaType);
                }
            }
            return 1;
        }

    }
}
