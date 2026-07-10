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
package org.apache.tika.parser.ogg;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.XMPDM;
import org.apache.tika.sax.BodyContentHandler;
import org.apache.tika.sax.XHTMLContentHandler;

/**
 * Regression test for the thread safety of duration formatting in
 * {@link OggAudioParser}. The ogg audio parsers are singletons shared across
 * threads, so the {@link DecimalFormat} used to emit {@link XMPDM#DURATION}
 * must not be shared static state. This drives {@code extractDuration}
 * concurrently with a range of duration shapes and asserts the emitted
 * metadata always matches the single-threaded value.
 */
public class OggAudioParserDurationTest {

    // Durations chosen to exercise rounding / off-fast-path behaviour of the
    // "0.0#" pattern, where a shared formatter's mutable state would show up.
    private static final double[] DURATIONS = {
            0.1, 1.005, 12.34, 59.995, 123.456, 3599.999, 7200.05, 86399.9
    };

    @Test
    @Timeout(60)
    public void durationFormattingIsThreadSafe() throws Exception {
        // Expected values computed single-threaded with the same pattern.
        String[] expected = new String[DURATIONS.length];
        for (int i = 0; i < DURATIONS.length; i++) {
            expected[i] = format(DURATIONS[i]);
        }

        int threads = 32;
        int iterationsPerThread = 5000;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        Map<String, String> mismatches = new ConcurrentHashMap<>();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        List<java.util.concurrent.Future<?>> futures = new ArrayList<>();

        try {
            for (int t = 0; t < threads; t++) {
                final int offset = t;
                futures.add(executor.submit(() -> {
                    try {
                        start.await();
                        for (int i = 0; i < iterationsPerThread; i++) {
                            int idx = (offset + i) % DURATIONS.length;
                            Metadata metadata = new Metadata();
                            XHTMLContentHandler xhtml =
                                    new XHTMLContentHandler(new BodyContentHandler(), metadata);
                            xhtml.startDocument();
                            OggAudioParser.extractDuration(metadata, xhtml, DURATIONS[idx]);
                            String actual = metadata.get(XMPDM.DURATION);
                            if (!expected[idx].equals(actual)) {
                                mismatches.putIfAbsent(expected[idx], String.valueOf(actual));
                            }
                        }
                    } catch (Throwable ex) {
                        failure.compareAndSet(null, ex);
                    }
                }));
            }
            start.countDown();
            executor.shutdown();
            assertTrue(executor.awaitTermination(50, TimeUnit.SECONDS),
                    "duration formatting did not finish in time");
        } finally {
            executor.shutdownNow();
        }

        if (failure.get() != null) {
            throw new AssertionError("concurrent duration formatting threw", failure.get());
        }
        assertTrue(mismatches.isEmpty(),
                "concurrent duration formatting produced corrupted output: " + mismatches);
    }

    @Test
    public void durationOutputMatchesPattern() throws Exception {
        Metadata metadata = new Metadata();
        XHTMLContentHandler xhtml = new XHTMLContentHandler(new BodyContentHandler(), metadata);
        xhtml.startDocument();
        OggAudioParser.extractDuration(metadata, xhtml, 123.456);
        assertEquals(format(123.456), metadata.get(XMPDM.DURATION));
    }

    private static String format(double duration) {
        DecimalFormat df = (DecimalFormat) NumberFormat.getNumberInstance(Locale.ROOT);
        df.applyPattern("0.0#");
        return df.format(duration);
    }
}
