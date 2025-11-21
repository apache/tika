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

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.ProfileCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.S3Object;

import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.pipes.api.FetchEmitTuple;
import org.apache.tika.pipes.api.emitter.Emitter;
import org.apache.tika.pipes.api.fetcher.Fetcher;
import org.apache.tika.pipes.api.pipesiterator.PipesIterator;
import org.apache.tika.pipes.emitter.s3.S3Emitter;
import org.apache.tika.pipes.pipesiterator.PipesIteratorBase;

// To enable these tests, fill OUTDIR and bucket, and adjust profile and region if needed.
// TODO: Update these tests to use the new pf4j plugin system with JSON configuration
@Disabled("turn these into actual tests with mock s3 - needs update for new plugin system")
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
        List<S3Object> s3ObjectList = s3Client.listObjectsV2Paginator(listObjectsV2Request).stream().
                flatMap(resp -> resp.contents().stream()).toList();
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

    // TODO: Implement tests using new plugin system with JSON configuration
    // The old tests used XML-based config loading which is no longer supported

    private static class FSFetcherEmitter implements Callable<Long> {
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

        S3FetcherEmitter(ArrayBlockingQueue<FetchEmitTuple> queue, Fetcher fetcher,
                         S3Emitter emitter) {
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
