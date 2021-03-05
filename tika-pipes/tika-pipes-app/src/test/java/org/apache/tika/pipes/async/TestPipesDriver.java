package org.apache.tika.pipes.async;


import org.apache.commons.io.FileUtils;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class TestPipesDriver {

    static Path TMP_DIR;
    static Path DB;

    static AtomicInteger PROCESSED = new AtomicInteger(0);

    @BeforeClass
    public static void setUp() throws Exception {
        TMP_DIR = Files.createTempDirectory("pipes-driver-");
        DB = Files.createTempFile(TMP_DIR, "", "");
    }

    @AfterClass
    public static void tearDown() throws Exception {
        FileUtils.deleteDirectory(TMP_DIR.toFile());
    }


    @Test
    public void testQueue() throws Exception {
        int numThreads = 20;
        ArrayBlockingQueue<Integer> queue = new ArrayBlockingQueue<>(10000 + numThreads);
        for (int i = 0; i < 10000; i++) {
            queue.add(1);
        }
        for (int i = 0; i < numThreads; i++) {
            queue.offer(-1);
        }
        ExecutorService service = Executors.newFixedThreadPool(numThreads);
        ExecutorCompletionService<Integer> executorCompletionService = new ExecutorCompletionService<>(service);

        long start = System.currentTimeMillis();
        executorCompletionService.submit(new Watcher(queue));
        for (int i = 0; i < numThreads; i++) {
            executorCompletionService.submit(new QueueWorker(queue));
        }
        int finished = 0;
        while (finished++ < numThreads) {
            executorCompletionService.take();
        }
        long elapsed = System.currentTimeMillis() - start;
        System.out.println("elapsed: " + elapsed);
        service.shutdownNow();
    }

    private static class Watcher implements Callable<Integer> {
        private final ArrayBlockingQueue<Integer> queue;

        Watcher(ArrayBlockingQueue<Integer> queue) {
            this.queue = queue;
        }

        @Override
        public Integer call() throws Exception {
            long start = System.currentTimeMillis();
            while (true) {
                long elapsed = System.currentTimeMillis() - start;
                Thread.sleep(1000);
            }
        }
    }

    private static class QueueWorker implements Callable<Integer> {
        static AtomicInteger counter = new AtomicInteger(0);


        private final int id;
        private final ArrayBlockingQueue<Integer> queue;

        QueueWorker(ArrayBlockingQueue<Integer> queue) {
            id = counter.incrementAndGet();
            this.queue = queue;
        }

        @Override
        public Integer call() throws Exception {
            while (true) {
                Integer val = queue.poll(1, TimeUnit.SECONDS);
                if (val != null) {
                    if (val < 0) {
                        return 1;
                    } else {
                        long sleep = id * 100;
                        Thread.sleep(sleep);
                    }
                }
            }
        }
    }
}
