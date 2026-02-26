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
package org.apache.tika.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;

import org.apache.tika.TikaTest;
import org.apache.tika.config.loader.TikaLoader;
import org.apache.tika.metadata.writefilter.MetadataWriteLimiterFactory;
import org.apache.tika.parser.ParseContext;

/**
 * Tests loading all limit configurations from a single tika-config.json file.
 * <p>
 * This test demonstrates how to configure all limits in one place using
 * the "parse-context" section of the JSON configuration.
 * <p>
 * Configuration file: configs/all-limits-test.json
 * <pre>
 * {
 *   "parsers": ["default-parser"],
 *   "parse-context": {
 *     "embedded-limits": {
 *       "maxDepth": 10,
 *       "throwOnMaxDepth": false,
 *       "maxCount": 1000,
 *       "throwOnMaxCount": false
 *     },
 *     "output-limits": {
 *       "writeLimit": 100000,
 *       "throwOnWriteLimit": false,
 *       "maxXmlDepth": 100,
 *       "maxPackageEntryDepth": 10,
 *       "zipBombThreshold": 1000000,
 *       "zipBombRatio": 100
 *     },
 *     "timeout-limits": {
 *       "totalTaskTimeoutMillis": 3600000,
 *       "progressTimeoutMillis": 60000
 *     },
 *     "standard-metadata-limiter-factory": {
 *       "maxTotalBytes": 1048576,
 *       "maxFieldSize": 102400,
 *       "maxKeySize": 1024,
 *       "maxValuesPerField": 100
 *     }
 *   }
 * }
 * </pre>
 */
public class AllLimitsTest extends TikaTest {

    @Test
    public void testLoadAllLimitsFromConfig() throws Exception {
        TikaLoader loader = TikaLoader.load(getConfigPath(getClass(), "all-limits-test.json"));

        // Load all limits into ParseContext
        ParseContext context = loader.loadParseContext();

        // Verify EmbeddedLimits
        EmbeddedLimits embeddedLimits = context.get(EmbeddedLimits.class);
        assertNotNull(embeddedLimits, "EmbeddedLimits should be loaded");
        assertEquals(10, embeddedLimits.getMaxDepth());
        assertFalse(embeddedLimits.isThrowOnMaxDepth());
        assertEquals(1000, embeddedLimits.getMaxCount());
        assertFalse(embeddedLimits.isThrowOnMaxCount());

        // Verify OutputLimits
        OutputLimits outputLimits = context.get(OutputLimits.class);
        assertNotNull(outputLimits, "OutputLimits should be loaded");
        assertEquals(100000, outputLimits.getWriteLimit());
        assertFalse(outputLimits.isThrowOnWriteLimit());
        assertEquals(100, outputLimits.getMaxXmlDepth());
        assertEquals(10, outputLimits.getMaxPackageEntryDepth());
        assertEquals(1000000, outputLimits.getZipBombThreshold());
        assertEquals(100, outputLimits.getZipBombRatio());

        // Verify TimeoutLimits
        TimeoutLimits timeoutLimits = context.get(TimeoutLimits.class);
        assertNotNull(timeoutLimits, "TimeoutLimits should be loaded");
        assertEquals(60000, timeoutLimits.getProgressTimeoutMillis());

        // Verify MetadataWriteLimiterFactory
        MetadataWriteLimiterFactory metadataFactory = context.get(MetadataWriteLimiterFactory.class);
        assertNotNull(metadataFactory, "MetadataWriteLimiterFactory should be loaded");
    }

    @Test
    public void testLoadIndividualLimits() throws Exception {
        TikaLoader loader = TikaLoader.load(getConfigPath(getClass(), "all-limits-test.json"));
        ParseContext context = loader.loadParseContext();

        // Load individual limit configs from ParseContext
        EmbeddedLimits embeddedLimits = context.get(EmbeddedLimits.class);
        assertNotNull(embeddedLimits);
        assertEquals(10, embeddedLimits.getMaxDepth());

        OutputLimits outputLimits = context.get(OutputLimits.class);
        assertNotNull(outputLimits);
        assertEquals(100000, outputLimits.getWriteLimit());

        TimeoutLimits timeoutLimits = context.get(TimeoutLimits.class);
        assertNotNull(timeoutLimits);
        assertEquals(60000, timeoutLimits.getProgressTimeoutMillis());
    }

    @Test
    public void testHelperMethodsWithContext() throws Exception {
        TikaLoader loader = TikaLoader.load(getConfigPath(getClass(), "all-limits-test.json"));
        ParseContext context = loader.loadParseContext();

        // Use helper methods to get limits with fallback defaults
        EmbeddedLimits embeddedLimits = EmbeddedLimits.get(context);
        assertEquals(10, embeddedLimits.getMaxDepth());

        OutputLimits outputLimits = OutputLimits.get(context);
        assertEquals(100000, outputLimits.getWriteLimit());

        TimeoutLimits timeoutLimits = TimeoutLimits.get(context);
        assertEquals(60000, timeoutLimits.getProgressTimeoutMillis());
    }

    @Test
    public void testHelperMethodsWithNullContext() {
        // Helper methods should return defaults when context is null
        EmbeddedLimits embeddedLimits = EmbeddedLimits.get(null);
        assertNotNull(embeddedLimits);
        assertEquals(EmbeddedLimits.UNLIMITED, embeddedLimits.getMaxDepth());

        OutputLimits outputLimits = OutputLimits.get(null);
        assertNotNull(outputLimits);
        assertEquals(OutputLimits.UNLIMITED, outputLimits.getWriteLimit());

        TimeoutLimits timeoutLimits = TimeoutLimits.get(null);
        assertNotNull(timeoutLimits);
        assertEquals(TimeoutLimits.DEFAULT_PROGRESS_TIMEOUT_MILLIS, timeoutLimits.getProgressTimeoutMillis());
    }
}
