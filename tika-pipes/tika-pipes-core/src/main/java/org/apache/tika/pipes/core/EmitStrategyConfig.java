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

import org.apache.tika.exception.TikaConfigException;
import org.apache.tika.exception.TikaException;

/**
 * Configuration for emit strategy in PipesConfig.
 * <p>
 * Example JSON configuration:
 * <pre>
 * {
 *   "pipes": {
 *     "emitStrategy": {
 *       "type": "DYNAMIC",
 *       "thresholdBytes": 100000
 *     }
 *   }
 * }
 * </pre>
 * Or for simpler strategies:
 * <pre>
 * {
 *   "pipes": {
 *     "emitStrategy": {
 *       "type": "EMIT_ALL"
 *     }
 *   }
 * }
 * </pre>
 */
public class EmitStrategyConfig {

    /**
     * Default emit strategy for PipesServer.
     * DYNAMIC means the strategy is determined by directEmitThresholdBytes.
     */
    public static final EmitStrategy DEFAULT_EMIT_STRATEGY = EmitStrategy.DYNAMIC;

    /**
     * Default threshold in bytes for direct emission from PipesServer.
     * If an extract is larger than this, it will be emitted
     * directly from the forked PipesServer rather than passed back to PipesClient.
     * Only used when emitStrategy is DYNAMIC.
     */
    public static final long DEFAULT_DIRECT_EMIT_THRESHOLD_BYTES = 100000;

    private EmitStrategy type = DEFAULT_EMIT_STRATEGY;
    private Long thresholdBytes = null;

    public EmitStrategyConfig() {
    }

    public EmitStrategyConfig(EmitStrategy type) {
        this.type = type;
        if (type == EmitStrategy.DYNAMIC) {
            thresholdBytes = DEFAULT_DIRECT_EMIT_THRESHOLD_BYTES;
        }
    }

    public EmitStrategyConfig(EmitStrategy type, Long thresholdBytes)  throws TikaException {
        this.type = type;
        this.thresholdBytes = thresholdBytes;
        validate();
    }

    /**
     * Get the emit strategy type.
     *
     * @return the emit strategy
     */
    public EmitStrategy getType() {
        return type;
    }

    /**
     * Set the emit strategy type.
     *
     * @param type the emit strategy
     */
    public void setType(EmitStrategy type) throws TikaConfigException {
        this.type = type;
        validate();
    }

    /**
     * Get the threshold in bytes for DYNAMIC strategy.
     * Only applicable when type is DYNAMIC.
     *
     * @return the threshold in bytes, or null to use default
     */
    public Long getThresholdBytes() {
        return thresholdBytes;
    }

    /**
     * Set the threshold in bytes for DYNAMIC strategy.
     * Only applicable when type is DYNAMIC.
     *
     * @param thresholdBytes the threshold in bytes
     */
    public void setThresholdBytes(Long thresholdBytes) throws TikaConfigException {
        this.thresholdBytes = thresholdBytes;
        validate();
    }

    private void validate() throws TikaConfigException {
        if (thresholdBytes != null &&
                (type == EmitStrategy.EMIT_ALL || type == EmitStrategy.PASSBACK_ALL)) {
            throw new TikaConfigException(
                "thresholdBytes cannot be set for emit strategy type " + type +
                ". thresholdBytes is only applicable for DYNAMIC strategy.");
        }
    }
}
