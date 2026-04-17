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

/**
 * SPI contract for an MoE charset-detection specialist.  Discovered via
 * {@link java.util.ServiceLoader} at
 * {@code META-INF/services/org.apache.tika.ml.chardetect.StatisticalSpecialist}.
 * Implementations must be thread-safe.
 */
public interface StatisticalSpecialist {

    /**
     * Short name: {@code "utf16"}, {@code "sbcs"}, etc.
     */
    String getName();

    /** Per-class logits for the probe, or {@code null} to decline
     *  (probe too short, hard-gated, etc.).  Declining contributes nothing;
     *  a low-scoring result contributes weak signal. */
    SpecialistOutput score(byte[] probe);
}
