package org.apache.tika.mime;

import java.io.File;
import java.io.StringWriter;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

import org.apache.tika.config.TikaConfig;
import org.apache.tika.exception.ZeroByteFileException;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.sax.ToTextContentHandler;
import org.apache.tika.utils.XMLReaderUtils;

public class TestMimeTypesMultithreaded {

    private static final File STOP_NOW = new File("");

    @Test
    public void testBasic() throws Exception {
        File dir = new File("/home/tallison/Downloads/polarity_html/movie");
        ArrayBlockingQueue<File> files = loadQueue(dir);
        int numThreads = 10;
        //comment this out for the default pool size
        XMLReaderUtils.setPoolSize(numThreads);
        ExecutorService executorService = Executors.newFixedThreadPool(numThreads);
        ExecutorCompletionService<Integer> executorCompletionService = new ExecutorCompletionService<>(executorService);
        MimeTypes mimeTypes = TikaConfig.getDefaultConfig()
                                        .getMimeRepository();
        Parser parser = new AutoDetectParser(TikaConfig.getDefaultConfig());
        long start = System.currentTimeMillis();
        for (int i = 0; i < numThreads; i++) {
            executorCompletionService.submit(new MyWorker(files, parser, mimeTypes));
        }
        int finished = 0;
        int processed = 0;
        try {
            while (finished < numThreads) {
                Future<Integer> future = executorCompletionService.take();
                Integer cnt = future.get();
                System.out.println("finished " + cnt + " files");
                processed += cnt;
                finished++;
            }
        } finally {
            executorService.shutdownNow();
        }
        long elapsed = System.currentTimeMillis() - start;
        System.out.println("total files=" + processed + ", elapsed=" + elapsed + "ms");

    }

    private ArrayBlockingQueue<File> loadQueue(File dir) {
        File[] files = dir.listFiles();
        ArrayBlockingQueue<File> queue = new ArrayBlockingQueue<>(files.length + 1);
        for (File f : files) {
            queue.add(f);
        }
        queue.add(STOP_NOW);
        return queue;
    }

    private class MyWorker implements Callable<Integer> {
        private final ArrayBlockingQueue<File> files;
        private final MimeTypes mimeTypes;// = TikaConfig.getDefaultConfig().getMimeRepository();
        private final Parser parser;// = new AutoDetectParser();
        public MyWorker(ArrayBlockingQueue<File> files, Parser parser, MimeTypes mimeTypes) {
            this.files = files;
            this.mimeTypes = mimeTypes;
            this.parser = parser;
        }

        @Override
        public Integer call() throws Exception {
            int counter = 0;
            while (true) {
                File f = files.poll(1, TimeUnit.SECONDS);
                if (f == STOP_NOW) {
                    files.offer(STOP_NOW);
                    return counter;
                }
                MimeType mimeType = mimeTypes.getMimeType(f);
                if ("text/html".equals(mimeType.toString())) {
                    StringWriter stringWriter = new StringWriter();
                    try (TikaInputStream tis = TikaInputStream.get(f)) {
                        parser.parse(tis, new ToTextContentHandler(stringWriter), new Metadata(), new ParseContext());
                    } catch (ZeroByteFileException e) {
                        //swallow
                    }
                }
                //System.out.println(mimeType);
                counter++;
            }
        }
    }
}
