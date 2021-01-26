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

import com.amazonaws.auth.profile.ProfileCredentialsProvider;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.iterable.S3Objects;
import com.amazonaws.services.s3.model.S3Object;
import com.amazonaws.services.s3.model.S3ObjectSummary;
import org.apache.tika.config.TikaConfig;
import org.apache.tika.exception.TikaException;
import org.apache.tika.pipes.emitter.Emitter;
import org.apache.tika.pipes.fetcher.FetchIdMetadataPair;
import org.apache.tika.pipes.fetcher.Fetcher;
import org.apache.tika.pipes.fetchiterator.FetchIterator;
import org.junit.Ignore;
import org.junit.Test;

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

@Ignore("turn these into actual tests")
public class PipeIntegrationTests {

    private static final Path OUTDIR = Paths.get("");

    @Test
    public void testBruteForce() throws Exception {
        String region = "";
        String profile = "";
        String bucket = "";
        AmazonS3 s3Client = AmazonS3ClientBuilder.standard()
                .withRegion(region)
                .withCredentials(new ProfileCredentialsProvider(profile))
                .build();
        s3Client.listObjects(bucket);
        int cnt = 0;
        long sz = 0;

        for (S3ObjectSummary summary : S3Objects.withPrefix(s3Client, bucket, "")) {
            Path targ = OUTDIR.resolve(summary.getKey());
            if (Files.isRegularFile(targ)) {
                continue;
            }
            if (! Files.isDirectory(targ.getParent())) {
                Files.createDirectories(targ.getParent());
            }
            System.out.println("id: " + cnt + " :: " + summary.getKey() + " : " + summary.getSize());
            S3Object s3Object = s3Client.getObject(bucket, summary.getKey());
            Files.copy(s3Object.getObjectContent(), targ);
            summary.getSize();
            cnt++;
            sz += summary.getSize();
        }
        System.out.println("iterated: "+cnt + " sz: "+sz);
    }

    @Test
    public void testS3ToFS() throws Exception {
        TikaConfig tikaConfig = getConfig("tika-config-s3ToFs.xml");
        FetchIterator it = tikaConfig.getFetchIterator();
        int numConsumers = 1;
        ExecutorService es = Executors.newFixedThreadPool(numConsumers + 1);
        ExecutorCompletionService<Integer> completionService = new ExecutorCompletionService<>(es);
        ArrayBlockingQueue<FetchIdMetadataPair> queue = it.init(numConsumers);
        completionService.submit(it);
        for (int i = 0; i < numConsumers; i++) {
            completionService.submit(new FetcherEmitter(
                    queue, tikaConfig.getFetcherManager().getFetcher("s3"), null));
        }
        int finished = 0;
        try {
            while (finished++ < numConsumers+1) {
                Future<Integer> future = completionService.take();
                future.get();
            }
        } finally {
            es.shutdownNow();
        }


    }

    private TikaConfig getConfig(String fileName) throws Exception {
        try (InputStream is =
                     PipeIntegrationTests.class.getResourceAsStream("/"+fileName)) {
            return new TikaConfig(is);
        }
    }


    private static class FetcherEmitter implements Callable<Integer> {
        private static final AtomicInteger counter = new AtomicInteger(0);

        private final Fetcher fetcher;
        private final Emitter emitter;
        private final ArrayBlockingQueue<FetchIdMetadataPair> queue;

        FetcherEmitter(ArrayBlockingQueue<FetchIdMetadataPair> queue, Fetcher
                fetcher, Emitter emitter) {
            this.queue = queue;
            this.fetcher = fetcher;
            this.emitter = emitter;
        }

        @Override
        public Integer call() throws Exception {

            while (true) {
                FetchIdMetadataPair p = queue.poll(5, TimeUnit.MINUTES);
                if (p == null) {
                    throw new TimeoutException("");
                }
                if (p == FetchIterator.COMPLETED_SEMAPHORE) {
                    return 1;
                }
                process(p);
            }
        }

        private void process(FetchIdMetadataPair p) throws IOException, TikaException {
            Path targ = OUTDIR.resolve(p.getFetchId().getFetchKey());
            if (Files.isRegularFile(targ)) {
                return;
            }
            try (InputStream is = fetcher.fetch(p.getFetchId().getFetchKey(), p.getMetadata())) {
                System.out.println(counter.getAndIncrement() + " : "+p );
                Files.createDirectories(targ.getParent());
                Files.copy(is, targ);
            }
        }
    }
}
