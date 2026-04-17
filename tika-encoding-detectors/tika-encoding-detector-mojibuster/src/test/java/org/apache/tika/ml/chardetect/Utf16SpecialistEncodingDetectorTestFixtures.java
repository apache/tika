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

import org.apache.tika.ml.LinearModel;

/**
 * Shared synthetic UTF-16 specialist model for tests — same weights as in
 * {@link Utf16SpecialistEncodingDetectorTest}.  Factored out so combiner
 * integration tests can reuse it without duplicating weight tuning.
 */
final class Utf16SpecialistEncodingDetectorTestFixtures {

    private static final int NUL_EVEN = 0, NUL_ODD = 1;
    private static final int CTRL_EVEN = 2, CTRL_ODD = 3;
    private static final int ASCII_EVEN = 4, ASCII_ODD = 5;
    private static final int C1_EVEN = 8, C1_ODD = 9;
    private static final int HI_EVEN = 10, HI_ODD = 11;

    private Utf16SpecialistEncodingDetectorTestFixtures() {
    }

    static LinearModel syntheticModel() {
        int numBuckets = Utf16ColumnFeatureExtractor.NUM_FEATURES;
        int numClasses = 2;
        String[] labels = {"UTF-16-LE", "UTF-16-BE"};
        byte[][] weights = new byte[numClasses][numBuckets];

        weights[0][NUL_ODD]    = +10;
        weights[0][NUL_EVEN]   = -10;
        weights[0][CTRL_ODD]   = +10;
        weights[0][CTRL_EVEN]  = -10;
        weights[0][ASCII_ODD]  = +3;
        weights[0][ASCII_EVEN] = -3;
        weights[0][C1_ODD]     = +100;
        weights[0][C1_EVEN]    = -100;
        weights[0][HI_EVEN]    = +3;
        weights[0][HI_ODD]     = -3;

        weights[1][NUL_EVEN]   = +10;
        weights[1][NUL_ODD]    = -10;
        weights[1][CTRL_EVEN]  = +10;
        weights[1][CTRL_ODD]   = -10;
        weights[1][ASCII_EVEN] = +3;
        weights[1][ASCII_ODD]  = -3;
        weights[1][C1_EVEN]    = +100;
        weights[1][C1_ODD]     = -100;
        weights[1][HI_ODD]     = +3;
        weights[1][HI_EVEN]    = -3;

        float[] scales = {0.002f, 0.002f};
        float[] biases = {0.0f, 0.0f};
        return new LinearModel(numBuckets, numClasses, labels, scales, biases, weights);
    }
}
