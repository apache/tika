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

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Raw per-class logits from a single MoE specialist.  Labels the specialist
 * doesn't cover are absent from the map (no OTHER class).  Logits are raw
 * (pre-softmax); pooling happens in the combiner.
 */
public final class SpecialistOutput {

    private final String specialistName;
    private final Map<String, Float> classLogits;

    public SpecialistOutput(String specialistName, Map<String, Float> classLogits) {
        if (specialistName == null) {
            throw new IllegalArgumentException("specialistName is required");
        }
        if (classLogits == null) {
            throw new IllegalArgumentException("classLogits is required");
        }
        this.specialistName = specialistName;
        this.classLogits = Collections.unmodifiableMap(new LinkedHashMap<>(classLogits));
    }

    public String getSpecialistName() {
        return specialistName;
    }

    public Map<String, Float> getClassLogits() {
        return classLogits;
    }

    public Iterable<String> getCoveredLabels() {
        return classLogits.keySet();
    }

    /**
     * Raw logit for {@code label}, or {@code null} if not covered.
     */
    public Float getLogit(String label) {
        return classLogits.get(label);
    }

    @Override
    public String toString() {
        return "SpecialistOutput{" + specialistName + "=" + classLogits + "}";
    }
}
