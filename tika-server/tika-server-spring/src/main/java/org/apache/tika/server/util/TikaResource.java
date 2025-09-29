/*
 *
 *  * Licensed to the Apache Software Foundation (ASF) under one or more
 *  * contributor license agreements.  See the NOTICE file distributed with
 *  * this work for additional information regarding copyright ownership.
 *  * The ASF licenses this file to You under the Apache License, Version 2.0
 *  * (the "License"); you may not use this file except in compliance with
 *  * the License.  You may obtain a copy of the License at
 *  *
 *  *     http://www.apache.org/licenses/LICENSE-2.0
 *  *
 *  * Unless required by applicable law or agreed to in writing, software
 *  * distributed under the License is distributed on an "AS IS" BASIS,
 *  * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  * See the License for the specific language governing permissions and
 *  * limitations under the License.
 *
 *
 */

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
package org.apache.tika.server.util;

import org.springframework.stereotype.Component;

import org.apache.tika.config.TikaConfig;
import org.apache.tika.detect.Detector;
import org.apache.tika.parser.ParseContext;

/**
 * Utility class providing access to Tika configuration and common operations.
 * This class serves as a bridge between the Spring controllers and Tika core functionality.
 */
@Component
public class TikaResource {
    
    private static final long DEFAULT_TASK_TIMEOUT_MILLIS = 300000; // 5 minutes
    private static TikaConfig tikaConfig;
    
    static {
        try {
            // Initialize with default Tika configuration
            tikaConfig = TikaConfig.getDefaultConfig();
        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize TikaConfig", e);
        }
    }
    
    /**
     * Get the task timeout from ParseContext, or return default if not configured.
     * 
     * @param parseContext The parse context which may contain timeout configuration
     * @return Timeout in milliseconds
     */
    public static long getTaskTimeout(ParseContext parseContext) {
        // Check if timeout is configured in parse context
        if (parseContext != null) {
            // Look for timeout configuration - this could be expanded based on actual Tika implementation
            Object timeout = parseContext.get(Object.class); // Placeholder - actual implementation would vary
            if (timeout instanceof Long) {
                return (Long) timeout;
            }
        }
        return DEFAULT_TASK_TIMEOUT_MILLIS;
    }
    
    /**
     * Get the Tika configuration instance.
     * 
     * @return TikaConfig instance
     */
    public static TikaConfig getConfig() {
        return tikaConfig;
    }
    
    /**
     * Get the detector from the Tika configuration.
     * 
     * @return Detector instance
     */
    public static Detector getDetector() {
        return tikaConfig.getDetector();
    }
    
    /**
     * Set a custom TikaConfig (useful for testing or custom configurations).
     * 
     * @param config The TikaConfig to use
     */
    public static void setConfig(TikaConfig config) {
        tikaConfig = config;
    }
    
    /**
     * Reset to default configuration.
     */
    public static void resetToDefault() {
        try {
            tikaConfig = TikaConfig.getDefaultConfig();
        } catch (Exception e) {
            throw new RuntimeException("Failed to reset to default TikaConfig", e);
        }
    }
}
