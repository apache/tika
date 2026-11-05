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
package org.apache.tika.ml.chardetect;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * The bundled model's logits match those of the reference scorer used in training
 * ({@code hashed-ngram-parity.tsv}, see tika-ml-chardetect's README).
 */
public class HashedNgramModelParityTest {

    private static final String FIXTURE = "/org/apache/tika/ml/chardetect/hashed-ngram-parity.tsv";
    private static final float TOLERANCE = 1e-4f;

    private static byte[] modelBytes;
    private static HashedNgramModel model;
    private static String fixtureMd5;
    private static final List<String> NAMES = new ArrayList<>();
    private static final List<byte[]> PROBES = new ArrayList<>();
    private static final List<float[]> EXPECTED = new ArrayList<>();

    @BeforeAll
    static void load() throws Exception {
        try (InputStream in = HashedNgramModelParityTest.class.getResourceAsStream(
                HashedNgramEncodingDetector.DEFAULT_MODEL_RESOURCE)) {
            assertNotNull(in);
            modelBytes = IOUtils.toByteArray(in);
        }
        model = new HashedNgramModel(new ByteArrayInputStream(modelBytes));
        try (BufferedReader r = new BufferedReader(new InputStreamReader(
                HashedNgramModelParityTest.class.getResourceAsStream(FIXTURE), StandardCharsets.UTF_8))) {
            for (String line = r.readLine(); line != null; line = r.readLine()) {
                if (line.startsWith("#") || line.isBlank()) {
                    continue;
                }
                String[] t = line.split("\t");
                if (t[0].equals("model-md5")) {
                    fixtureMd5 = t[1];
                    continue;
                }
                NAMES.add(t[0]);
                PROBES.add(HexFormat.of().parseHex(t[1]));
                if (t[2].equals("abstain")) {
                    EXPECTED.add(null);
                } else {
                    String[] v = t[2].split(",");
                    float[] f = new float[v.length];
                    for (int i = 0; i < v.length; i++) {
                        f[i] = Float.parseFloat(v[i]);
                    }
                    EXPECTED.add(f);
                }
            }
        }
    }

    @Test
    public void fixtureMatchesBundledModel() throws Exception {
        String md5 = HexFormat.of().formatHex(MessageDigest.getInstance("MD5").digest(modelBytes));
        assertEquals(fixtureMd5, md5, "model changed: regenerate " + FIXTURE);
    }

    @Test
    public void provenanceMatchesBundledModel() throws Exception {
        String provenance;
        try (InputStream in = HashedNgramModelParityTest.class.getResourceAsStream(
                HashedNgramEncodingDetector.DEFAULT_MODEL_RESOURCE + ".provenance")) {
            assertNotNull(in, "hashed-ngram.hng.provenance missing");
            provenance = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        String recorded = null;
        for (String line : provenance.split("\n")) {
            if (line.startsWith("model_sha256\t")) {
                recorded = line.substring("model_sha256\t".length()).trim();
            }
        }
        String sha256 = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(modelBytes));
        assertEquals(sha256, recorded, "hashed-ngram.hng changed without its provenance");
    }

    @Test
    public void logitsMatchReference() {
        assertTrue(PROBES.size() >= 10);
        float[] out = new float[model.getNumClasses()];
        for (int p = 0; p < PROBES.size(); p++) {
            byte[] probe = PROBES.get(p);
            float[] exp = EXPECTED.get(p);
            boolean scored = model.logits(probe, 0, probe.length, out);
            if (exp == null) {
                assertFalse(scored, NAMES.get(p));
                continue;
            }
            assertTrue(scored, NAMES.get(p));
            assertEquals(exp.length, out.length);
            for (int c = 0; c < out.length; c++) {
                assertEquals(exp[c], out[c], TOLERANCE, NAMES.get(p) + " class " + c);
            }
        }
    }

    @Test
    public void concurrentScoringIsIsolated() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(4);
        try {
            List<Future<Float>> futures = new ArrayList<>();
            for (int k = 0; k < 400; k++) {
                int p = 1 + k % (PROBES.size() - 1);
                futures.add(pool.submit(() -> {
                    float[] out = new float[model.getNumClasses()];
                    byte[] probe = PROBES.get(p);
                    model.logits(probe, 0, probe.length, out);
                    float maxDiff = 0;
                    for (int c = 0; c < out.length; c++) {
                        maxDiff = Math.max(maxDiff, Math.abs(out[c] - EXPECTED.get(p)[c]));
                    }
                    return maxDiff;
                }));
            }
            for (Future<Float> f : futures) {
                assertTrue(f.get() <= TOLERANCE);
            }
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    public void truncatedModelRejected() {
        byte[] cut = java.util.Arrays.copyOf(modelBytes, modelBytes.length - 1);
        assertThrows(IOException.class, () -> new HashedNgramModel(new ByteArrayInputStream(cut)));
        byte[] badMagic = modelBytes.clone();
        badMagic[3] = '1';
        assertThrows(IOException.class, () -> new HashedNgramModel(new ByteArrayInputStream(badMagic)));
    }
}
