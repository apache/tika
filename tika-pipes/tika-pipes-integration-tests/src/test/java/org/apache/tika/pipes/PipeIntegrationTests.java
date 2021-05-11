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

package org.apache.tika.pipes;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

import com.amazonaws.auth.profile.ProfileCredentialsProvider;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.iterable.S3Objects;
import com.amazonaws.services.s3.model.S3Object;
import com.amazonaws.services.s3.model.S3ObjectSummary;
import org.junit.Ignore;
import org.junit.Test;

import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.pipes.emitter.Emitter;
import org.apache.tika.pipes.emitter.EmitterManager;
import org.apache.tika.pipes.emitter.s3.S3Emitter;
import org.apache.tika.pipes.fetcher.Fetcher;
import org.apache.tika.pipes.fetcher.FetcherManager;
import org.apache.tika.pipes.pipesiterator.PipesIterator;

@Ignore("turn these into actual tests")
public class PipeIntegrationTests {

    private static final Path OUTDIR = Paths.get("");

    @Test
    public void testBruteForce() throws Exception {
        String region = "";
        String profile = "";
        String bucket = "";
        AmazonS3 s3Client = AmazonS3ClientBuilder.standard().withRegion(region)
                .withCredentials(new ProfileCredentialsProvider(profile)).build();
        s3Client.listObjects(bucket);
        int cnt = 0;
        long sz = 0;

        for (S3ObjectSummary summary : S3Objects.withPrefix(s3Client, bucket, "")) {
            Path targ = OUTDIR.resolve(summary.getKey());
            if (Files.isRegularFile(targ)) {
                continue;
            }
            if (!Files.isDirectory(targ.getParent())) {
                Files.createDirectories(targ.getParent());
            }
            System.out
                    .println("id: " + cnt + " :: " + summary.getKey() + " : " + summary.getSize());
            S3Object s3Object = s3Client.getObject(bucket, summary.getKey());
            Files.copy(s3Object.getObjectContent(), targ);
            summary.getSize();
            cnt++;
            sz += summary.getSize();
        }
        System.out.println("iterated: " + cnt + " sz: " + sz);
    }

    @Test
    public void testS3ToFS() throws Exception {
        Fetcher fetcher = getFetcher("tika-config-s3ToFs.xml", "s3f");
        PipesIterator pipesIterator = getPipesIterator("tika-config-s3ToFs.xml");

        int numConsumers = 1;
        ExecutorService es = Executors.newFixedThreadPool(numConsumers + 1);
        ExecutorCompletionService<Integer> completionService = new ExecutorCompletionService<>(es);
        ArrayBlockingQueue<FetchEmitTuple> queue = new ArrayBlockingQueue<>(1000);
        for (int i = 0; i < numConsumers; i++) {
            completionService.submit(new FSFetcherEmitter(queue, fetcher, null));
        }
        for (FetchEmitTuple t : pipesIterator) {
            queue.offer(t);
        }
        for (int i = 0; i < numConsumers; i++) {
            queue.offer(PipesIterator.COMPLETED_SEMAPHORE);
        }
        int finished = 0;
        try {
            while (finished++ < numConsumers + 1) {
                Future<Integer> future = completionService.take();
                future.get();
            }
        } finally {
            es.shutdownNow();
        }
    }

    @Test
    public void testS3ToS3() throws Exception {
        Fetcher fetcher = getFetcher("tika-config-s3Tos3.xml", "s3f");
        Emitter emitter = getEmitter("tika-config-s3Tos3.xml", "s3e");
        PipesIterator pipesIterator = getPipesIterator("tika-config-s3Tos3.xml");
        int numConsumers = 20;
        ExecutorService es = Executors.newFixedThreadPool(numConsumers + 1);
        ExecutorCompletionService<Integer> completionService = new ExecutorCompletionService<>(es);
        ArrayBlockingQueue<FetchEmitTuple> queue = new ArrayBlockingQueue<>(1000);
        for (int i = 0; i < numConsumers; i++) {
            completionService.submit(new S3FetcherEmitter(queue, fetcher, (S3Emitter) emitter));
        }
        for (FetchEmitTuple t : pipesIterator) {
            queue.offer(t);
        }
        for (int i = 0; i < numConsumers; i++) {
            queue.offer(PipesIterator.COMPLETED_SEMAPHORE);
        }
        int finished = 0;
        try {
            while (finished++ < numConsumers + 1) {
                Future<Integer> future = completionService.take();
                future.get();
            }
        } finally {
            es.shutdownNow();
        }
    }

    private Fetcher getFetcher(String fileName, String fetcherName) throws Exception {
        FetcherManager manager = FetcherManager.load(getPath(fileName));
        return manager.getFetcher(fetcherName);
    }

    private Emitter getEmitter(String fileName, String emitterName) throws Exception {
        EmitterManager manager = EmitterManager.load(getPath(fileName));
        return manager.getEmitter(emitterName);
    }

    private PipesIterator getPipesIterator(String fileName) throws Exception {
        return PipesIterator.build(getPath(fileName));
    }

    private Path getPath(String fileName) throws Exception {
        return Paths.get(PipeIntegrationTests.class.getResource("/" + fileName).toURI());
    }


    private static class FSFetcherEmitter implements Callable<Integer> {
        private static final AtomicInteger counter = new AtomicInteger(0);

        private final Fetcher fetcher;
        private final Emitter emitter;
        private final ArrayBlockingQueue<FetchEmitTuple> queue;

        FSFetcherEmitter(ArrayBlockingQueue<FetchEmitTuple> queue, Fetcher fetcher,
                         Emitter emitter) {
            this.queue = queue;
            this.fetcher = fetcher;
            this.emitter = emitter;
        }

        @Override
        public Integer call() throws Exception {

            while (true) {
                FetchEmitTuple t = queue.poll(5, TimeUnit.MINUTES);
                if (t == null) {
                    throw new TimeoutException("");
                }
                if (t == PipesIterator.COMPLETED_SEMAPHORE) {
                    return 1;
                }
                process(t);
            }
        }

        private void process(FetchEmitTuple t) throws IOException, TikaException {
            Path targ = OUTDIR.resolve(t.getFetchKey().getFetchKey());
            if (Files.isRegularFile(targ)) {
                return;
            }
            try (InputStream is = fetcher.fetch(t.getFetchKey().getFetchKey(), t.getMetadata())) {
                System.out.println(counter.getAndIncrement() + " : " + t);
                Files.createDirectories(targ.getParent());
                Files.copy(is, targ);
            }
        }
    }

    private static class S3FetcherEmitter implements Callable<Integer> {
        private static final AtomicInteger counter = new AtomicInteger(0);

        private final Fetcher fetcher;
        private final S3Emitter emitter;
        private final ArrayBlockingQueue<FetchEmitTuple> queue;

        S3FetcherEmitter(ArrayBlockingQueue<FetchEmitTuple> queue, Fetcher fetcher,
                         S3Emitter emitter) {
            this.queue = queue;
            this.fetcher = fetcher;
            this.emitter = emitter;
        }

        @Override
        public Integer call() throws Exception {

            while (true) {
                FetchEmitTuple t = queue.poll(5, TimeUnit.MINUTES);
                if (t == null) {
                    throw new TimeoutException("");
                }
                if (t == PipesIterator.COMPLETED_SEMAPHORE) {
                    return 1;
                }
                process(t);
            }
        }

        private void process(FetchEmitTuple t) throws IOException, TikaException {
            Metadata userMetadata = new Metadata();
            userMetadata.set("project", "my-project");

            try (InputStream is = fetcher.fetch(t.getFetchKey().getFetchKey(), t.getMetadata())) {
                emitter.emit(t.getEmitKey().getEmitKey(), is, userMetadata);
            }
        }
    }
}
