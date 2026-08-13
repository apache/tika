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

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import org.apache.tika.exception.TikaConfigException;

public class TimeoutLimitsTest {

    /**
     * Zero progress with a positive total would kill every task at start; the
     * cross-field check fails config load (via Initializable) instead of failing
     * each request at runtime.
     */
    @Test
    public void testZeroProgressWithPositiveTotalFailsInitialize() {
        TimeoutLimits limits = new TimeoutLimits(3_600_000, 0);
        TikaConfigException e = assertThrows(TikaConfigException.class, limits::initialize);
        assertTrue(e.getMessage().contains("progressTimeoutMillis"), e.getMessage());
    }

    /** (0, 0) is a deliberately fully-exhausted budget and stays valid. */
    @Test
    public void testZeroZeroIsValid() {
        assertDoesNotThrow(new TimeoutLimits(0, 0)::initialize);
        assertDoesNotThrow(new TimeoutLimits()::initialize);
    }
}
