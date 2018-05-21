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

import org.apache.tika.detect.Detector;
import org.apache.tika.detect.XmlRootExtractor;
import org.apache.tika.exception.TikaException;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.parser.RecursiveParserWrapper;
import org.apache.tika.sax.AbstractRecursiveParserWrapperHandler;
import org.apache.tika.sax.BasicContentHandlerFactory;
import org.apache.tika.sax.RecursiveParserWrapperHandler;
import org.apache.tika.utils.XMLReaderUtils;

import javax.xml.namespace.QName;
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

import static org.junit.Assert.assertEquals;

public class MultiThreadedTikaTest extends TikaTest {
    //TODO: figure out how to make failures reproducible a la Lucene/Solr with a seed
    //TODO: Consider randomizing the Locale and timezone, like Lucene/Solr...
    XmlRootExtractor ex = new XmlRootExtractor();

    /**
     * This calls {@link #testEach(Path[], ParseContext[], int, int)} and then {@link #testAll(Path[], ParseContext[], int, int)}
     *
     * @param numThreads    number of threads to use
     * @param numIterations number of iterations per thread
     * @param filter        file filter to select files from "/test-documents"; if <code>null</code>,
     *                      all files will be used
     * @throws Exception
     */
    protected void testMultiThreaded(ParseContext[] parseContext, int numThreads, int numIterations, FileFilter filter) throws Exception {
        Path[] allFiles = getTestFiles(filter);
        testEach(allFiles, parseContext, numThreads, numIterations);
        testAll(allFiles, parseContext, numThreads, numIterations);
    }

    public void testDetector(Detector detector, int numThreads, int numIterations, FileFilter filter, int randomlyResizeSAXPool) throws Exception {
        Path[] files = getTestFiles(filter);
        testDetectorEach(detector, files, numThreads, numIterations, randomlyResizeSAXPool);
        testDetectorOnAll(detector, files, numThreads, numIterations, randomlyResizeSAXPool);
    }

    void testDetectorEach(Detector detector, Path[] files, int numThreads, int numIterations, int randomlyResizeSAXPool) {
        for (Path p : files) {
            Path[] toTest = new Path[1];
            toTest[0] = p;
            testDetectorOnAll(detector, toTest, numThreads, numIterations, randomlyResizeSAXPool);
        }
    }

    private void testDetectorOnAll(Detector detector, Path[] toTest, int numThreads, int numIterations, int randomlyResizeSAXPool) {
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
        int actualThreadCount = numThreads + ((randomlyResizeSAXPool > 0) ? randomlyResizeSAXPool : 0);
        ExecutorService ex = Executors.newFixedThreadPool(actualThreadCount);
        try {
            _testDetectorOnAll(detector, testFiles, numThreads, numIterations, truth, ex, randomlyResizeSAXPool);
        } finally {
            ex.shutdown();
            ex.shutdownNow();
        }
    }

    private void _testDetectorOnAll(Detector detector, Path[] testFiles, int numThreads,
                                    int numIterations, Map<Path, MediaType> truth, ExecutorService ex, int randomlyResizeSAXPool) {
        ExecutorCompletionService<Integer> executorCompletionService = new ExecutorCompletionService<>(ex);

        executorCompletionService.submit(new SAXPoolResizer(randomlyResizeSAXPool));
        for (int i = 0; i < numThreads; i++) {
            executorCompletionService.submit(new TikaDetectorRunner(detector, numIterations, testFiles, truth));
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
    protected void testEach(Path[] files, ParseContext[] parseContext, int numThreads, int numIterations) {
        for (Path p : files) {
            Path[] toTest = new Path[1];
            toTest[0] = p;
            testAll(toTest, parseContext, numThreads, numIterations);
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
    protected void testAll(Path[] files, ParseContext[] parseContext, int numThreads, int numIterations) {

        Map<Path, Extract> truth = getBaseline(files, parseContext[0]);
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
            _testAll(testFiles, parseContext, numThreads, numIterations, truth, ex);
        } finally {
            ex.shutdown();
            ex.shutdownNow();
        }
    }

    private void _testAll(Path[] testFiles, ParseContext[] parseContext, int numThreads, int numIterations,
                          Map<Path, Extract> truth, ExecutorService ex) {

        ExecutorCompletionService<Integer> executorCompletionService = new ExecutorCompletionService<>(ex);

        //use the same parser in all threads
        Parser parser = new AutoDetectParser();
        RecursiveParserWrapper wrapper = new RecursiveParserWrapper(parser);
        for (int i = 0; i < numThreads; i++) {
            executorCompletionService.submit(new TikaRunner(wrapper, parseContext[i], numIterations, testFiles, truth));
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

    public static Path[] getTestFiles(final FileFilter fileFilter) throws URISyntaxException, IOException {
        Path root = Paths.get(
                MultiThreadedTikaTest.class.getResource("/test-documents").toURI());
        final List<Path> files = new ArrayList<>();
        Files.walkFileTree(root, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                if (fileFilter != null && !fileFilter.accept(file.toFile())) {
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

    private static ConcurrentHashMap<Path, MediaType> getBaselineDetection(Detector detector, Path[] files) {

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

    private static ConcurrentHashMap<Path, Extract> getBaseline(Path[] files, ParseContext parseContext) {
        ConcurrentHashMap<Path, Extract> baseline = new ConcurrentHashMap<>();
        for (Path f : files) {

            try {
                Parser p = new AutoDetectParser();
                RecursiveParserWrapper wrapper = new RecursiveParserWrapper(p);
                RecursiveParserWrapperHandler handler = new RecursiveParserWrapperHandler(
                        new BasicContentHandlerFactory(BasicContentHandlerFactory.HANDLER_TYPE.TEXT, -1),
                        -1);
                try (TikaInputStream is = TikaInputStream.get(f)) {
                    wrapper.parse(is, handler, new Metadata(), parseContext);
                }
                List<Metadata> metadataList = handler.getMetadataList();
                baseline.put(f, new Extract(metadataList));
            } catch (Exception e) {
                //swallow
            }
        }
        return baseline;
    }

    private static List<Metadata> getRecursiveMetadata(InputStream is,
                                                       RecursiveParserWrapper wrapper, ParseContext parseContext) throws Exception {
        //different from parent TikaTest in that this extracts text.
        //can't extract xhtml because "tmp" file names wind up in
        //content's metadata and they'll differ by file.

        RecursiveParserWrapperHandler handler = new RecursiveParserWrapperHandler(
                new BasicContentHandlerFactory(BasicContentHandlerFactory.HANDLER_TYPE.TEXT, -1),
                -1);
        wrapper.parse(is, handler, new Metadata(), parseContext);
        return handler.getMetadataList();
    }

    private class SAXPoolResizer implements Callable<Integer> {
        private final int maxResize;
        private final Random rand = new Random();
        SAXPoolResizer(int maxResize) {
            this.maxResize = maxResize;
        }

        public Integer call() throws TikaException  {
            int resized = 0;
            while (true) {
                try {
                    Thread.yield();
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    return resized;
                }
                if (maxResize > 0 && rand.nextFloat() > 0.01) {
                    int sz = rand.nextInt(maxResize)+1;
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

        private TikaDetectorRunner(Detector detector, int iterations, Path[] files, Map<Path, MediaType> truth) {
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
                    assertEquals("failed on: " + testFile.getFileName(), truth.get(testFile), mediaType);
                }
            }
            return 1;
        }

    }


    //TODO: make this return something useful besides an integer
    private class TikaRunner implements Callable<Integer> {
        private final RecursiveParserWrapper parser;
        private final int iterations;
        private final Path[] files;
        private final Map<Path, Extract> truth;
        private final ParseContext parseContext;
        private final Random random = new Random();

        private TikaRunner(RecursiveParserWrapper parser, ParseContext parseContext, int iterations, Path[] files, Map<Path, Extract> truth) {
            this.parser = parser;
            this.iterations = iterations;
            this.files = files;
            this.truth = truth;
            this.parseContext = parseContext;
        }

        @Override
        public Integer call() throws Exception {
            for (int i = 0; i < iterations; i++) {
                int randIndex = random.nextInt(files.length);
                Path testFile = files[randIndex];
                try (InputStream is = Files.newInputStream(testFile)) {
                    List<Metadata> metadataList = getRecursiveMetadata(is, parser, parseContext);
                    assertExtractEquals(truth.get(testFile), new Extract(metadataList));
                } catch (Exception e) {
                    throw new RuntimeException(testFile + " triggered this exception", e);
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
                    extractA.metadataList.get(i).get(AbstractRecursiveParserWrapperHandler.TIKA_CONTENT),
                    extractB.metadataList.get(i).get(AbstractRecursiveParserWrapperHandler.TIKA_CONTENT));
        }
    }

    private static class Extract {
        final List<Metadata> metadataList;

        private Extract(List<Metadata> metadataList) {
            this.metadataList = metadataList;
        }
    }
}
