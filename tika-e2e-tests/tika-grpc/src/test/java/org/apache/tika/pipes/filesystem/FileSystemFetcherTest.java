package org.apache.tika.pipes.filesystem;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.grpc.ManagedChannel;
import io.grpc.stub.StreamObserver;
import lombok.extern.slf4j.Slf4j;
import org.apache.tika.FetchAndParseReply;
import org.apache.tika.FetchAndParseRequest;
import org.apache.tika.GetFetcherReply;
import org.apache.tika.GetFetcherRequest;
import org.apache.tika.SaveFetcherReply;
import org.apache.tika.SaveFetcherRequest;
import org.apache.tika.TikaGrpc;
import org.apache.tika.pipes.ExternalTestBase;
import org.apache.tika.pipes.fetcher.fs.FileSystemFetcherConfig;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

@Slf4j
class FileSystemFetcherTest extends ExternalTestBase {
    
    @Test
    void testFileSystemFetcher() throws Exception {
        String fetcherId = "defaultFetcher";
        ManagedChannel channel = getManagedChannel();
        TikaGrpc.TikaBlockingStub blockingStub = TikaGrpc.newBlockingStub(channel);
        TikaGrpc.TikaStub tikaStub = TikaGrpc.newStub(channel);

        // Create and save the fetcher dynamically
        FileSystemFetcherConfig config = new FileSystemFetcherConfig();
        config.setBasePath("/tika/govdocs1");
        
        String configJson = OBJECT_MAPPER.writeValueAsString(config);
        log.info("Creating fetcher with config: {}", configJson);
        
        SaveFetcherReply saveReply = blockingStub.saveFetcher(SaveFetcherRequest
                .newBuilder()
                .setFetcherId(fetcherId)
                .setFetcherClass("org.apache.tika.pipes.fetcher.fs.FileSystemFetcher")
                .setFetcherConfigJson(configJson)
                .build());
        
        log.info("Fetcher created: {}", saveReply.getFetcherId());

        List<FetchAndParseReply> successes = Collections.synchronizedList(new ArrayList<>());
        List<FetchAndParseReply> errors = Collections.synchronizedList(new ArrayList<>());

        CountDownLatch countDownLatch = new CountDownLatch(1);
        StreamObserver<FetchAndParseRequest>
                requestStreamObserver = tikaStub.fetchAndParseBiDirectionalStreaming(new StreamObserver<>() {
            @Override
            public void onNext(FetchAndParseReply fetchAndParseReply) {
                log.debug("Reply from fetch-and-parse - key={}, status={}", 
                    fetchAndParseReply.getFetchKey(), fetchAndParseReply.getStatus());
                if ("FETCH_AND_PARSE_EXCEPTION".equals(fetchAndParseReply.getStatus())) {
                    errors.add(fetchAndParseReply);
                } else {
                    successes.add(fetchAndParseReply);
                }
            }

            @Override
            public void onError(Throwable throwable) {
                log.error("Received an error", throwable);
                Assertions.fail(throwable);
                countDownLatch.countDown();
            }

            @Override
            public void onCompleted() {
                log.info("Finished streaming fetch and parse replies");
                countDownLatch.countDown();
            }
        });

        // Submit all files for parsing
        int maxDocs = Integer.parseInt(System.getProperty("corpa.numdocs", "-1"));
        log.info("Document limit: {}", maxDocs == -1 ? "unlimited" : maxDocs);
        
        try (Stream<Path> paths = Files.walk(TEST_FOLDER.toPath())) {
            Stream<Path> fileStream = paths.filter(Files::isRegularFile);
            
            // Limit number of documents if specified
            if (maxDocs > 0) {
                fileStream = fileStream.limit(maxDocs);
            }
            
            fileStream.forEach(file -> {
                        try {
                            String relPath = TEST_FOLDER.toPath().relativize(file).toString();
                            requestStreamObserver.onNext(FetchAndParseRequest
                                    .newBuilder()
                                    .setFetcherId(fetcherId)
                                    .setFetchKey(relPath)
                                    .build());
                        } catch (Exception e) {
                            throw new RuntimeException(e);
                        }
                    });
        }
        log.info("Done submitting files to fetcher {}", fetcherId);

        requestStreamObserver.onCompleted();

        // Wait for all parsing to complete
        try {
            if (!countDownLatch.await(3, TimeUnit.MINUTES)) {
                log.error("Timed out waiting for parse to complete");
                Assertions.fail("Timed out waiting for parsing to complete");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            Assertions.fail("Interrupted while waiting for parsing to complete");
        }
        
        // Verify all files were processed (unless we limited the number)
        if (maxDocs == -1) {
            assertAllFilesFetched(TEST_FOLDER.toPath(), successes, errors);
        } else {
            int totalProcessed = successes.size() + errors.size();
            log.info("Processed {} documents (limit was {})", totalProcessed, maxDocs);
            Assertions.assertTrue(totalProcessed <= maxDocs, 
                "Should not process more than " + maxDocs + " documents");
            Assertions.assertTrue(totalProcessed > 0, 
                "Should have processed at least one document");
        }
        
        log.info("Test completed successfully - {} successes, {} errors", 
            successes.size(), errors.size());
    }
}
