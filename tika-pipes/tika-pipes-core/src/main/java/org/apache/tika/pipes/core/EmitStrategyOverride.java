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
package org.apache.tika.pipes.core;

/**
 * Runtime override for emit strategy that can be set in ParseContext to override
 * the default strategy from PipesConfig on a per-request basis.
 * <p>
 * Example usage:
 * <pre>
 * ParseContext context = new ParseContext();
 * context.set(EmitStrategyOverride.class,
 *     new EmitStrategyOverride(EmitStrategy.EMIT_ALL));
 * </pre>
 */
public class EmitStrategyOverride {

    private final EmitStrategy emitStrategy;
    private final Long directEmitThresholdBytes;

    /**
     * Create an emit strategy override with just the strategy.
     * If the strategy is DYNAMIC, the threshold from PipesConfig will be used.
     *
     * @param emitStrategy the emit strategy to use
     */
    public EmitStrategyOverride(EmitStrategy emitStrategy) {
        this(emitStrategy, null);
    }

    /**
     * Create an emit strategy override with both strategy and threshold.
     * The threshold is only used when emitStrategy is DYNAMIC.
     *
     * @param emitStrategy the emit strategy to use
     * @param directEmitThresholdBytes the threshold in bytes for DYNAMIC strategy (can be null to use default)
     * @throws IllegalArgumentException if thresholdBytes is set for EMIT_ALL or PASSBACK_ALL strategies
     */
    public EmitStrategyOverride(EmitStrategy emitStrategy, Long directEmitThresholdBytes) {
        if (directEmitThresholdBytes != null &&
                (emitStrategy == EmitStrategy.EMIT_ALL || emitStrategy == EmitStrategy.PASSBACK_ALL)) {
            throw new IllegalArgumentException(
                "directEmitThresholdBytes cannot be set for emit strategy " + emitStrategy +
                ". Threshold is only applicable for DYNAMIC strategy.");
        }
        this.emitStrategy = emitStrategy;
        this.directEmitThresholdBytes = directEmitThresholdBytes;
    }

    /**
     * Get the emit strategy.
     *
     * @return the emit strategy
     */
    public EmitStrategy getEmitStrategy() {
        return emitStrategy;
    }

    /**
     * Get the direct emit threshold in bytes for DYNAMIC strategy.
     * Returns null if not set, indicating the default from PipesConfig should be used.
     *
     * @return the threshold in bytes, or null to use default
     */
    public Long getDirectEmitThresholdBytes() {
        return directEmitThresholdBytes;
    }
}
