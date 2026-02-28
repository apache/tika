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
package org.apache.tika.langdetect.charsoup;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;

import org.junit.jupiter.api.Test;

import org.apache.tika.detect.encoding.LinearModel;

public class LinearModelTest {

    @Test
    public void testRoundTrip() throws IOException {
        // Build a tiny 3-class, 256-bucket model
        int numBuckets = 256;
        int numClasses = 3;
        String[] labels = {"eng", "deu", "fra"};
        float[] scales = {0.01f, 0.02f, 0.015f};
        float[] biases = {0.1f, -0.05f, 0.0f};
        byte[][] weights = new byte[numClasses][numBuckets];
        // Set some non-zero weights
        weights[0][0] = 127;
        weights[0][1] = -127;
        weights[1][10] = 50;
        weights[2][100] = -100;

        LinearModel original = new LinearModel(numBuckets, numClasses, labels, scales, biases,
                weights);

        // Save and reload
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        original.save(baos);
        byte[] bytes = baos.toByteArray();

        LinearModel loaded = LinearModel.load(new ByteArrayInputStream(bytes));

        assertEquals(numBuckets, loaded.getNumBuckets());
        assertEquals(numClasses, loaded.getNumClasses());
        assertArrayEquals(labels, loaded.getLabels());

        for (int c = 0; c < numClasses; c++) {
            assertEquals(scales[c], loaded.getScales()[c], 1e-6);
            assertEquals(biases[c], loaded.getBiases()[c], 1e-6);
            assertArrayEquals(weights[c], loaded.getWeights()[c]);
        }
    }

    @Test
    public void testSoftmax() {
        float[] logits = {1.0f, 2.0f, 3.0f};
        float[] probs = LinearModel.softmax(logits);

        // Should sum to ~1.0
        float sum = 0f;
        for (float p : probs) sum += p;
        assertEquals(1.0f, sum, 1e-5);

        // Highest logit should have highest probability
        assertTrue(probs[2] > probs[1]);
        assertTrue(probs[1] > probs[0]);
    }

    @Test
    public void testSoftmaxNumericalStability() {
        // Very large logits should not overflow
        float[] logits = {1000.0f, 1001.0f, 999.0f};
        float[] probs = LinearModel.softmax(logits);
        float sum = 0f;
        for (float p : probs) {
            assertTrue(Float.isFinite(p));
            sum += p;
        }
        assertEquals(1.0f, sum, 1e-5);
    }

    @Test
    public void testPredict() {
        // Simple model where class 0 has high weight on bucket 0
        int numBuckets = 4;
        int numClasses = 2;
        String[] labels = {"eng", "deu"};
        float[] scales = {1.0f, 1.0f};
        float[] biases = {0.0f, 0.0f};
        byte[][] weights = new byte[numClasses][numBuckets];
        weights[0][0] = 127;  // eng strongly triggered by bucket 0
        weights[1][1] = 127;  // deu strongly triggered by bucket 1

        LinearModel model = new LinearModel(numBuckets, numClasses, labels, scales, biases,
                weights);

        // Feature vector activating bucket 0
        int[] features0 = {10, 0, 0, 0};
        float[] probs0 = model.predict(features0);
        assertTrue(probs0[0] > probs0[1], "eng should win when bucket 0 is active");

        // Feature vector activating bucket 1
        int[] features1 = {0, 10, 0, 0};
        float[] probs1 = model.predict(features1);
        assertTrue(probs1[1] > probs1[0], "deu should win when bucket 1 is active");
    }

    @Test
    public void testCorruptMagicThrows() {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(baos);
        try {
            dos.writeInt(0xDEADBEEF); // wrong magic
            dos.flush();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        assertThrows(IOException.class,
                () -> LinearModel.load(new ByteArrayInputStream(baos.toByteArray())));
    }

    @Test
    public void testSaveHeaderFormat() throws IOException {
        LinearModel model = new LinearModel(128, 2, new String[]{"a", "b"},
                new float[]{1f, 2f}, new float[]{0f, 0f}, new byte[2][128]);

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        model.save(baos);
        byte[] data = baos.toByteArray();

        // Check magic bytes (big-endian "LDM1" = 0x4C444D31)
        assertEquals(0x4C, data[0] & 0xFF);
        assertEquals(0x44, data[1] & 0xFF);
        assertEquals(0x4D, data[2] & 0xFF);
        assertEquals(0x31, data[3] & 0xFF);
    }
}
