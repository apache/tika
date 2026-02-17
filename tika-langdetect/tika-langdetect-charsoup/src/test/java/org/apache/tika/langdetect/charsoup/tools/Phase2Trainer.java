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
package org.apache.tika.langdetect.charsoup.tools;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.DoubleAdder;

import org.apache.tika.langdetect.charsoup.FeatureExtractor;
import org.apache.tika.langdetect.charsoup.ScriptAwareFeatureExtractor;

/**
 * Phase 2 multinomial logistic regression trainer.
 * <p>
 * Streams training data from disk each epoch to avoid loading
 * the full corpus into memory. Data must be pre-shuffled during
 * data preparation.
 * <p>
 * Key features:
 * <ul>
 *   <li><b>Streaming</b>: reads training file from disk each
 *       epoch; flat memory usage regardless of corpus size</li>
 *   <li><b>Bucket-major layout</b>: weights as
 *       {@code [bucket][class]} for cache-friendly sparse
 *       feature access</li>
 *   <li><b>Adam → SGD</b>: Adam for fast convergence, then
 *       SGD for fine-tuning</li>
 *   <li><b>Within-epoch early stopping</b>: checkpoints on
 *       dev subsample every N sentences</li>
 *   <li><b>Across-epoch early stopping</b>: full dev eval
 *       after each epoch, patience-based</li>
 *   <li><b>Hogwild!</b>: lock-free parallel weight updates
 *       via batched line dispatch to worker threads</li>
 *   <li><b>Buffer reuse</b>: thread-local feature/logit
 *       buffers to minimize GC pressure</li>
 * </ul>
 */
public class Phase2Trainer {

    /**
     * VarHandle for {@code float[]} element access.
     * Swap between plain get/set and getOpaque/setOpaque
     * to experiment with visibility guarantees vs JIT
     * optimization. Plain access lets the JIT vectorize;
     * opaque prevents register caching across iterations.
     */
    private static final VarHandle FA =
            MethodHandles.arrayElementVarHandle(float[].class);

    private static float getO(float[] a, int i) {
        return (float) FA.get(a, i);  // plain access
    }

    private static void setO(float[] a, int i, float v) {
        FA.set(a, i, v);  // plain access
    }

    private final int numBuckets;

    // --- Optimizer hyperparameters ---
    private float adamLr = 0.001f;
    private float adamBeta1 = 0.9f;
    private float adamBeta2 = 0.999f;
    private float adamEpsilon = 1e-8f;

    private float sgdLrStart = 0.01f;
    private float sgdLrEnd = 0.001f;

    private float l2Lambda = 1e-5f;

    // --- Training schedule ---
    private int adamEpochs = 2;
    private int maxEpochs = 5;

    // --- Early stopping: within-epoch ---
    private int checkpointInterval = 200_000;
    private int rollingWindow = 5;
    private double withinEpochThreshold = 0.005;

    // --- Early stopping: across-epoch ---
    private int patience = 2;
    private double acrossEpochThreshold = 0.001;
    private int devSubsampleSize = 20_000;

    // --- Mini-batch ---
    /**
     * Gradient mini-batch size. Gradients are accumulated
     * over this many samples before a single Adam/SGD
     * update. Standard ML mini-batch — NOT just I/O
     * batching.
     */
    private int miniBatchSize = 128;

    // --- I/O batching ---
    /**
     * Lines read from disk before dispatching to threads.
     * Must be large enough that each thread gets a
     * meaningful contiguous slice.
     */
    private int batchSize = 100_000;

    // --- Threading ---
    /**
     * Threads for SGD epochs (Hogwild-safe).
     * Defaults to all available cores.
     */
    private int sgdThreads = Runtime.getRuntime()
            .availableProcessors();
    /**
     * Threads for Adam epochs. Each thread gets its own
     * moment arrays (m, v), so memory scales linearly:
     * {@code adamThreads * 2 * numBuckets * numClasses * 4}
     * bytes. Default 1 (single-threaded, no extra memory).
     */
    private int adamThreads = 1;
    private long seed = 42L;
    private boolean verbose = true;
    private boolean preprocessed = false;

    // --- Model parameters: BUCKET-MAJOR layout ---
    private float[][] weights; // [bucket][class]
    private float[] biases;    // [class]
    private String[] labels;
    private Map<String, Integer> labelIndex;
    private int numClasses;

    // --- Chunk byte offsets for seek-based shuffling ---
    private long[] chunkByteOffsets;

    // --- Adam state (same bucket-major layout) ---
    // Shared state (single-threaded Adam)
    private float[][] mW;      // [bucket][class] first moment
    private float[][] vW;      // [bucket][class] second moment
    private float[] mBias;     // [class]
    private float[] vBias;     // [class]
    private AtomicLong globalStep;

    // Per-thread Adam state (Hogwild Adam)
    private float[][][] perThreadMW;   // [thread][bucket][class]
    private float[][][] perThreadVW;   // [thread][bucket][class]
    private float[][] perThreadMBias;  // [thread][class]
    private float[][] perThreadVBias;  // [thread][class]
    private long[] perThreadStep;      // [thread]

    public Phase2Trainer(int numBuckets) {
        this.numBuckets = numBuckets;
    }

    // --- Builder-style setters ---

    public Phase2Trainer setAdamLr(float lr) {
        this.adamLr = lr;
        return this;
    }

    public Phase2Trainer setSgdLr(float start, float end) {
        this.sgdLrStart = start;
        this.sgdLrEnd = end;
        return this;
    }

    public Phase2Trainer setL2Lambda(float lambda) {
        this.l2Lambda = lambda;
        return this;
    }

    public Phase2Trainer setAdamEpochs(int epochs) {
        this.adamEpochs = epochs;
        return this;
    }

    public Phase2Trainer setMaxEpochs(int epochs) {
        this.maxEpochs = epochs;
        return this;
    }

    public Phase2Trainer setCheckpointInterval(int interval) {
        this.checkpointInterval = interval;
        return this;
    }

    public Phase2Trainer setPatience(int patience) {
        this.patience = patience;
        return this;
    }

    public Phase2Trainer setDevSubsampleSize(int size) {
        this.devSubsampleSize = size;
        return this;
    }

    public Phase2Trainer setMiniBatchSize(int size) {
        this.miniBatchSize = size;
        return this;
    }

    public Phase2Trainer setBatchSize(int size) {
        this.batchSize = size;
        return this;
    }

    public Phase2Trainer setNumThreads(int threads) {
        return setSgdThreads(threads).setAdamThreads(1);
    }

    public Phase2Trainer setSgdThreads(int threads) {
        if (threads < 1) {
            throw new IllegalArgumentException(
                    "sgdThreads must be >= 1");
        }
        this.sgdThreads = threads;
        return this;
    }

    public Phase2Trainer setAdamThreads(int threads) {
        if (threads < 1) {
            throw new IllegalArgumentException(
                    "adamThreads must be >= 1");
        }
        this.adamThreads = threads;
        return this;
    }

    public Phase2Trainer setSeed(long seed) {
        this.seed = seed;
        return this;
    }

    public Phase2Trainer setVerbose(boolean verbose) {
        this.verbose = verbose;
        return this;
    }

    public Phase2Trainer setPreprocessed(boolean preprocessed) {
        this.preprocessed = preprocessed;
        return this;
    }

    // ================================================================
    //  Training entry point
    // ================================================================

    /**
     * Train the model by streaming training data from disk.
     * <p>
     * The training file is read sequentially each epoch.
     * Data must be pre-shuffled during data preparation.
     *
     * @param trainFile path to preprocessed training file
     *                  (tab-separated: {@code lang\ttext})
     * @param devData   dev set (kept in memory for eval)
     * @throws IOException if reading fails
     */
    public void train(Path trainFile,
                      List<LabeledSentence> devData)
            throws IOException {
        // First pass: scan file to build label index,
        // count lines, and find chunk byte offsets
        long scanStart = System.nanoTime();
        int totalLines = scanLabels(trainFile);
        chunkByteOffsets = scanChunkOffsets(trainFile);
        if (verbose) {
            System.out.printf(Locale.US,
                    "Scanned: %,d lines, %d classes, "
                            + "%d chunks  [%.1f s]%n",
                    totalLines, numClasses,
                    chunkByteOffsets.length,
                    elapsed(scanStart));
        }

        // Initialize weights and Adam state
        weights = new float[numBuckets][numClasses];
        biases = new float[numClasses];
        globalStep = new AtomicLong(0);

        if (adamThreads > 1) {
            // Per-thread moment arrays for Hogwild Adam
            perThreadMW =
                    new float[adamThreads][numBuckets][numClasses];
            perThreadVW =
                    new float[adamThreads][numBuckets][numClasses];
            perThreadMBias =
                    new float[adamThreads][numClasses];
            perThreadVBias =
                    new float[adamThreads][numClasses];
            perThreadStep = new long[adamThreads];
            mW = null;
            vW = null;
            mBias = null;
            vBias = null;
        } else {
            // Shared moment arrays for single-thread Adam
            mW = new float[numBuckets][numClasses];
            vW = new float[numBuckets][numClasses];
            mBias = new float[numClasses];
            vBias = new float[numClasses];
            perThreadMW = null;
            perThreadVW = null;
            perThreadMBias = null;
            perThreadVBias = null;
            perThreadStep = null;
        }

        // Sample fixed dev subsample for within-epoch checks
        List<LabeledSentence> devSubsample =
                sampleDevSubset(devData, devSubsampleSize);

        if (verbose) {
            System.out.printf(Locale.US,
                    "Training: %,d samples, %d classes, "
                            + "%,d buckets, "
                            + "Adam=%d thread(s), "
                            + "SGD=%d thread(s)%n",
                    totalLines, numClasses,
                    numBuckets, adamThreads, sgdThreads);
            System.out.printf(Locale.US,
                    "Schedule: Adam(lr=%.4f) x%d epochs, "
                            + "SGD(lr=%.4f->%.4f) x%d max%n",
                    adamLr, adamEpochs,
                    sgdLrStart, sgdLrEnd,
                    maxEpochs - adamEpochs);
            System.out.printf(Locale.US,
                    "Early stop: checkpoint every %,d sents "
                            + "(window=%d, thresh=%.4f), "
                            + "patience=%d%n",
                    checkpointInterval, rollingWindow,
                    withinEpochThreshold, patience);
            System.out.printf(Locale.US,
                    "Dev subsample: %,d sents, "
                            + "ioBatch=%,d lines, "
                            + "miniBatch=%d%n",
                    devSubsample.size(), batchSize,
                    miniBatchSize);
        }

        int maxThreads = Math.max(adamThreads, sgdThreads);
        ExecutorService pool = maxThreads > 1
                ? Executors.newFixedThreadPool(maxThreads)
                : null;

        double bestDevF1 = Double.NEGATIVE_INFINITY;
        int epochsWithoutImprovement = 0;

        try {
            for (int epoch = 0; epoch < maxEpochs; epoch++) {
                long epochStart = System.nanoTime();
                boolean useAdam = epoch < adamEpochs;
                float sgdLr = 0f;
                if (!useAdam) {
                    int sgdEpoch = epoch - adamEpochs;
                    int totalSgd = Math.max(1,
                            maxEpochs - adamEpochs);
                    float frac = totalSgd == 1 ? 0f
                            : (float) sgdEpoch
                            / (totalSgd - 1);
                    sgdLr = sgdLrStart
                            + frac * (sgdLrEnd - sgdLrStart);
                }

                String optLabel = useAdam
                        ? String.format(Locale.US,
                        "Adam(lr=%.4f)", adamLr)
                        : String.format(Locale.US,
                        "SGD(lr=%.4f)", sgdLr);

                // Stream the training file
                EpochResult result = trainEpochStreaming(
                        pool, trainFile, useAdam, sgdLr,
                        devSubsample, epoch);

                long epochMs = (System.nanoTime()
                        - epochStart) / 1_000_000;

                // Full dev eval
                F1Result devResult = devData != null
                        ? evaluateMacroF1(devData) : null;
                double devF1 = devResult != null
                        ? devResult.f1 : Double.NaN;

                if (verbose) {
                    System.out.printf(Locale.US,
                            "Epoch %d/%d  %s  "
                                    + "avgLoss=%.4f  "
                                    + "devF1=%.4f (%d langs)  "
                                    + "processed=%,d/%,d"
                                    + "%s  [%,d ms]%n",
                            epoch + 1, maxEpochs, optLabel,
                            result.avgLoss, devF1,
                            devResult != null
                                    ? devResult.numLangs : 0,
                            result.sentencesProcessed,
                            totalLines,
                            result.earlyStopped
                                    ? " (early-stopped)"
                                    : "",
                            epochMs);
                }

                // Across-epoch early stopping
                if (!Double.isNaN(devF1)) {
                    if (devF1 > bestDevF1
                            + acrossEpochThreshold) {
                        bestDevF1 = devF1;
                        epochsWithoutImprovement = 0;
                    } else {
                        epochsWithoutImprovement++;
                        if (epochsWithoutImprovement
                                >= patience) {
                            if (verbose) {
                                System.out.printf(Locale.US,
                                        "Stopping: no "
                                                + "improvement "
                                                + "for %d epochs "
                                                + "(best=%.4f)%n",
                                        patience, bestDevF1);
                            }
                            break;
                        }
                    }
                }
            }
        } finally {
            if (pool != null) {
                pool.shutdown();
            }
        }

        // Free Adam state
        mW = null;
        vW = null;
        mBias = null;
        vBias = null;
        perThreadMW = null;
        perThreadVW = null;
        perThreadMBias = null;
        perThreadVBias = null;
        perThreadStep = null;
    }

    // ================================================================
    //  File scanning
    // ================================================================

    /**
     * Scan the training file to discover all language labels
     * and count lines. Builds {@link #labels} and
     * {@link #labelIndex}.
     *
     * @return total number of lines
     */
    private int scanLabels(Path file) throws IOException {
        Map<String, Integer> idx = new HashMap<>();
        List<String> labelList = new ArrayList<>();
        int count = 0;
        try (BufferedReader br = Files.newBufferedReader(
                file, StandardCharsets.UTF_8)) {
            String line;
            while ((line = br.readLine()) != null) {
                int tab = line.indexOf('\t');
                if (tab < 0) {
                    continue;
                }
                String lang = line.substring(0, tab);
                if (!idx.containsKey(lang)) {
                    idx.put(lang, labelList.size());
                    labelList.add(lang);
                }
                count++;
            }
        }
        this.labels = labelList.toArray(new String[0]);
        Arrays.sort(this.labels);
        this.labelIndex = new HashMap<>();
        for (int i = 0; i < labels.length; i++) {
            this.labelIndex.put(labels[i], i);
        }
        this.numClasses = labels.length;
        return count;
    }

    /**
     * Scan the file for byte offsets at chunk boundaries.
     * Records the byte position after every {@link #chunkSize}
     * newlines. Fast sequential scan — only counts newline
     * bytes, no UTF-8 decoding needed.
     *
     * @return array of byte offsets (first element is 0)
     */
    private long[] scanChunkOffsets(Path file)
            throws IOException {
        List<Long> offsets = new ArrayList<>();
        offsets.add(0L);
        long bytePos = 0;
        int lineCount = 0;
        try (BufferedInputStream bis =
                     new BufferedInputStream(
                             Files.newInputStream(file),
                             1 << 16)) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = bis.read(buf)) != -1) {
                for (int i = 0; i < n; i++) {
                    if (buf[i] == '\n') {
                        lineCount++;
                        if (lineCount % chunkSize == 0) {
                            offsets.add(bytePos + i + 1);
                        }
                    }
                }
                bytePos += n;
            }
        }
        return offsets.stream()
                .mapToLong(Long::longValue).toArray();
    }

    // ================================================================
    //  Streaming epoch
    // ================================================================

    private static class EpochResult {
        double avgLoss;
        int sentencesProcessed;
        boolean earlyStopped;
    }

    /**
     * Size of chunks for chunk-level shuffling. Each epoch
     * the file is read into chunks of this size, then the
     * chunks are shuffled to vary data ordering across
     * epochs. Within each chunk, lines keep their original
     * (pre-shuffled) order.
     */
    private int chunkSize = 500_000;

    public Phase2Trainer setChunkSize(int size) {
        this.chunkSize = size;
        return this;
    }

    /**
     * Train one epoch with chunk-level shuffling.
     * <p>
     * Uses pre-computed byte offsets to seek to chunks in
     * shuffled order. Only one chunk (~{@link #chunkSize}
     * lines) is in memory at a time, so this scales to
     * arbitrarily large files.
     */
    private EpochResult trainEpochStreaming(
            ExecutorService pool, Path trainFile,
            boolean useAdam, float sgdLr,
            List<LabeledSentence> devSubsample,
            int epochNum)
            throws IOException {

        // Shuffle chunk order (different per epoch)
        int numChunks = chunkByteOffsets.length;
        int[] order = new int[numChunks];
        for (int i = 0; i < numChunks; i++) {
            order[i] = i;
        }
        Random rng = new Random(seed + epochNum * 31L);
        for (int i = numChunks - 1; i > 0; i--) {
            int j = rng.nextInt(i + 1);
            int tmp = order[i];
            order[i] = order[j];
            order[j] = tmp;
        }

        DoubleAdder totalLoss = new DoubleAdder();
        int processed = 0;
        double[] recentF1 = new double[rollingWindow];
        int checkCount = 0;
        boolean earlyStopped = false;
        int nextCheckpoint = checkpointInterval;

        // Reusable buffers for one chunk
        String[] cTexts = new String[chunkSize];
        int[] cLabels = new int[chunkSize];

        for (int ci = 0; ci < numChunks && !earlyStopped;
             ci++) {
            int chunkIdx = order[ci];

            // Read one chunk by seeking to its byte offset
            int fill = readChunk(trainFile,
                    chunkByteOffsets[chunkIdx],
                    cTexts, cLabels);

            // Shuffle lines within this chunk so that
            // mini-batches mix languages instead of seeing
            // one language at a time (data is written
            // language-by-language during prep).
            shuffleParallel(cTexts, cLabels, fill,
                    new Random(seed + epochNum * 31L
                            + chunkIdx * 7L));

            // Process chunk in batches
            for (int off = 0; off < fill && !earlyStopped;
                 off += batchSize) {
                int end = Math.min(off + batchSize, fill);
                int bLen = end - off;

                if (off == 0 && bLen == fill) {
                    processBatch(pool, cTexts, cLabels,
                            fill, useAdam, sgdLr,
                            totalLoss);
                } else {
                    String[] bTexts =
                            Arrays.copyOfRange(
                                    cTexts, off, end);
                    int[] bLabs =
                            Arrays.copyOfRange(
                                    cLabels, off, end);
                    processBatch(pool, bTexts, bLabs,
                            bLen, useAdam, sgdLr,
                            totalLoss);
                }
                processed += bLen;

                if (processed >= nextCheckpoint
                        && devSubsample != null
                        && !devSubsample.isEmpty()) {
                    earlyStopped = checkpoint(
                            processed, devSubsample,
                            recentF1, checkCount);
                    checkCount++;
                    nextCheckpoint = processed
                            + checkpointInterval;
                }
            }
        }

        EpochResult r = new EpochResult();
        r.avgLoss = processed > 0
                ? totalLoss.sum() / processed : 0;
        r.sentencesProcessed = processed;
        r.earlyStopped = earlyStopped;
        return r;
    }

    /**
     * Read one chunk starting at the given byte offset.
     * Reads up to {@link #chunkSize} valid lines into
     * the provided buffers.
     *
     * @return number of lines read
     */
    private int readChunk(Path file, long byteOffset,
                          String[] texts, int[] labels)
            throws IOException {
        int fill = 0;
        try (FileChannel fc = FileChannel.open(file,
                StandardOpenOption.READ)) {
            fc.position(byteOffset);
            BufferedReader br = new BufferedReader(
                    new InputStreamReader(
                            Channels.newInputStream(fc),
                            StandardCharsets.UTF_8));
            String line;
            while (fill < chunkSize
                    && (line = br.readLine()) != null) {
                int tab = line.indexOf('\t');
                if (tab < 0) {
                    continue;
                }
                String lang = line.substring(0, tab);
                Integer idx = labelIndex.get(lang);
                if (idx == null) {
                    continue;
                }
                texts[fill] = line.substring(tab + 1);
                labels[fill] = idx;
                fill++;
            }
        }
        return fill;
    }

    /**
     * Check dev subsample F1 and return true if epoch should
     * stop early.
     */
    /** Compute L2 norm of all weight values. */
    private double weightNorm() {
        double sum = 0;
        for (int b = 0; b < numBuckets; b++) {
            float[] wb = weights[b];
            for (int c = 0; c < numClasses; c++) {
                float w = getO(wb, c);
                sum += (double) w * w;
            }
        }
        return Math.sqrt(sum);
    }

    /** Max absolute weight value (detects blowup). */
    private float maxAbsWeight() {
        float max = 0;
        for (int b = 0; b < numBuckets; b++) {
            float[] wb = weights[b];
            for (int c = 0; c < numClasses; c++) {
                float a = Math.abs(getO(wb, c));
                if (a > max) {
                    max = a;
                }
            }
        }
        return max;
    }

    private boolean checkpoint(int processed,
                               List<LabeledSentence> devSub,
                               double[] recentF1,
                               int checkCount) {
        F1Result r = evaluateMacroF1(devSub);
        int slot = checkCount % rollingWindow;
        recentF1[slot] = r.f1;

        if (verbose) {
            System.out.printf(Locale.US,
                    "    checkpoint %,d sents: "
                            + "devSubF1=%.4f (%d langs) "
                            + "wNorm=%.1f maxW=%.3f%n",
                    processed, r.f1, r.numLangs,
                    weightNorm(), maxAbsWeight());
        }

        if (checkCount + 1 >= rollingWindow) {
            double minF1 = Double.MAX_VALUE;
            double maxF1 = Double.MIN_VALUE;
            for (double d : recentF1) {
                if (d < minF1) {
                    minF1 = d;
                }
                if (d > maxF1) {
                    maxF1 = d;
                }
            }
            if (maxF1 - minF1 < withinEpochThreshold) {
                if (verbose) {
                    System.out.printf(Locale.US,
                            "    early-stop at %,d sents "
                                    + "(F1 range=%.5f "
                                    + "< %.4f)%n",
                            processed, maxF1 - minF1,
                            withinEpochThreshold);
                }
                return true;
            }
        }
        return false;
    }

    // ================================================================
    //  Batch dispatch
    // ================================================================

    /**
     * Process a batch of lines. Uses the appropriate thread
     * count based on optimizer: adamThreads for Adam epochs,
     * sgdThreads for SGD epochs.
     */
    private void processBatch(ExecutorService pool,
                              String[] texts, int[] labels,
                              int count,
                              boolean useAdam, float sgdLr,
                              DoubleAdder totalLoss) {
        int threads = useAdam ? adamThreads : sgdThreads;
        if (threads > 1 && pool != null) {
            processBatchHogwild(pool, texts, labels, count,
                    useAdam, sgdLr, totalLoss, threads);
        } else {
            processBatchSingle(texts, labels, count,
                    useAdam, sgdLr, totalLoss);
        }
    }

    private void processBatchSingle(
            String[] texts, int[] batchLabels, int count,
            boolean useAdam, float sgdLr,
            DoubleAdder totalLoss) {
        FeatureExtractor ext = createExtractor();
        int[] featureBuf = new int[numBuckets];
        float[] logitBuf = new float[numClasses];
        int[] nzBuf = new int[numBuckets];

        if (useAdam) {
            // Mini-batch Adam: accumulate gradients, then
            // apply one update per mini-batch.
            float[][] gradAccumW =
                    new float[numBuckets][numClasses];
            float[] gradAccumB = new float[numClasses];
            int mbCount = 0;

            for (int i = 0; i < count; i++) {
                extractInto(ext, texts[i], featureBuf);
                double loss = forwardGrad(featureBuf,
                        batchLabels[i], logitBuf, nzBuf);
                totalLoss.add(loss);

                // Accumulate: gradW[b][c] += grad[c]*feat[b]
                int nnz = sparseIndex(featureBuf, nzBuf);
                for (int j = 0; j < nnz; j++) {
                    int b = nzBuf[j];
                    float fv = featureBuf[b];
                    float[] ab = gradAccumW[b];
                    for (int c = 0; c < numClasses; c++) {
                        ab[c] += logitBuf[c] * fv;
                    }
                }
                for (int c = 0; c < numClasses; c++) {
                    gradAccumB[c] += logitBuf[c];
                }
                mbCount++;

                if (mbCount == miniBatchSize) {
                    applyAdamMiniBatch(gradAccumW,
                            gradAccumB, mbCount, -1);
                    mbCount = 0;
                }
            }
            if (mbCount > 0) {
                applyAdamMiniBatch(gradAccumW,
                        gradAccumB, mbCount, -1);
            }
        } else {
            // Online SGD (Hogwild-safe with multi-thread)
            for (int i = 0; i < count; i++) {
                extractInto(ext, texts[i], featureBuf);
                double loss = forwardGrad(featureBuf,
                        batchLabels[i], logitBuf, nzBuf);
                totalLoss.add(loss);
                int nnz = sparseIndex(featureBuf, nzBuf);
                sgdUpdate(featureBuf, logitBuf, nnz,
                        nzBuf, sgdLr);
            }
        }
    }

    private void processBatchHogwild(
            ExecutorService pool,
            String[] texts, int[] batchLabels, int count,
            boolean useAdam, float sgdLr,
            DoubleAdder totalLoss, int threads) {

        List<Future<?>> futures =
                new ArrayList<>(threads);
        for (int t = 0; t < threads; t++) {
            int from = (int) ((long) count * t
                    / threads);
            int to = (int) ((long) count * (t + 1)
                    / threads);
            int tid = t;
            futures.add(pool.submit(() -> {
                FeatureExtractor ext = createExtractor();
                int[] featureBuf = new int[numBuckets];
                float[] logitBuf = new float[numClasses];
                int[] nzBuf = new int[numBuckets];
                double threadLoss = 0;

                if (useAdam) {
                    // Per-thread mini-batch Adam
                    float[][] gradAccumW =
                            new float[numBuckets]
                                    [numClasses];
                    float[] gradAccumB =
                            new float[numClasses];
                    int mbCount = 0;

                    for (int i = from; i < to; i++) {
                        extractInto(ext, texts[i],
                                featureBuf);
                        threadLoss += forwardGrad(
                                featureBuf,
                                batchLabels[i],
                                logitBuf, nzBuf);

                        int nnz = sparseIndex(
                                featureBuf, nzBuf);
                        for (int j = 0; j < nnz; j++) {
                            int b = nzBuf[j];
                            float fv = featureBuf[b];
                            float[] ab = gradAccumW[b];
                            for (int c = 0;
                                 c < numClasses; c++) {
                                ab[c] +=
                                        logitBuf[c] * fv;
                            }
                        }
                        for (int c = 0;
                             c < numClasses; c++) {
                            gradAccumB[c] +=
                                    logitBuf[c];
                        }
                        mbCount++;

                        if (mbCount == miniBatchSize) {
                            applyAdamMiniBatch(
                                    gradAccumW,
                                    gradAccumB,
                                    mbCount, tid);
                            mbCount = 0;
                        }
                    }
                    if (mbCount > 0) {
                        applyAdamMiniBatch(gradAccumW,
                                gradAccumB, mbCount, tid);
                    }
                } else {
                    // Online SGD (Hogwild)
                    for (int i = from; i < to; i++) {
                        extractInto(ext, texts[i],
                                featureBuf);
                        threadLoss += forwardGrad(
                                featureBuf,
                                batchLabels[i],
                                logitBuf, nzBuf);
                        int nnz = sparseIndex(
                                featureBuf, nzBuf);
                        sgdUpdate(featureBuf, logitBuf,
                                nnz, nzBuf, sgdLr);
                    }
                }
                totalLoss.add(threadLoss);
            }));
        }
        for (Future<?> f : futures) {
            try {
                f.get();
            } catch (Exception e) {
                throw new RuntimeException(
                        "Hogwild thread failed", e);
            }
        }
    }

    // ================================================================
    //  Forward pass + gradient (no weight update)
    // ================================================================

    /**
     * Forward pass and gradient computation for one sample.
     * After return, {@code logitBuf} contains the gradient:
     * {@code grad[c] = prob[c] - 1{c == trueClass}}.
     *
     * @return cross-entropy loss for this sample
     */
    private double forwardGrad(int[] features,
                               int trueClass,
                               float[] logitBuf,
                               int[] nzBuf) {
        int nnz = sparseIndex(features, nzBuf);

        for (int c = 0; c < numClasses; c++) {
            logitBuf[c] = getO(biases, c);
        }
        for (int i = 0; i < nnz; i++) {
            int b = nzBuf[i];
            float fv = features[b];
            float[] wb = weights[b];
            for (int c = 0; c < numClasses; c++) {
                logitBuf[c] += getO(wb, c) * fv;
            }
        }

        softmaxInPlace(logitBuf);

        double loss = -Math.log(
                Math.max(logitBuf[trueClass], 1e-10));

        logitBuf[trueClass] -= 1.0f;
        return loss;
    }

    /**
     * Build sparse index of non-zero features.
     *
     * @return count of non-zero entries
     */
    private int sparseIndex(int[] features, int[] nzBuf) {
        int nnz = 0;
        for (int b = 0; b < numBuckets; b++) {
            if (features[b] != 0) {
                nzBuf[nnz++] = b;
            }
        }
        return nnz;
    }

    // ================================================================
    //  Mini-batch Adam update
    // ================================================================

    /**
     * Apply one AdamW update from accumulated mini-batch
     * gradients. Averages the accumulated gradient by
     * {@code mbCount}, then runs a single Adam step.
     * Clears the accumulator buffers after the update.
     *
     * @param gradAccumW accumulated weight gradients
     *                   [bucket][class]
     * @param gradAccumB accumulated bias gradients [class]
     * @param mbCount    number of samples in this
     *                   mini-batch
     * @param threadId   thread ID for per-thread moments,
     *                   or -1 for shared moments
     */
    private void applyAdamMiniBatch(
            float[][] gradAccumW, float[] gradAccumB,
            int mbCount, int threadId) {
        float scale = 1.0f / mbCount;

        long t;
        float bc1, bc2;
        float[][] lMW, lVW;
        float[] lMB, lVB;

        if (threadId >= 0 && perThreadMW != null) {
            t = ++perThreadStep[threadId];
            lMW = perThreadMW[threadId];
            lVW = perThreadVW[threadId];
            lMB = perThreadMBias[threadId];
            lVB = perThreadVBias[threadId];
        } else {
            t = globalStep.incrementAndGet();
            lMW = mW;
            lVW = vW;
            lMB = mBias;
            lVB = vBias;
        }
        bc1 = 1f - (float) Math.pow(adamBeta1, t);
        bc2 = 1f - (float) Math.pow(adamBeta2, t);

        // Weight update — only touch buckets that have
        // accumulated gradient (sparse)
        for (int b = 0; b < numBuckets; b++) {
            float[] ab = gradAccumW[b];
            boolean touched = false;
            for (int c = 0; c < numClasses; c++) {
                if (ab[c] != 0f) {
                    touched = true;
                    break;
                }
            }
            if (!touched) {
                continue;
            }

            float[] wb = weights[b];
            float[] mb = lMW[b];
            float[] vb = lVW[b];
            for (int c = 0; c < numClasses; c++) {
                float g = ab[c] * scale;
                float m = adamBeta1 * mb[c]
                        + (1 - adamBeta1) * g;
                float v = adamBeta2 * vb[c]
                        + (1 - adamBeta2) * g * g;
                mb[c] = m;
                vb[c] = v;
                float mHat = m / bc1;
                float vHat = v / bc2;
                float w = getO(wb, c);
                w -= adamLr * mHat
                        / ((float) Math.sqrt(vHat)
                        + adamEpsilon);
                w -= adamLr * l2Lambda * w;
                setO(wb, c, w);
                ab[c] = 0f; // clear as we go
            }
        }

        // Bias update
        for (int c = 0; c < numClasses; c++) {
            float g = gradAccumB[c] * scale;
            float m = adamBeta1 * lMB[c]
                    + (1 - adamBeta1) * g;
            float v = adamBeta2 * lVB[c]
                    + (1 - adamBeta2) * g * g;
            lMB[c] = m;
            lVB[c] = v;
            float mHat = m / bc1;
            float vHat = v / bc2;
            float bi = getO(biases, c);
            bi -= adamLr * mHat
                    / ((float) Math.sqrt(vHat)
                    + adamEpsilon);
            setO(biases, c, bi);
            gradAccumB[c] = 0f; // clear
        }
    }

    /**
     * Online SGD update for one sample. Gradient is
     * {@code grad[c] = prob[c] - 1{c == trueClass}}.
     * Weight decay is coupled (standard L2).
     */
    private void sgdUpdate(int[] features, float[] grad,
                           int nnz, int[] nzIdx,
                           float lr) {
        for (int i = 0; i < nnz; i++) {
            int b = nzIdx[i];
            float fv = features[b];
            float[] wb = weights[b];
            for (int c = 0; c < numClasses; c++) {
                float w = getO(wb, c);
                setO(wb, c, w - lr * (grad[c] * fv
                        + l2Lambda * w));
            }
        }
        for (int c = 0; c < numClasses; c++) {
            float bi = getO(biases, c);
            setO(biases, c, bi - lr * grad[c]);
        }
    }

    // ================================================================
    //  Evaluation
    // ================================================================

    /**
     * Compute macro-averaged F1 on a dataset using current
     * float32 weights.
     */
    /** Result of a macro F1 evaluation. */
    public static class F1Result {
        public final double f1;
        public final int numLangs;

        F1Result(double f1, int numLangs) {
            this.f1 = f1;
            this.numLangs = numLangs;
        }
    }

    public F1Result evaluateMacroF1(
            List<LabeledSentence> data) {
        int[][] counts = new int[numClasses][3];
        FeatureExtractor ext = createExtractor();
        int[] featureBuf = new int[numBuckets];
        float[] logitBuf = new float[numClasses];

        for (LabeledSentence s : data) {
            Integer trueIdx =
                    labelIndex.get(s.getLanguage());
            if (trueIdx == null) {
                continue;
            }
            extractInto(ext, s.getText(), featureBuf);
            int predicted =
                    predictFromBuf(featureBuf, logitBuf);
            if (predicted == trueIdx) {
                counts[trueIdx][0]++;
            } else {
                counts[trueIdx][2]++;
                counts[predicted][1]++;
            }
        }

        double f1Sum = 0;
        int n = 0;
        for (int c = 0; c < numClasses; c++) {
            int tp = counts[c][0];
            int fp = counts[c][1];
            int fn = counts[c][2];
            if (tp + fn == 0) {
                continue;
            }
            double p = tp + fp > 0
                    ? (double) tp / (tp + fp) : 0;
            double r = (double) tp / (tp + fn);
            double f1 = p + r > 0
                    ? 2 * p * r / (p + r) : 0;
            f1Sum += f1;
            n++;
        }
        return new F1Result(
                n > 0 ? f1Sum / n : 0, n);
    }

    private int predictFromBuf(int[] features,
                               float[] logitBuf) {
        for (int c = 0; c < numClasses; c++) {
            logitBuf[c] = getO(biases, c);
        }
        for (int b = 0; b < numBuckets; b++) {
            if (features[b] != 0) {
                float fv = features[b];
                float[] wb = weights[b];
                for (int c = 0; c < numClasses; c++) {
                    logitBuf[c] += getO(wb, c) * fv;
                }
            }
        }
        int best = 0;
        for (int c = 1; c < numClasses; c++) {
            if (logitBuf[c] > logitBuf[best]) {
                best = c;
            }
        }
        return best;
    }

    /**
     * Predict the language label for a text string.
     */
    public String predict(String text) {
        FeatureExtractor ext = createExtractor();
        int[] features = new int[numBuckets];
        extractInto(ext, text, features);
        float[] logits = new float[numClasses];
        return labels[predictFromBuf(features, logits)];
    }

    // ================================================================
    //  Accessors
    // ================================================================

    /**
     * Return weights transposed to class-major
     * {@code [class][bucket]} for {@link ModelQuantizer}.
     */
    public float[][] getWeightsClassMajor() {
        float[][] cm = new float[numClasses][numBuckets];
        for (int b = 0; b < numBuckets; b++) {
            float[] wb = weights[b];
            for (int c = 0; c < numClasses; c++) {
                cm[c][b] = getO(wb, c);
            }
        }
        return cm;
    }

    public float[] getBiases() {
        return biases;
    }

    public String[] getLabels() {
        return labels;
    }

    public int getNumBuckets() {
        return numBuckets;
    }


    public FeatureExtractor getExtractor() {
        return createExtractor();
    }

    public Map<String, Integer> getLabelIndex() {
        return labelIndex;
    }

    // ================================================================
    //  Helpers
    // ================================================================

    private FeatureExtractor createExtractor() {
        return new ScriptAwareFeatureExtractor(numBuckets);
    }

    private void extractInto(FeatureExtractor ext,
                             String text, int[] buf) {
        if (preprocessed) {
            ext.extractFromPreprocessed(text, buf, true);
        } else {
            ext.extract(text, buf);
        }
    }

    private List<LabeledSentence> sampleDevSubset(
            List<LabeledSentence> devData, int maxSize) {
        if (devData == null || devData.size() <= maxSize) {
            return devData != null
                    ? devData : Collections.emptyList();
        }
        Map<String, List<LabeledSentence>> byLang =
                new HashMap<>();
        for (LabeledSentence s : devData) {
            byLang.computeIfAbsent(s.getLanguage(),
                    k -> new ArrayList<>()).add(s);
        }
        Random rng = new Random(seed + 7);
        List<LabeledSentence> sample = new ArrayList<>();
        double ratio = (double) maxSize / devData.size();
        for (List<LabeledSentence> langSents :
                byLang.values()) {
            int take = Math.max(1,
                    (int) (langSents.size() * ratio));
            Collections.shuffle(langSents, rng);
            sample.addAll(langSents.subList(
                    0, Math.min(take, langSents.size())));
        }
        return sample;
    }

    private static void softmaxInPlace(float[] logits) {
        float max = Float.NEGATIVE_INFINITY;
        for (float v : logits) {
            if (v > max) {
                max = v;
            }
        }
        float sum = 0f;
        for (int i = 0; i < logits.length; i++) {
            logits[i] = (float) Math.exp(logits[i] - max);
            sum += logits[i];
        }
        if (sum > 0f) {
            for (int i = 0; i < logits.length; i++) {
                logits[i] /= sum;
            }
        }
    }

    /**
     * Fisher–Yates shuffle of parallel arrays (texts and
     * labels) in-place, up to {@code len} elements.
     */
    private static void shuffleParallel(
            String[] texts, int[] labels, int len,
            Random rng) {
        for (int i = len - 1; i > 0; i--) {
            int j = rng.nextInt(i + 1);
            String tmpT = texts[i];
            texts[i] = texts[j];
            texts[j] = tmpT;
            int tmpL = labels[i];
            labels[i] = labels[j];
            labels[j] = tmpL;
        }
    }

    private static double elapsed(long startNanos) {
        return (System.nanoTime() - startNanos)
                / 1_000_000_000.0;
    }
}
