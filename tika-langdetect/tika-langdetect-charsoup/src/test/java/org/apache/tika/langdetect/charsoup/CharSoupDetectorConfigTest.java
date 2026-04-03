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
package org.apache.tika.langdetect.charsoup;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Collections;
import java.util.Map;

import org.junit.jupiter.api.Test;

public class CharSoupDetectorConfigTest {

    @Test
    public void testDefaultConfig() {
        CharSoupDetectorConfig cfg = CharSoupDetectorConfig.DEFAULT;
        assertEquals(CharSoupLanguageDetector.Strategy.AUTOMATIC, cfg.getStrategy());
    }

    @Test
    public void testFromMapNullReturnsDefault() {
        assertEquals(CharSoupDetectorConfig.DEFAULT, CharSoupDetectorConfig.fromMap(null));
    }

    @Test
    public void testFromMapEmptyReturnsDefault() {
        assertEquals(CharSoupDetectorConfig.DEFAULT,
                CharSoupDetectorConfig.fromMap(Collections.emptyMap()));
    }

    @Test
    public void testFromMapStrategyAutomatic() {
        CharSoupDetectorConfig cfg =
                CharSoupDetectorConfig.fromMap(Map.of("strategy", "AUTOMATIC"));
        assertEquals(CharSoupLanguageDetector.Strategy.AUTOMATIC, cfg.getStrategy());
    }

    @Test
    public void testFromMapStrategyGlm() {
        CharSoupDetectorConfig cfg =
                CharSoupDetectorConfig.fromMap(Map.of("strategy", "GLM"));
        assertEquals(CharSoupLanguageDetector.Strategy.GLM, cfg.getStrategy());
    }

    @Test
    public void testFromMapStrategyStandard() {
        CharSoupDetectorConfig cfg =
                CharSoupDetectorConfig.fromMap(Map.of("strategy", "STANDARD"));
        assertEquals(CharSoupLanguageDetector.Strategy.STANDARD, cfg.getStrategy());
    }

    @Test
    public void testFromMapStrategyCaseInsensitive() {
        CharSoupDetectorConfig cfg =
                CharSoupDetectorConfig.fromMap(Map.of("strategy", "glm"));
        assertEquals(CharSoupLanguageDetector.Strategy.GLM, cfg.getStrategy());
    }

    @Test
    public void testFromMapUnknownKeysIgnored() {
        CharSoupDetectorConfig cfg = CharSoupDetectorConfig.fromMap(
                Map.of("bogusKey", "ignored", "strategy", "STANDARD"));
        assertEquals(CharSoupLanguageDetector.Strategy.STANDARD, cfg.getStrategy());
    }

    @Test
    public void testFromMapInvalidStrategyThrows() {
        assertThrows(IllegalArgumentException.class,
                () -> CharSoupDetectorConfig.fromMap(Map.of("strategy", "INVALID")));
    }

    @Test
    public void testToStringContainsStrategy() {
        String s = CharSoupDetectorConfig.DEFAULT.toString();
        assertNotNull(s);
    }
}
