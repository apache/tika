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
package org.apache.tika.server.core.benchmark;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.LongSummaryStatistics;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Performance benchmark for tika-server.
 * <p>
 * This benchmark sends mock XML files to tika-server and measures throughput and latency.
 * It uses the MockParser which must be on the classpath of the tika-server being tested.
 * <p>
 * Two test modes are supported:
 * <ul>
 *   <li><b>size</b> - Tests with different file sizes (I/O bound)</li>
 *   <li><b>sleep</b> - Tests with parser sleep delays (CPU/process bound) - better for measuring forking overhead</li>
 * </ul>
 * <p>
 * Usage:
 * <pre>
 * java TikaServerBenchmark [options]
 *   --url=URL            Base URL of tika-server (default: http://localhost:9998)
 *   --endpoint=PATH      Endpoint to test (default: /tika)
 *   --threads=N          Number of client threads (default: 4)
 *   --count=N            Number of requests per test (default: 1000)
 *   --warmup=N           Number of warmup requests (default: 100)
 *   --repeats=N          Number of times to repeat the benchmark (default: 1)
 *   --mode=MODE          Test mode: 'size' or 'sleep' (default: size)
 *   --sync               Synchronous mode: each thread sends one request at a time (default)
 *   --async              Async mode: all requests sent immediately (stress test)
 *
 *   Size mode options:
 *   --small-kb=N         Size of small files in KB (default: 1)
 *   --large-kb=N         Size of large files in KB (default: 100)
 *
 *   Sleep mode options:
 *   --short-ms=N         Short sleep duration in ms (default: 10)
 *   --long-ms=N          Long sleep duration in ms (default: 5000)
 * </pre>
 */
public class TikaServerBenchmark {

    private static final String MOCK_XML_SIZE_TEMPLATE = """
            <?xml version="1.0" encoding="UTF-8" ?>
            <mock>
                <metadata action="add" name="author">Benchmark Test</metadata>
                <metadata action="add" name="title">Performance Test Document</metadata>
                <write element="p">%s</write>
            </mock>
            """;

    private static final String MOCK_XML_SLEEP_TEMPLATE = """
            <?xml version="1.0" encoding="UTF-8" ?>
            <mock>
                <metadata action="add" name="author">Benchmark Test</metadata>
                <metadata action="add" name="title">Sleep Test Document</metadata>
                <hang millis="%d" heavy="false" interruptible="false" />
                <write element="p">Test content after sleep</write>
            </mock>
            """;

    private final String baseUrl;
    private final String endpoint;
    private final int threads;
    private final int count;
    private final int warmupCount;
    private final int repeats;
    private final String mode;
    private final boolean syncMode;

    // Size mode params
    private final int smallSizeKb;
    private final int largeSizeKb;

    // Sleep mode params
    private final int shortSleepMs;
    private final int longSleepMs;

    private final HttpClient httpClient;
    private final ExecutorService httpExecutor;
    private final ExecutorService taskExecutor;

    private byte[] smallContent;
    private byte[] largeContent;

    public TikaServerBenchmark(String baseUrl, String endpoint, int threads, int count,
                               int warmupCount, int repeats, String mode, boolean syncMode,
                               int smallSizeKb, int largeSizeKb, int shortSleepMs, int longSleepMs) {
        this.baseUrl = baseUrl;
        this.endpoint = endpoint;
        this.threads = threads;
        this.count = count;
        this.warmupCount = warmupCount;
        this.repeats = repeats;
        this.mode = mode;
        this.syncMode = syncMode;
        this.smallSizeKb = smallSizeKb;
        this.largeSizeKb = largeSizeKb;
        this.shortSleepMs = shortSleepMs;
        this.longSleepMs = longSleepMs;

        // Separate executors to avoid deadlock:
        // - httpExecutor: used by HttpClient for internal async operations
        // - taskExecutor: used for our benchmark tasks in sync mode
        this.httpExecutor = Executors.newFixedThreadPool(threads);
        this.taskExecutor = Executors.newFixedThreadPool(threads);
        this.httpClient = HttpClient.newBuilder()
                .executor(httpExecutor)
                .connectTimeout(Duration.ofSeconds(30))
                .build();

        generateTestContent();
    }

    private void generateTestContent() {
        if ("sleep".equals(mode)) {
            smallContent = generateSleepMockXml(shortSleepMs);
            largeContent = generateSleepMockXml(longSleepMs);
        } else {
            smallContent = generateSizeMockXml(smallSizeKb * 1024);
            largeContent = generateSizeMockXml(largeSizeKb * 1024);
        }
    }

    private byte[] generateSizeMockXml(int targetSizeBytes) {
        StringBuilder content = new StringBuilder();
        String baseText = "Lorem ipsum dolor sit amet, consectetur adipiscing elit. " +
                "Sed do eiusmod tempor incididunt ut labore et dolore magna aliqua. " +
                "Ut enim ad minim veniam, quis nostrud exercitation ullamco laboris. ";

        while (content.length() < targetSizeBytes) {
            content.append(baseText);
        }

        String xml = String.format(Locale.ROOT, MOCK_XML_SIZE_TEMPLATE,
                content.substring(0, Math.min(content.length(), targetSizeBytes)));
        return xml.getBytes(StandardCharsets.UTF_8);
    }

    private byte[] generateSleepMockXml(int sleepMs) {
        String xml = String.format(Locale.ROOT, MOCK_XML_SLEEP_TEMPLATE, sleepMs);
        return xml.getBytes(StandardCharsets.UTF_8);
    }

    public void run() throws Exception {
        System.out.println("=".repeat(70));
        System.out.println("Tika Server Performance Benchmark");
        System.out.println("=".repeat(70));
        System.out.println();
        System.out.printf(Locale.ROOT, "Target URL:    %s%s%n", baseUrl, endpoint);
        System.out.printf(Locale.ROOT, "Threads:       %d%n", threads);
        System.out.printf(Locale.ROOT, "Requests/test: %d%n", count);
        System.out.printf(Locale.ROOT, "Repeats:       %d%n", repeats);
        System.out.printf(Locale.ROOT, "Mode:          %s%n", mode);
        System.out.printf(Locale.ROOT, "Request mode:  %s%n", syncMode ? "sync (realistic)" : "async (stress test)");

        if ("sleep".equals(mode)) {
            System.out.printf(Locale.ROOT, "Short sleep:   %d ms%n", shortSleepMs);
            System.out.printf(Locale.ROOT, "Long sleep:    %d ms%n", longSleepMs);
        } else {
            System.out.printf(Locale.ROOT, "Small size:    %d KB%n", smallSizeKb);
            System.out.printf(Locale.ROOT, "Large size:    %d KB%n", largeSizeKb);
        }
        System.out.println();

        // Check server is reachable
        if (!checkServerHealth()) {
            System.err.println("ERROR: Cannot reach tika-server at " + baseUrl);
            System.err.println("Make sure tika-server is running before starting the benchmark.");
            System.exit(1);
        }
        System.out.println("Server is reachable.");

        // Verify MockParser is being used (only for sleep mode)
        if ("sleep".equals(mode)) {
            if (!verifyMockParserInUse()) {
                System.err.println("ERROR: MockParser is NOT being used by the server!");
                System.err.println("The tika-core test jar must be on the server's classpath.");
                System.err.println("If using java -jar, the test jar must be in the manifest Class-Path.");
                System.err.println("Try running with: java -cp 'tika-server.jar:lib/*' org.apache.tika.server.core.TikaServerCli");
                System.exit(1);
            }
            System.out.println("MockParser verified - sleep mode will work correctly.");
        }
        System.out.println();

        // Warmup
        System.out.printf(Locale.ROOT, "Warming up with %d requests...%n", warmupCount);
        runBenchmark(smallContent, warmupCount, "warmup", getSmallLabel());
        System.out.println("Warmup complete.");
        System.out.println();

        String firstLabel = getSmallLabel();
        String secondLabel = getLargeLabel();

        // Collect results across all repeats
        List<BenchmarkResult> firstResults = new ArrayList<>();
        List<BenchmarkResult> secondResults = new ArrayList<>();

        // Per-benchmark warmup count (10 requests per thread)
        int perBenchmarkWarmup = threads * 10;

        for (int rep = 1; rep <= repeats; rep++) {
            if (repeats > 1) {
                System.out.println();
                System.out.println("*".repeat(70));
                System.out.printf(Locale.ROOT, "REPEAT %d of %d%n", rep, repeats);
                System.out.println("*".repeat(70));
            }

            // First test (small/short)
            System.out.println("-".repeat(70));
            System.out.printf(Locale.ROOT, "Running %s benchmark (%d requests)%n", firstLabel.toUpperCase(Locale.ROOT), count);
            System.out.println("-".repeat(70));
            // Warmup for this benchmark (10 requests per thread, not counted)
            System.out.printf(Locale.ROOT, "  Per-benchmark warmup (%d requests)...%n", perBenchmarkWarmup);
            runBenchmark(smallContent, perBenchmarkWarmup, "warmup", firstLabel);
            BenchmarkResult firstResult = runBenchmark(smallContent, count, "first", firstLabel);
            firstResults.add(firstResult);
            printResults(firstResult, firstLabel);
            System.out.println();

            // Second test (large/long)
            System.out.println("-".repeat(70));
            System.out.printf(Locale.ROOT, "Running %s benchmark (%d requests)%n", secondLabel.toUpperCase(Locale.ROOT), count);
            System.out.println("-".repeat(70));
            // Warmup for this benchmark (10 requests per thread, not counted)
            System.out.printf(Locale.ROOT, "  Per-benchmark warmup (%d requests)...%n", perBenchmarkWarmup);
            runBenchmark(largeContent, perBenchmarkWarmup, "warmup", secondLabel);
            BenchmarkResult secondResult = runBenchmark(largeContent, count, "second", secondLabel);
            secondResults.add(secondResult);
            printResults(secondResult, secondLabel);
        }

        // Calculate aggregated results
        BenchmarkResult firstAgg = aggregateResults(firstResults);
        BenchmarkResult secondAgg = aggregateResults(secondResults);

        // Summary
        System.out.println();
        System.out.println("=".repeat(70));
        if (repeats > 1) {
            System.out.printf(Locale.ROOT, "SUMMARY (averaged over %d repeats)%n", repeats);
        } else {
            System.out.println("SUMMARY");
        }
        System.out.println("=".repeat(70));
        System.out.printf(Locale.ROOT, "%-20s %18s %18s%n", "Metric", firstLabel, secondLabel);
        System.out.println("-".repeat(70));
        System.out.printf(Locale.ROOT, "%-20s %18.2f %18.2f%n", "Throughput (req/s)", firstAgg.throughput, secondAgg.throughput);
        System.out.printf(Locale.ROOT, "%-20s %18.2f %18.2f%n", "Avg Latency (ms)", firstAgg.avgLatencyMs, secondAgg.avgLatencyMs);
        System.out.printf(Locale.ROOT, "%-20s %18.2f %18.2f%n", "P50 Latency (ms)", firstAgg.p50LatencyMs, secondAgg.p50LatencyMs);
        System.out.printf(Locale.ROOT, "%-20s %18.2f %18.2f%n", "P95 Latency (ms)", firstAgg.p95LatencyMs, secondAgg.p95LatencyMs);
        System.out.printf(Locale.ROOT, "%-20s %18.2f %18.2f%n", "P99 Latency (ms)", firstAgg.p99LatencyMs, secondAgg.p99LatencyMs);
        System.out.printf(Locale.ROOT, "%-20s %18d %18d%n", "Success Count", firstAgg.successCount, secondAgg.successCount);
        System.out.printf(Locale.ROOT, "%-20s %18d %18d%n", "Error Count", firstAgg.errorCount, secondAgg.errorCount);
        System.out.println("=".repeat(70));

        // Output CSV-friendly line for easy comparison
        System.out.println();
        System.out.println("CSV format (for comparison):");
        System.out.printf(Locale.ROOT, "mode,threads,repeats,%s_throughput,%s_p50,%s_p95,%s_throughput,%s_p50,%s_p95%n",
                firstLabel, firstLabel, firstLabel, secondLabel, secondLabel, secondLabel);
        System.out.printf(Locale.ROOT, "%s,%d,%d,%.2f,%.2f,%.2f,%.2f,%.2f,%.2f%n",
                mode, threads, repeats,
                firstAgg.throughput, firstAgg.p50LatencyMs, firstAgg.p95LatencyMs,
                secondAgg.throughput, secondAgg.p50LatencyMs, secondAgg.p95LatencyMs);

        shutdown();
    }

    private BenchmarkResult aggregateResults(List<BenchmarkResult> results) {
        if (results.size() == 1) {
            return results.get(0);
        }
        double avgThroughput = results.stream().mapToDouble(r -> r.throughput).average().orElse(0);
        double avgLatency = results.stream().mapToDouble(r -> r.avgLatencyMs).average().orElse(0);
        double avgP50 = results.stream().mapToDouble(r -> r.p50LatencyMs).average().orElse(0);
        double avgP95 = results.stream().mapToDouble(r -> r.p95LatencyMs).average().orElse(0);
        double avgP99 = results.stream().mapToDouble(r -> r.p99LatencyMs).average().orElse(0);
        double avgMax = results.stream().mapToDouble(r -> r.maxLatencyMs).average().orElse(0);
        int totalSuccess = results.stream().mapToInt(r -> r.successCount).sum();
        int totalErrors = results.stream().mapToInt(r -> r.errorCount).sum();
        return new BenchmarkResult(avgThroughput, avgLatency, avgP50, avgP95, avgP99, avgMax, totalSuccess, totalErrors);
    }

    private String getSmallLabel() {
        return "sleep".equals(mode) ? "short-sleep" : "small-files";
    }

    private String getLargeLabel() {
        return "sleep".equals(mode) ? "long-sleep" : "large-files";
    }

    private boolean checkServerHealth() {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/tika"))
                    .GET()
                    .timeout(Duration.ofSeconds(5))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            return response.statusCode() == 200;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Verifies that MockParser is available and being used by the server.
     * Sends a test request and checks that the response contains MockParser in X-Parsed-By.
     */
    private boolean verifyMockParserInUse() {
        try {
            // Send a simple mock XML request to /rmeta to get metadata including X-Parsed-By
            String testXml = "<?xml version=\"1.0\" encoding=\"UTF-8\" ?><mock><write element=\"p\">test</write></mock>";
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/rmeta"))
                    .header("Content-Type", "application/mock+xml")
                    .header("Accept", "application/json")
                    .PUT(HttpRequest.BodyPublishers.ofString(testXml))
                    .timeout(Duration.ofSeconds(10))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                System.err.printf(Locale.ROOT, "MockParser verification failed: HTTP %d%n", response.statusCode());
                return false;
            }

            String body = response.body();
            // Check if response contains MockParser in the X-TIKA-Parsed-By field
            if (body.contains("MockParser")) {
                return true;
            } else {
                System.err.println("Response does not contain MockParser:");
                System.err.println(body.substring(0, Math.min(500, body.length())));
                return false;
            }
        } catch (Exception e) {
            System.err.println("MockParser verification failed: " + e.getMessage());
            return false;
        }
    }

    private BenchmarkResult runBenchmark(byte[] content, int requestCount, String phase, String label) {
        if (syncMode) {
            return runBenchmarkSync(content, requestCount, phase, label);
        } else {
            return runBenchmarkAsync(content, requestCount, phase, label);
        }
    }

    /**
     * Synchronous benchmark: each thread sends requests one at a time, waiting for response before sending next.
     * This models realistic usage where clients process results before making the next request.
     */
    private BenchmarkResult runBenchmarkSync(byte[] content, int requestCount, String phase, String label) {
        ConcurrentLinkedQueue<Long> latencies = new ConcurrentLinkedQueue<>();
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger errorCount = new AtomicInteger(0);
        AtomicInteger completedCount = new AtomicInteger(0);

        // Calculate appropriate timeout based on content
        int timeoutSeconds = "sleep".equals(mode) ? Math.max(60, longSleepMs / 1000 + 30) : 60;

        // Divide requests among threads
        int requestsPerThread = requestCount / threads;
        int extraRequests = requestCount % threads;

        CountDownLatch latch = new CountDownLatch(threads);

        long startTime = System.nanoTime();

        for (int t = 0; t < threads; t++) {
            final int threadRequests = requestsPerThread + (t < extraRequests ? 1 : 0);

            taskExecutor.submit(() -> {
                try {
                    for (int i = 0; i < threadRequests; i++) {
                        long latency = sendRequestSync(content, timeoutSeconds);
                        if (latency >= 0) {
                            successCount.incrementAndGet();
                            latencies.add(latency);
                        } else {
                            errorCount.incrementAndGet();
                        }
                        int completed = completedCount.incrementAndGet();
                        if (completed % 100 == 0 || completed == requestCount) {
                            System.out.printf(Locale.ROOT, "\r  Progress: %d/%d (%.1f%%)    ",
                                    completed, requestCount, (100.0 * completed / requestCount));
                        }
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        long endTime = System.nanoTime();
        double totalTimeSeconds = (endTime - startTime) / 1_000_000_000.0;

        System.out.println(); // New line after progress

        return calculateResults(new ArrayList<>(latencies), successCount.get(), errorCount.get(), totalTimeSeconds);
    }

    /**
     * Asynchronous benchmark: all requests sent immediately (stress test mode).
     * This tests the server's maximum throughput capacity.
     */
    private BenchmarkResult runBenchmarkAsync(byte[] content, int requestCount, String phase, String label) {
        List<Long> latencies = new ArrayList<>(requestCount);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger errorCount = new AtomicInteger(0);
        AtomicInteger completedCount = new AtomicInteger(0);

        // Calculate appropriate timeout based on content
        int timeoutSeconds = "sleep".equals(mode) ? Math.max(60, longSleepMs / 1000 + 30) : 60;

        long startTime = System.nanoTime();

        // Create all requests
        List<CompletableFuture<Long>> futures = new ArrayList<>(requestCount);
        for (int i = 0; i < requestCount; i++) {
            CompletableFuture<Long> future = sendRequestAsync(content, timeoutSeconds)
                    .thenApply(latency -> {
                        if (latency >= 0) {
                            successCount.incrementAndGet();
                        } else {
                            errorCount.incrementAndGet();
                        }
                        int completed = completedCount.incrementAndGet();
                        if (completed % 100 == 0 || completed == requestCount) {
                            System.out.printf(Locale.ROOT, "\r  Progress: %d/%d (%.1f%%)    ",
                                    completed, requestCount, (100.0 * completed / requestCount));
                        }
                        return latency;
                    });
            futures.add(future);
        }

        // Wait for all to complete
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

        long endTime = System.nanoTime();
        double totalTimeSeconds = (endTime - startTime) / 1_000_000_000.0;

        System.out.println(); // New line after progress

        // Collect latencies
        for (CompletableFuture<Long> future : futures) {
            try {
                long latency = future.get();
                if (latency >= 0) {
                    latencies.add(latency);
                }
            } catch (Exception e) {
                // Already counted as error
            }
        }

        return calculateResults(latencies, successCount.get(), errorCount.get(), totalTimeSeconds);
    }

    /**
     * Sends a request synchronously and returns the latency in milliseconds.
     * Returns -1 on error.
     */
    private long sendRequestSync(byte[] content, int timeoutSeconds) {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + endpoint))
                .header("Content-Type", "application/mock+xml")
                .header("Accept", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofByteArray(content))
                .timeout(Duration.ofSeconds(timeoutSeconds))
                .build();

        long startTime = System.nanoTime();

        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            long latency = (System.nanoTime() - startTime) / 1_000_000; // Convert to ms
            if (response.statusCode() == 200) {
                return latency;
            } else {
                System.err.printf(Locale.ROOT, "%nUnexpected status %d: %s%n",
                        response.statusCode(),
                        response.body().substring(0, Math.min(200, response.body().length())));
                return -1L;
            }
        } catch (Exception e) {
            System.err.printf(Locale.ROOT, "%nRequest failed: %s%n", e.getMessage());
            return -1L;
        }
    }

    private CompletableFuture<Long> sendRequestAsync(byte[] content, int timeoutSeconds) {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + endpoint))
                .header("Content-Type", "application/mock+xml")
                .header("Accept", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofByteArray(content))
                .timeout(Duration.ofSeconds(timeoutSeconds))
                .build();

        long startTime = System.nanoTime();

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    long latency = (System.nanoTime() - startTime) / 1_000_000; // Convert to ms
                    if (response.statusCode() == 200) {
                        return latency;
                    } else {
                        System.err.printf(Locale.ROOT, "%nUnexpected status %d: %s%n",
                                response.statusCode(),
                                response.body().substring(0, Math.min(200, response.body().length())));
                        return -1L;
                    }
                })
                .exceptionally(e -> {
                    System.err.printf(Locale.ROOT, "%nRequest failed: %s%n", e.getMessage());
                    return -1L;
                });
    }

    private BenchmarkResult calculateResults(List<Long> latencies, int successCount,
                                              int errorCount, double totalTimeSeconds) {
        if (latencies.isEmpty()) {
            return new BenchmarkResult(0, 0, 0, 0, 0, 0, successCount, errorCount);
        }

        long[] sortedLatencies = latencies.stream().mapToLong(Long::longValue).sorted().toArray();
        LongSummaryStatistics stats = Arrays.stream(sortedLatencies).summaryStatistics();

        double throughput = successCount / totalTimeSeconds;
        double avgLatency = stats.getAverage();
        double p50 = percentile(sortedLatencies, 50);
        double p95 = percentile(sortedLatencies, 95);
        double p99 = percentile(sortedLatencies, 99);
        double maxLatency = stats.getMax();

        return new BenchmarkResult(throughput, avgLatency, p50, p95, p99, maxLatency, successCount, errorCount);
    }

    private double percentile(long[] sortedValues, int percentile) {
        if (sortedValues.length == 0) return 0;
        int index = (int) Math.ceil(percentile / 100.0 * sortedValues.length) - 1;
        return sortedValues[Math.max(0, Math.min(index, sortedValues.length - 1))];
    }

    private void printResults(BenchmarkResult result, String label) {
        System.out.println();
        System.out.printf(Locale.ROOT, "Results for %s:%n", label);
        System.out.printf(Locale.ROOT, "  Throughput:     %.2f requests/second%n", result.throughput);
        System.out.printf(Locale.ROOT, "  Avg Latency:    %.2f ms%n", result.avgLatencyMs);
        System.out.printf(Locale.ROOT, "  P50 Latency:    %.2f ms%n", result.p50LatencyMs);
        System.out.printf(Locale.ROOT, "  P95 Latency:    %.2f ms%n", result.p95LatencyMs);
        System.out.printf(Locale.ROOT, "  P99 Latency:    %.2f ms%n", result.p99LatencyMs);
        System.out.printf(Locale.ROOT, "  Max Latency:    %.2f ms%n", result.maxLatencyMs);
        System.out.printf(Locale.ROOT, "  Success:        %d%n", result.successCount);
        System.out.printf(Locale.ROOT, "  Errors:         %d%n", result.errorCount);
    }

    private void shutdown() {
        taskExecutor.shutdown();
        httpExecutor.shutdown();
    }

    public static void main(String[] args) {
        // Parse arguments
        String url = "http://localhost:9998";
        String endpoint = "/tika";
        int threads = 4;
        int count = 1000;
        int warmup = 100;
        int repeats = 1;
        String mode = "size";
        boolean syncMode = true; // default to sync (realistic)
        int smallKb = 1;
        int largeKb = 100;
        int shortMs = 10;
        int longMs = 5000;

        for (String arg : args) {
            if (arg.startsWith("--url=")) {
                url = arg.substring(6);
            } else if (arg.startsWith("--endpoint=")) {
                endpoint = arg.substring(11);
            } else if (arg.startsWith("--threads=")) {
                threads = Integer.parseInt(arg.substring(10));
            } else if (arg.startsWith("--count=")) {
                count = Integer.parseInt(arg.substring(8));
            } else if (arg.startsWith("--warmup=")) {
                warmup = Integer.parseInt(arg.substring(9));
            } else if (arg.startsWith("--repeats=")) {
                repeats = Integer.parseInt(arg.substring(10));
            } else if (arg.startsWith("--mode=")) {
                mode = arg.substring(7);
            } else if (arg.equals("--sync")) {
                syncMode = true;
            } else if (arg.equals("--async")) {
                syncMode = false;
            } else if (arg.startsWith("--small-kb=")) {
                smallKb = Integer.parseInt(arg.substring(11));
            } else if (arg.startsWith("--large-kb=")) {
                largeKb = Integer.parseInt(arg.substring(11));
            } else if (arg.startsWith("--short-ms=")) {
                shortMs = Integer.parseInt(arg.substring(11));
            } else if (arg.startsWith("--long-ms=")) {
                longMs = Integer.parseInt(arg.substring(10));
            } else if (arg.equals("--help") || arg.equals("-h")) {
                printHelp();
                return;
            }
        }

        if (!mode.equals("size") && !mode.equals("sleep")) {
            System.err.println("Invalid mode: " + mode + ". Must be 'size' or 'sleep'.");
            System.exit(1);
        }

        TikaServerBenchmark benchmark = new TikaServerBenchmark(
                url, endpoint, threads, count, warmup, repeats, mode, syncMode, smallKb, largeKb, shortMs, longMs);

        try {
            benchmark.run();
        } catch (Exception e) {
            System.err.println("Benchmark failed: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }

    private static void printHelp() {
        System.out.println("Tika Server Performance Benchmark");
        System.out.println();
        System.out.println("Usage: java TikaServerBenchmark [options]");
        System.out.println();
        System.out.println("Options:");
        System.out.println("  --url=URL          Base URL of tika-server (default: http://localhost:9998)");
        System.out.println("  --endpoint=PATH    Endpoint to test: /tika or /rmeta (default: /tika)");
        System.out.println("  --threads=N        Number of client threads (default: 4)");
        System.out.println("  --count=N          Number of requests per test (default: 1000)");
        System.out.println("  --warmup=N         Number of initial warmup requests (default: 100)");
        System.out.println("  --repeats=N        Number of times to repeat the benchmark (default: 1)");
        System.out.println("  --mode=MODE        Test mode: 'size' or 'sleep' (default: size)");
        System.out.println("  --sync             Synchronous: each thread waits for response before next request (default)");
        System.out.println("  --async            Asynchronous: all requests sent immediately (stress test)");
        System.out.println();
        System.out.println("Size mode options (tests I/O throughput):");
        System.out.println("  --small-kb=N       Size of small files in KB (default: 1)");
        System.out.println("  --large-kb=N       Size of large files in KB (default: 100)");
        System.out.println();
        System.out.println("Sleep mode options (tests process forking overhead):");
        System.out.println("  --short-ms=N       Short sleep duration in ms (default: 10)");
        System.out.println("  --long-ms=N        Long sleep duration in ms (default: 5000)");
        System.out.println();
        System.out.println("Note: Each benchmark also runs a per-benchmark warmup of 10*threads requests");
        System.out.println("      that is not counted towards the statistics.");
        System.out.println();
        System.out.println("Examples:");
        System.out.println("  # Realistic test with 4 threads (default sync mode)");
        System.out.println("  java TikaServerBenchmark --mode=sleep --threads=4 --short-ms=100 --long-ms=1000");
        System.out.println();
        System.out.println("  # Stress test with async mode");
        System.out.println("  java TikaServerBenchmark --mode=sleep --threads=4 --async --count=500");
        System.out.println();
        System.out.println("  # Test /rmeta endpoint with 3 repeats for more stable results");
        System.out.println("  java TikaServerBenchmark --endpoint=/rmeta --mode=size --repeats=3");
    }

    private static class BenchmarkResult {
        final double throughput;
        final double avgLatencyMs;
        final double p50LatencyMs;
        final double p95LatencyMs;
        final double p99LatencyMs;
        final double maxLatencyMs;
        final int successCount;
        final int errorCount;

        BenchmarkResult(double throughput, double avgLatencyMs, double p50LatencyMs,
                        double p95LatencyMs, double p99LatencyMs, double maxLatencyMs,
                        int successCount, int errorCount) {
            this.throughput = throughput;
            this.avgLatencyMs = avgLatencyMs;
            this.p50LatencyMs = p50LatencyMs;
            this.p95LatencyMs = p95LatencyMs;
            this.p99LatencyMs = p99LatencyMs;
            this.maxLatencyMs = maxLatencyMs;
            this.successCount = successCount;
            this.errorCount = errorCount;
        }
    }
}
