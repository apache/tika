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
package org.apache.tika.ml;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import org.junit.jupiter.api.Test;

public class LinearModelCalibrationTest {

    private static LinearModel modelWithCalibration(float[] mean, float[] std) {
        byte[][] weights = new byte[2][4];
        weights[0][0] = 10;
        weights[1][1] = 10;
        return new LinearModel(4, 2,
                new String[]{"A", "B"},
                new float[]{1.0f, 1.0f}, new float[]{0.0f, 0.0f},
                weights, mean, std);
    }

    @Test
    public void hasCalibrationReflectsConstructor() {
        LinearModel cal = modelWithCalibration(
                new float[]{0.5f, -0.5f}, new float[]{1.0f, 1.0f});
        assertTrue(cal.hasCalibration());

        LinearModel raw = new LinearModel(4, 2,
                new String[]{"A", "B"},
                new float[]{1.0f, 1.0f}, new float[]{0.0f, 0.0f},
                new byte[2][4]);
        assertFalse(raw.hasCalibration());
    }

    @Test
    public void predictCalibratedLogitsFallsBackToRawWithoutCalibration() {
        LinearModel raw = new LinearModel(4, 2,
                new String[]{"A", "B"},
                new float[]{1.0f, 1.0f}, new float[]{0.0f, 0.0f},
                new byte[2][4]);
        int[] features = {1, 0, 0, 0};
        float[] rawLogits = raw.predictLogits(features);
        float[] calibrated = raw.predictCalibratedLogits(features);
        assertArrayEquals(rawLogits, calibrated, 1e-6f);
    }

    @Test
    public void predictCalibratedLogitsStandardizes() {
        // mean=2, std=0.5 for class A → calibrated = (raw - 2) / 0.5
        LinearModel cal = modelWithCalibration(
                new float[]{2.0f, 0.0f}, new float[]{0.5f, 2.0f});
        int[] features = {5, 0, 0, 0};  // class 0 weight=10, scale=1 → logit=10*5/... clipped
        float[] raw = cal.predictLogits(features);
        float[] calibrated = cal.predictCalibratedLogits(features);
        assertEquals((raw[0] - 2.0f) / 0.5f, calibrated[0], 1e-5f);
        assertEquals((raw[1] - 0.0f) / 2.0f, calibrated[1], 1e-5f);
    }

    @Test
    public void zeroStdIsSanitizedToOne() {
        // std=0 would divide-by-zero; constructor must rewrite to 1.0.
        LinearModel cal = modelWithCalibration(
                new float[]{1.0f, 1.0f}, new float[]{0.0f, 0.0f});
        assertEquals(1.0f, cal.getClassStd()[0], 0.0f);
        assertEquals(1.0f, cal.getClassStd()[1], 0.0f);
    }

    @Test
    public void saveLoadRoundTripPreservesCalibration() throws IOException {
        LinearModel src = modelWithCalibration(
                new float[]{1.5f, -0.25f}, new float[]{0.7f, 2.3f});
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        src.save(bos);
        LinearModel loaded = LinearModel.load(new ByteArrayInputStream(bos.toByteArray()));

        assertTrue(loaded.hasCalibration());
        assertArrayEquals(src.getClassMean(), loaded.getClassMean(), 1e-6f);
        assertArrayEquals(src.getClassStd(), loaded.getClassStd(), 1e-6f);
    }

    @Test
    public void saveLoadRoundTripWithoutCalibration() throws IOException {
        LinearModel src = new LinearModel(4, 2,
                new String[]{"A", "B"},
                new float[]{1.0f, 1.0f}, new float[]{0.0f, 0.0f},
                new byte[2][4]);
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        src.save(bos);
        LinearModel loaded = LinearModel.load(new ByteArrayInputStream(bos.toByteArray()));

        assertFalse(loaded.hasCalibration());
    }

    @Test
    public void v1FormatStillLoadable() throws IOException {
        // Hand-build a V1 file (no calibration bytes) and verify it loads.
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        java.io.DataOutputStream dos = new java.io.DataOutputStream(bos);
        dos.writeInt(LinearModel.MAGIC);
        dos.writeInt(LinearModel.VERSION_V1);  // version 1, no calibration
        dos.writeInt(4);                       // numBuckets
        dos.writeInt(2);                       // numClasses
        for (String lbl : new String[]{"A", "B"}) {
            byte[] utf8 = lbl.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            dos.writeShort(utf8.length);
            dos.write(utf8);
        }
        for (int c = 0; c < 2; c++) {
            dos.writeFloat(1.0f);  // scales
        }
        for (int c = 0; c < 2; c++) {
            dos.writeFloat(0.0f);  // biases
        }
        // No hasCalibration byte in V1.  Weights follow directly.
        for (int b = 0; b < 4 * 2; b++) {
            dos.write(0);
        }
        dos.flush();

        LinearModel loaded = LinearModel.load(new ByteArrayInputStream(bos.toByteArray()));
        assertFalse(loaded.hasCalibration());
        assertEquals(4, loaded.getNumBuckets());
        assertEquals(2, loaded.getNumClasses());
    }
}
