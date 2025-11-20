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
package org.apache.tika.pipes.s3.tests;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.pipes.core.FetchEmitTuple;
import org.apache.tika.pipes.core.emitter.Emitter;
import org.apache.tika.pipes.core.emitter.EmitterManager;
import org.apache.tika.pipes.core.fetcher.Fetcher;
import org.apache.tika.pipes.core.fetcher.FetcherManager;
import org.apache.tika.pipes.core.pipesiterator.CallablePipesIterator;
import org.apache.tika.pipes.core.pipesiterator.PipesIterator;
import org.apache.tika.pipes.emitter.s3.S3Emitter;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.ProfileCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.S3Object;

// To enable these tests, fill OUTDIR and bucket, and adjust profile and region if needed.
@Disabled("turn these into actual tests with mock s3")
public class PipeIntegrationTests {

    private static final Path OUTDIR = Paths.get("");

    /**
     * This downloads files from a specific bucket.
     * @throws Exception 
     */
    @Test
    public void testBruteForce() throws Exception {
        String region = "us-east-1";
        String profile = "default";
        String bucket = "";
        AwsCredentialsProvider provider = ProfileCredentialsProvider.builder().profileName(profile).build();
        S3Client s3Client = S3Client.builder().credentialsProvider(provider).region(Region.of(region)).build();

        int cnt = 0;
        long sz = 0;

        ListObjectsV2Request listObjectsV2Request = ListObjectsV2Request.builder().bucket(bucket).prefix("").build();
        List<S3Object> s3ObjectList = s3Client.listObjectsV2Paginator(listObjectsV2Request).stream()
                .flatMap(resp -> resp.contents().stream()).toList();
        for (S3Object s3Object : s3ObjectList) {
            String key = s3Object.key();
            Path targ = OUTDIR.resolve(key);
            if (Files.isRegularFile(targ)) {
                continue;
            }
            if (!Files.isDirectory(targ.getParent())) {
                Files.createDirectories(targ.getParent());
            }
            System.out.println("id: " + cnt + " :: " + key + " : " + s3Object.size());
            GetObjectRequest objectRequest = GetObjectRequest.builder().bucket(bucket).key(key).build();
            s3Client.getObject(objectRequest, targ);
            cnt++;
            sz += s3Object.size();
        }
        System.out.println("iterated: " + cnt + " sz: " + sz);
    }

    // to test this, files must be in the fetcher bucket
    @Test
    public void testS3ToFS() throws Exception {
        Fetcher fetcher = getFetcher("tika-config-s3ToFs.xml", "s3f");
        PipesIterator pipesIterator = getPipesIterator("tika-config-s3ToFs.xml");

        int numConsumers = 1;
        ExecutorService es = Executors.newFixedThreadPool(numConsumers + 1);
        ExecutorCompletionService<Long> completionService = new ExecutorCompletionService<>(es);
        ArrayBlockingQueue<FetchEmitTuple> queue = new ArrayBlockingQueue<>(1000);

        completionService.submit(new CallablePipesIterator(pipesIterator, queue, 60000, numConsumers));
        for (int i = 0; i < numConsumers; i++) {
            completionService.submit(new FSFetcherEmitter(queue, fetcher, null));
        }

        for (int i = 0; i < numConsumers; i++) {
            queue.offer(PipesIterator.COMPLETED_SEMAPHORE);
        }
        int finished = 0;
        try {
            while (finished++ < numConsumers + 1) {
                Future<Long> future = completionService.take();
                future.get();
            }
        } finally {
            es.shutdownNow();
        }
    }

    // to test this, files must be in the iterator bucket
    @Test
    public void testS3ToS3() throws Exception {
        Fetcher fetcher = getFetcher("tika-config-s3Tos3.xml", "s3f");
        Emitter emitter = getEmitter("tika-config-s3Tos3.xml", "s3e");
        PipesIterator pipesIterator = getPipesIterator("tika-config-s3Tos3.xml");
        int numConsumers = 20;
        ExecutorService es = Executors.newFixedThreadPool(numConsumers + 1);
        ExecutorCompletionService<Long> completionService = new ExecutorCompletionService<>(es);
        ArrayBlockingQueue<FetchEmitTuple> queue = new ArrayBlockingQueue<>(1000);
        completionService.submit(new CallablePipesIterator(pipesIterator, queue, 60000, numConsumers));
        for (int i = 0; i < numConsumers; i++) {
            completionService.submit(new S3FetcherEmitter(queue, fetcher, (S3Emitter) emitter));
        }
        for (int i = 0; i < numConsumers; i++) {
            queue.offer(PipesIterator.COMPLETED_SEMAPHORE);
        }
        int finished = 0;
        try {
            while (finished++ < numConsumers + 1) {
                Future<Long> future = completionService.take();
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

    private static class FSFetcherEmitter implements Callable<Long> {
        private static final AtomicInteger counter = new AtomicInteger(0);

        private final Fetcher fetcher;
        private final Emitter emitter;
        private final ArrayBlockingQueue<FetchEmitTuple> queue;

        FSFetcherEmitter(ArrayBlockingQueue<FetchEmitTuple> queue, Fetcher fetcher, Emitter emitter) {
            this.queue = queue;
            this.fetcher = fetcher;
            this.emitter = emitter;
        }

        @Override
        public Long call() throws Exception {

            while (true) {
                FetchEmitTuple t = queue.poll(5, TimeUnit.MINUTES);
                if (t == null) {
                    throw new TimeoutException("");
                }
                if (t == PipesIterator.COMPLETED_SEMAPHORE) {
                    return 1l;
                }
                process(t);
            }
        }

        private void process(FetchEmitTuple t) throws IOException, TikaException {
            Path targ = OUTDIR.resolve(t.getFetchKey().getFetchKey());
            if (Files.isRegularFile(targ)) {
                return;
            }
            try (InputStream is = fetcher.fetch(t.getFetchKey().getFetchKey(), t.getMetadata(), t.getParseContext())) {
                System.out.println(counter.getAndIncrement() + " : " + t);
                Files.createDirectories(targ.getParent());
                Files.copy(is, targ);
            }
        }
    }

    private static class S3FetcherEmitter implements Callable<Long> {
        private static final AtomicInteger counter = new AtomicInteger(0);

        private final Fetcher fetcher;
        private final S3Emitter emitter;
        private final ArrayBlockingQueue<FetchEmitTuple> queue;

        S3FetcherEmitter(ArrayBlockingQueue<FetchEmitTuple> queue, Fetcher fetcher, S3Emitter emitter) {
            this.queue = queue;
            this.fetcher = fetcher;
            this.emitter = emitter;
        }

        @Override
        public Long call() throws Exception {

            while (true) {
                FetchEmitTuple t = queue.poll(5, TimeUnit.MINUTES);
                if (t == null) {
                    throw new TimeoutException("");
                }
                if (t == PipesIterator.COMPLETED_SEMAPHORE) {
                    return 1l;
                }
                process(t);
            }
        }

        private void process(FetchEmitTuple t) throws IOException, TikaException {
            Metadata userMetadata = t.getMetadata();
            userMetadata.set("project", "my-project");

            try (InputStream is = fetcher.fetch(t.getFetchKey().getFetchKey(), t.getMetadata(), t.getParseContext())) {
                emitter.emit(t.getEmitKey().getEmitKey(), is, userMetadata, t.getParseContext());
            }
        }
    }
}
