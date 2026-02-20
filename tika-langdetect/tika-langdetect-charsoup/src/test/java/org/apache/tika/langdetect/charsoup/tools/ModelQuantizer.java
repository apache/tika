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

import org.apache.tika.ml.LinearModel;

/**
 * Quantizes float32 model weights to INT8 for compact storage.
 * <p>
 * Per-class quantization: each class row is independently scaled
 * to fit into [-127, 127]. The scale factor is stored alongside
 * the quantized weights to allow dequantization at inference time.
 * </p>
 */
public class ModelQuantizer {

    private ModelQuantizer() {
    }

    /**
     * Quantize a Phase2Trainer's float32 weights to INT8.
     * Phase2Trainer uses bucket-major layout internally;
     * {@link Phase2Trainer#getWeightsClassMajor()} transposes
     * to the class-major layout expected here.
     *
     * @param trainer the trained Phase2Trainer
     * @return a LinearModel with INT8 quantized weights
     */
    public static LinearModel quantize(Phase2Trainer trainer) {
        return quantize(trainer.getLabels(),
                trainer.getWeightsClassMajor(),
                trainer.getBiases(),
                trainer.getNumBuckets());
    }

    /**
     * Quantize float32 weights to INT8.
     *
     * @param labels     class labels
     * @param weights    float32 weights [numClasses][numBuckets]
     * @param biases     float32 biases [numClasses]
     * @param numBuckets number of feature buckets
     * @return a LinearModel with INT8 quantized weights
     */
    public static LinearModel quantize(String[] labels,
                                       float[][] weights,
                                       float[] biases,
                                       int numBuckets) {
        int numClasses = labels.length;
        float[] scales = new float[numClasses];
        byte[][] quantizedWeights =
                new byte[numClasses][numBuckets];

        for (int c = 0; c < numClasses; c++) {
            float maxAbs = 0f;
            for (int b = 0; b < numBuckets; b++) {
                float abs = Math.abs(weights[c][b]);
                if (abs > maxAbs) {
                    maxAbs = abs;
                }
            }

            if (maxAbs == 0f) {
                scales[c] = 1f;
            } else {
                scales[c] = maxAbs / 127f;
            }

            for (int b = 0; b < numBuckets; b++) {
                int q = Math.round(weights[c][b] / scales[c]);
                quantizedWeights[c][b] =
                        (byte) Math.max(-127, Math.min(127, q));
            }
        }

        return new LinearModel(numBuckets, numClasses,
                labels.clone(), scales, biases.clone(),
                quantizedWeights);
    }
}
