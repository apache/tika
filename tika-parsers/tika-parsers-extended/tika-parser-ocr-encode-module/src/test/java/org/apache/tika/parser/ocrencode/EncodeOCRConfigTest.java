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
package org.apache.tika.parser.ocrencode;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import org.apache.tika.TikaTest;
import org.apache.tika.config.loader.TikaLoader;
import org.apache.tika.parser.CompositeParser;

public class EncodeOCRConfigTest extends TikaTest {

    @Test
    public void testDefaultConfig() {
        EncodeOCRConfig config = new EncodeOCRConfig();
        assertEquals(0, config.getMinFileSizeToOcr(),
                "Invalid default minFileSizeToOcr value");
        assertEquals(EncodeOCRConfig.DEFAULT_MAX_FILE_SIZE_TO_OCR,
                config.getMaxFileSizeToOcr(),
                "Invalid default maxFileSizeToOcr value");
        assertFalse(config.isSkipOcr(),
                "Invalid default skipOcr value");
        assertEquals(50, config.getMaxImagesToOcr(),
                "Invalid default maxImagesToOcr value");
        assertFalse(config.isInlineContent(),
                "Invalid default inlineContent value");
    }

    @Test
    public void testMaxImagesToOcrValidation() {
        EncodeOCRConfig config = new EncodeOCRConfig();
        config.setMaxImagesToOcr(0);
        assertEquals(0, config.getMaxImagesToOcr());
        config.setMaxImagesToOcr(100);
        assertEquals(100, config.getMaxImagesToOcr());

        assertThrows(IllegalArgumentException.class, () -> {
            config.setMaxImagesToOcr(-1);
        });
    }

    @Test
    public void testFullConfig() throws Exception {
        TikaLoader loader = TikaLoader.load(
                getConfigPath(EncodeOCRConfigTest.class,
                        "tika-config-encodeocr-full.json"));
        EncodeOCRParser parser =
                (EncodeOCRParser) ((CompositeParser) loader.loadParsers())
                        .getAllComponentParsers().get(0);
        EncodeOCRConfig config = parser.getDefaultConfig();
        assertEquals(1, config.getMinFileSizeToOcr(),
                "Invalid overridden minFileSizeToOcr value");
        assertEquals(2000000, config.getMaxFileSizeToOcr(),
                "Invalid overridden maxFileSizeToOcr value");
        assertEquals(25, config.getMaxImagesToOcr(),
                "Invalid overridden maxImagesToOcr value");
        assertFalse(config.isSkipOcr(),
                "Invalid overridden skipOcr value");
    }

    @Test
    public void testPartialConfig() throws Exception {
        TikaLoader loader = TikaLoader.load(
                getConfigPath(EncodeOCRConfigTest.class,
                        "tika-config-encodeocr-partial.json"));
        EncodeOCRParser parser =
                (EncodeOCRParser) ((CompositeParser) loader.loadParsers())
                        .getAllComponentParsers().get(0);
        EncodeOCRConfig config = parser.getDefaultConfig();
        assertEquals(1, config.getMinFileSizeToOcr(),
                "Invalid overridden minFileSizeToOcr value");
        assertEquals(EncodeOCRConfig.DEFAULT_MAX_FILE_SIZE_TO_OCR,
                config.getMaxFileSizeToOcr(),
                "Invalid default maxFileSizeToOcr value");
        assertEquals(10, config.getMaxImagesToOcr(),
                "Invalid overridden maxImagesToOcr value");
        assertFalse(config.isSkipOcr(),
                "Invalid default skipOcr value");
    }

    @Test
    public void testSkipOcrConfig() throws Exception {
        TikaLoader loader = TikaLoader.load(
                getConfigPath(EncodeOCRConfigTest.class,
                        "tika-config-encodeocr-skip.json"));
        EncodeOCRParser parser =
                (EncodeOCRParser) ((CompositeParser) loader.loadParsers())
                        .getAllComponentParsers().get(0);
        EncodeOCRConfig config = parser.getDefaultConfig();
        assertTrue(config.isSkipOcr(),
                "skipOcr should be true when set in config");
    }
}
