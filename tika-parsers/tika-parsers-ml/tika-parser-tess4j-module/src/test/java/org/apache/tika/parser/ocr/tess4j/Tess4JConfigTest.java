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
package org.apache.tika.parser.ocr.tess4j;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import org.apache.tika.exception.TikaConfigException;

public class Tess4JConfigTest {

    @Test
    public void testDefaults() {
        Tess4JConfig config = new Tess4JConfig();
        assertEquals("eng", config.getLanguage());
        assertEquals("", config.getDataPath());
        assertEquals(1, config.getPageSegMode());
        assertEquals(3, config.getOcrEngineMode());
        assertEquals(50 * 1024 * 1024, config.getMaxFileSizeToOcr());
        assertEquals(0, config.getMinFileSizeToOcr());
        assertEquals(2, config.getPoolSize());
        assertEquals(120, config.getTimeoutSeconds());
        assertFalse(config.isSkipOcr());
        assertEquals(300, config.getDpi());
    }

    @Test
    public void testSetLanguageValid() {
        Tess4JConfig config = new Tess4JConfig();
        config.setLanguage("eng+fra");
        assertEquals("eng+fra", config.getLanguage());
    }

    @Test
    public void testSetLanguageInvalid() {
        Tess4JConfig config = new Tess4JConfig();
        assertThrows(IllegalArgumentException.class, () -> config.setLanguage("xy"));
    }

    @Test
    public void testSetLanguageLeadingPlus() {
        Tess4JConfig config = new Tess4JConfig();
        assertThrows(IllegalArgumentException.class, () -> config.setLanguage("+eng"));
    }

    @Test
    public void testSetPageSegModeValid() {
        Tess4JConfig config = new Tess4JConfig();
        config.setPageSegMode(6);
        assertEquals(6, config.getPageSegMode());
    }

    @Test
    public void testSetPageSegModeInvalid() {
        Tess4JConfig config = new Tess4JConfig();
        assertThrows(IllegalArgumentException.class, () -> config.setPageSegMode(14));
        assertThrows(IllegalArgumentException.class, () -> config.setPageSegMode(-1));
    }

    @Test
    public void testSetOcrEngineModeValid() {
        Tess4JConfig config = new Tess4JConfig();
        config.setOcrEngineMode(1);
        assertEquals(1, config.getOcrEngineMode());
    }

    @Test
    public void testSetOcrEngineModeInvalid() {
        Tess4JConfig config = new Tess4JConfig();
        assertThrows(IllegalArgumentException.class, () -> config.setOcrEngineMode(4));
        assertThrows(IllegalArgumentException.class, () -> config.setOcrEngineMode(-1));
    }

    @Test
    public void testSetPoolSizeValid() {
        Tess4JConfig config = new Tess4JConfig();
        config.setPoolSize(4);
        assertEquals(4, config.getPoolSize());
    }

    @Test
    public void testSetPoolSizeInvalid() {
        Tess4JConfig config = new Tess4JConfig();
        assertThrows(IllegalArgumentException.class, () -> config.setPoolSize(0));
        assertThrows(IllegalArgumentException.class, () -> config.setPoolSize(-1));
    }

    @Test
    public void testSetDpiValid() {
        Tess4JConfig config = new Tess4JConfig();
        config.setDpi(150);
        assertEquals(150, config.getDpi());
    }

    @Test
    public void testSetDpiInvalid() {
        Tess4JConfig config = new Tess4JConfig();
        assertThrows(IllegalArgumentException.class, () -> config.setDpi(50));
        assertThrows(IllegalArgumentException.class, () -> config.setDpi(1500));
    }

    @Test
    public void testSkipOcr() {
        Tess4JConfig config = new Tess4JConfig();
        config.setSkipOcr(true);
        assertTrue(config.isSkipOcr());
    }

    @Test
    public void testRuntimeConfigBlocksDataPath() {
        Tess4JConfig.RuntimeConfig config = new Tess4JConfig.RuntimeConfig();
        assertThrows(TikaConfigException.class,
                () -> config.setDataPath("/some/path"));
    }

    @Test
    public void testRuntimeConfigAllowsEmptyDataPath() throws TikaConfigException {
        Tess4JConfig.RuntimeConfig config = new Tess4JConfig.RuntimeConfig();
        config.setDataPath("");
        assertEquals("", config.getDataPath());
    }
}
