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
package org.apache.tika.pipes.bench;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.Locale;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.AsyncAppender;
import org.apache.logging.log4j.core.appender.FileAppender;
import org.apache.logging.log4j.core.config.AppenderRef;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.LoggerConfig;
import org.apache.logging.log4j.core.layout.PatternLayout;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;

import org.apache.tika.config.loader.TikaJsonConfig;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.pipes.api.FetchEmitTuple;
import org.apache.tika.pipes.api.PipesResult;
import org.apache.tika.pipes.api.emitter.EmitKey;
import org.apache.tika.pipes.api.fetcher.FetchKey;
import org.apache.tika.pipes.core.PipesConfig;
import org.apache.tika.pipes.core.PipesParser;
import org.apache.tika.pipes.core.PluginsTestHelper;

/**
 * Benchmark harness that drives a corpus through the pipes pipeline and writes
 * one PIPES_TIMING TSV-friendly line per parse to a file for offline analysis.
 * <p>
 * <b>Run:</b>
 * <pre>
 * ./mvnw test -pl tika-pipes/tika-pipes-integration-tests \
 *   -Dtest=PipesBenchmark -Dpipes.bench.run=true \
 *   -Dpipes.bench.corpus=&lt;corpus dir&gt; \
 *   [-Dpipes.bench.mock.ok=50] [-Dpipes.bench.mock.oom=5] [-Dpipes.bench.mock.timeout=2] \
 *   [-Dpipes.bench.timing.out=&lt;path&gt;] [-Dpipes.bench.threads=8]
 * </pre>
 * <p>
 * The harness configures the parent JVM's log4j2 to route the
 * {@code org.apache.tika.pipes.timing} logger to the timing file. The forked
 * PipesServer JVMs do not emit timing logs themselves — they stamp per-stage
 * timings onto the PipesResult and the parent's PipesClient logs the line.
 */
public class PipesBenchmark {

    private static final String MOCK_OK_XML = "<?xml version=\"1.0\" encoding=\"UTF-8\" ?>" +
            "<mock>" +
            "<metadata action=\"add\" name=\"dc:creator\">Bench OK Author</metadata>" +
            "<write element=\"p\">Bench OK content</write>" +
            "</mock>";

    private static final String FETCHER_NAME = "fsf";
    private static final String EMITTER_NAME = "fse";

    @Test
    @EnabledIfSystemProperty(named = "pipes.bench.run", matches = "true")
    public void run(@TempDir Path tmp) throws Exception {
        Path corpusSource = readCorpusDir();
        int mockOk = Integer.getInteger("pipes.bench.mock.ok", 50);
        int threads = Integer.getInteger("pipes.bench.threads", 8);
        Path timingOut = Paths.get(System.getProperty("pipes.bench.timing.out",
                tmp.resolve("pipes-timing.tsv").toString())).toAbsolutePath();

        int warmupPasses = Integer.getInteger("pipes.bench.warmup-passes", 0);

        Path inputDir = tmp.resolve("input");
        Path outputDir = tmp.resolve("output");
        Files.createDirectories(inputDir);
        Files.createDirectories(outputDir);

        // Stage corpus + mock files
        copyCorpus(corpusSource, inputDir);
        writeMocks(inputDir, "bench-ok-", mockOk, MOCK_OK_XML);

        long fileCount = countFiles(inputDir);
        System.out.println("PipesBenchmark: corpus=" + inputDir + " files=" + fileCount);
        System.out.println("PipesBenchmark: warmup-passes=" + warmupPasses);
        System.out.println("PipesBenchmark: timing log -> " + timingOut);

        Path tikaConfigPath = PluginsTestHelper.getFileSystemFetcherConfig(
                "tika-config-bench.json", tmp, inputDir, outputDir, false);
        TikaJsonConfig tikaJsonConfig = TikaJsonConfig.load(tikaConfigPath);
        PipesConfig pipesConfig = PipesConfig.load(tikaJsonConfig);
        Integer numClientsOverride = Integer.getInteger("pipes.bench.num-clients");
        if (numClientsOverride != null) {
            pipesConfig.setNumClients(numClientsOverride);
        }
        if (Boolean.getBoolean("pipes.bench.shared-server")) {
            pipesConfig.setUseSharedServer(true);
        }
        if (Boolean.getBoolean("pipes.bench.cap-cpu")) {
            // Per-client mode runs N forked JVMs. Each one defaults its GC, JIT,
            // and common ForkJoinPool sizes to Runtime.availableProcessors(), so
            // 4 JVMs on 16 cores spawn ~64 GC threads + ~60 FJP threads + 16 JIT
            // threads -- way more than the 4 actually-active parse threads.
            // We size each JVM to a fair slice of the *non-parent* CPU budget so
            // the parent isn't starved (which causes pathological req_socket_us
            // tail latency from preemption between clock reads).
            int cores = Runtime.getRuntime().availableProcessors();
            int parentReserved = Integer.getInteger("pipes.bench.parent-cores", 2);
            int n = Math.max(1, pipesConfig.getNumClients());
            int forkBudget = Math.max(1, cores - parentReserved);
            int slice = Math.max(1, forkBudget / n);
            pipesConfig.getForkedJvmArgs().add("-XX:ActiveProcessorCount=" + slice);
            System.out.println("PipesBenchmark: capping forked JVMs to " + slice +
                    " active CPUs (host=" + cores + ", parentReserved=" + parentReserved +
                    ", numClients=" + n + ")");
        }
        System.out.println("PipesBenchmark: numClients=" + pipesConfig.getNumClients() +
                " sharedServer=" + pipesConfig.isUseSharedServer() +
                " forkedJvmArgs=" + pipesConfig.getForkedJvmArgs());

        long progressInterval = Long.getLong("pipes.bench.progress.interval", 1000);
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        long benchStart = System.nanoTime();

        BenchCounters measured;
        try (PipesParser pipesParser = PipesParser.load(tikaJsonConfig, pipesConfig, tikaConfigPath)) {
            // Warmup passes - run BEFORE attaching the timing appender so
            // these parses don't pollute the measurement TSV.
            for (int pass = 0; pass < warmupPasses; pass++) {
                long warmStart = System.nanoTime();
                runOnePass(pipesParser, executor, inputDir, progressInterval, "warmup-" + (pass + 1));
                long warmMs = (System.nanoTime() - warmStart) / 1_000_000L;
                System.out.println("PipesBenchmark: warmup pass " + (pass + 1) + " done in " +
                        warmMs + "ms");
            }

            // Attach the timing appender now that JVMs are warm
            attachTimingAppender(timingOut);

            long measureStart = System.nanoTime();
            measured = runOnePass(pipesParser, executor, inputDir, progressInterval, "measured");
            long measureMs = (System.nanoTime() - measureStart) / 1_000_000L;
            System.out.println("PipesBenchmark: measured pass done in " + measureMs + "ms");
        } finally {
            executor.shutdown();
            executor.awaitTermination(60, TimeUnit.SECONDS);
        }
        long benchWallMs = (System.nanoTime() - benchStart) / 1_000_000L;

        // Flush log4j so the timing file is fully written
        LogManager.shutdown();

        System.out.println("PipesBenchmark: done in " + benchWallMs + "ms");
        System.out.println("  files=" + measured.total.get() +
                " success=" + measured.success.get() +
                " other=" + measured.other.get());
        System.out.println("  timing log: " + timingOut);
    }

    /** Per-pass tally - counters only, no result accumulation. */
    private static final class BenchCounters {
        final AtomicInteger total = new AtomicInteger();
        final AtomicInteger success = new AtomicInteger();
        final AtomicInteger other = new AtomicInteger();
    }

    /**
     * Submits all corpus files via an ExecutorCompletionService, drains
     * results as they complete (so {@link PipesResult} instances aren't
     * retained), and prints a heartbeat every {@code progressInterval}
     * completions. Tally is kept in counters only -- safe for million-file
     * runs.
     */
    private static BenchCounters runOnePass(PipesParser pipesParser, ExecutorService executor,
                                            Path inputDir, long progressInterval,
                                            String passName) throws Exception {
        ExecutorCompletionService<PipesResult> ecs = new ExecutorCompletionService<>(executor);
        long submitted = 0;
        try (var stream = Files.walk(inputDir)) {
            for (var iter = stream.filter(Files::isRegularFile).iterator(); iter.hasNext(); ) {
                Path p = iter.next();
                String key = inputDir.relativize(p).toString();
                ecs.submit(() -> pipesParser.parse(new FetchEmitTuple(
                        key,
                        new FetchKey(FETCHER_NAME, key),
                        new EmitKey(EMITTER_NAME, ""),
                        new Metadata(),
                        new ParseContext(),
                        FetchEmitTuple.ON_PARSE_EXCEPTION.SKIP)));
                submitted++;
            }
        }

        BenchCounters counters = new BenchCounters();
        long passStart = System.nanoTime();
        long lastReportNanos = passStart;
        int lastReportedTotal = 0;
        for (long i = 0; i < submitted; i++) {
            PipesResult r = ecs.take().get();
            // Discard r reference asap so the heap doesn't accumulate metadata payloads.
            int total = counters.total.incrementAndGet();
            if (r.isSuccess()) {
                counters.success.incrementAndGet();
            } else {
                counters.other.incrementAndGet();
            }
            r = null;
            if (progressInterval > 0 && total % progressInterval == 0) {
                long now = System.nanoTime();
                long elapsedMs = (now - passStart) / 1_000_000L;
                long sinceLastMs = Math.max(1, (now - lastReportNanos) / 1_000_000L);
                int sinceLast = total - lastReportedTotal;
                double overallRate = total * 1000.0 / Math.max(1, elapsedMs);
                double recentRate = sinceLast * 1000.0 / sinceLastMs;
                System.out.printf(Locale.ROOT, 
                        "PipesBenchmark[%s]: %,d/%,d done elapsed=%,dms overall=%.0f f/s recent=%.0f f/s success=%,d other=%,d%n",
                        passName, total, submitted, elapsedMs,
                        overallRate, recentRate,
                        counters.success.get(), counters.other.get());
                lastReportNanos = now;
                lastReportedTotal = total;
            }
        }
        return counters;
    }

    private static Path readCorpusDir() {
        String s = System.getProperty("pipes.bench.corpus");
        if (s == null || s.isBlank()) {
            throw new IllegalArgumentException(
                    "set -Dpipes.bench.corpus=<path-to-corpus-dir>");
        }
        Path p = Paths.get(s);
        if (!Files.isDirectory(p)) {
            throw new IllegalArgumentException("corpus dir does not exist: " + p);
        }
        return p;
    }

    private static void copyCorpus(Path src, Path dst) throws IOException {
        Files.walkFileTree(src, new SimpleFileVisitor<>() {
            @Override
            public java.nio.file.FileVisitResult visitFile(Path file, BasicFileAttributes attrs)
                    throws IOException {
                Path rel = src.relativize(file);
                Path target = dst.resolve(rel.toString());
                if (target.getParent() != null) {
                    Files.createDirectories(target.getParent());
                }
                Files.copy(file, target);
                return java.nio.file.FileVisitResult.CONTINUE;
            }
        });
    }

    private static void writeMocks(Path dir, String prefix, int count, String xml) throws IOException {
        for (int i = 0; i < count; i++) {
            Files.writeString(dir.resolve(prefix + i + ".xml"), xml, StandardCharsets.UTF_8);
        }
    }

    private static long countFiles(Path dir) throws IOException {
        try (var stream = Files.walk(dir)) {
            return stream.filter(Files::isRegularFile).count();
        }
    }

    /**
     * Programmatically attaches a file appender to the {@code org.apache.tika.pipes.timing}
     * logger so each PIPES_TIMING line lands in the requested TSV file.
     * <p>
     * The {@link FileAppender} is wrapped in an {@link AsyncAppender} so concurrent
     * PipesClient threads don't contend on the synchronous file write at
     * high throughput (matters at million-file scale).
     */
    private static void attachTimingAppender(Path timingOut) throws IOException {
        if (timingOut.getParent() != null) {
            Files.createDirectories(timingOut.getParent());
        }
        LoggerContext ctx = (LoggerContext) LogManager.getContext(false);
        Configuration cfg = ctx.getConfiguration();

        PatternLayout layout = PatternLayout.newBuilder()
                .withPattern("%m%n")
                .withConfiguration(cfg)
                .build();

        FileAppender fileAppender = FileAppender.newBuilder()
                .setName("PipesBenchTimingFile")
                .withFileName(timingOut.toString())
                .withAppend(false)
                .setLayout(layout)
                .setConfiguration(cfg)
                .build();
        fileAppender.start();
        cfg.addAppender(fileAppender);

        AppenderRef fileRef = AppenderRef.createAppenderRef("PipesBenchTimingFile", Level.INFO, null);
        AsyncAppender asyncAppender = AsyncAppender.newBuilder()
                .setName("PipesBenchTimingAsync")
                .setConfiguration(cfg)
                .setAppenderRefs(new AppenderRef[]{fileRef})
                .setBlocking(true)
                .setBufferSize(8192)
                .build();
        asyncAppender.start();
        cfg.addAppender(asyncAppender);

        AppenderRef asyncRef = AppenderRef.createAppenderRef(
                "PipesBenchTimingAsync", Level.INFO, null);
        AppenderRef[] refs = new AppenderRef[]{asyncRef};
        LoggerConfig loggerConfig = LoggerConfig.createLogger(false, Level.INFO,
                "org.apache.tika.pipes.timing", "true", refs, null, cfg, null);
        loggerConfig.addAppender(asyncAppender, Level.INFO, null);
        cfg.addLogger("org.apache.tika.pipes.timing", loggerConfig);

        ctx.updateLoggers();
    }
}
