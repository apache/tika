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
package org.apache.tika.parser.ocr;

import java.io.InputStream;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

import org.apache.tika.TikaTest;
import org.apache.tika.config.TikaConfig;
import org.apache.tika.parser.CompositeParser;

import static org.junit.jupiter.api.Assertions.*;

public class TesseractOCRConfigTest extends TikaTest {

    @Test
    public void testNoConfig() throws Exception {
        TesseractOCRConfig config = new TesseractOCRConfig();
        assertEquals("eng", config.getLanguage(), "Invalid default language value");
        assertEquals("1", config.getPageSegMode(), "Invalid default pageSegMode value");
        assertEquals(0, config.getMinFileSizeToOcr(), "Invalid default minFileSizeToOcr value");
        assertEquals(Integer.MAX_VALUE, config.getMaxFileSizeToOcr(), "Invalid default maxFileSizeToOcr value");
        assertEquals(120, config.getTimeoutSeconds(), "Invalid default timeout value");
        assertEquals(300, config.getDensity(), "Invalid default density value");
        assertEquals(4, config.getDepth(), "Invalid default depth value");
        assertEquals("gray", config.getColorspace(), "Invalid default colorpsace value");
        assertEquals("triangle", config.getFilter(), "Invalid default filter value");
        assertEquals(200, config.getResize(), "Invalid default resize value");
        assertFalse(config.isApplyRotation(), "Invalid default applyRotation value");
    }

    @Test
    public void testPartialConfig() throws Exception {

        InputStream stream = getResourceAsStream("/test-configs/tika-config-tesseract-partial.xml");

        TesseractOCRParser parser =
                (TesseractOCRParser) ((CompositeParser) new TikaConfig(stream).getParser())
                        .getAllComponentParsers().get(0);
        TesseractOCRConfig config = parser.getDefaultConfig();
        assertEquals("fra+deu", config.getLanguage(), "Invalid overridden language value");
        assertEquals("1", config.getPageSegMode(), "Invalid default pageSegMode value");
        assertEquals(1, config.getMinFileSizeToOcr(), "Invalid overridden minFileSizeToOcr value");
        assertEquals(Integer.MAX_VALUE, config.getMaxFileSizeToOcr(), "Invalid default maxFileSizeToOcr value");
        assertEquals(240, config.getTimeoutSeconds(), "Invalid overridden timeout value");
        assertEquals(200, config.getDensity(), "Invalid overridden density value");
        assertEquals(8, config.getDepth(), "Invalid overridden depth value");
        assertEquals("box", config.getFilter(), "Invalid overridden filter value");
        assertEquals(300, config.getResize(), "Invalid overridden resize value");
        assertFalse(config.isApplyRotation(), "Invalid default applyRotation value");
    }

    @Test
    public void testFullConfig() throws Exception {

        InputStream stream = getResourceAsStream("/test-configs/tika-config-tesseract-full.xml");

        TesseractOCRParser parser =
                (TesseractOCRParser) ((CompositeParser) new TikaConfig(stream).getParser())
                        .getAllComponentParsers().get(0);
        TesseractOCRConfig config = parser.getDefaultConfig();
        assertEquals("ceb", config.getLanguage(), "Invalid overridden language value");
        assertEquals("2", config.getPageSegMode(), "Invalid default pageSegMode value");
        assertEquals(1, config.getMinFileSizeToOcr(), "Invalid overridden minFileSizeToOcr value");
        assertEquals(2000000, config.getMaxFileSizeToOcr(), "Invalid default maxFileSizeToOcr " +
                "value");
        assertEquals(240, config.getTimeoutSeconds(), "Invalid overridden timeout value");
        assertEquals(200, config.getDensity(), "Invalid overridden density value");
        assertEquals(8, config.getDepth(), "Invalid overridden depth value");
        assertEquals("box", config.getFilter(), "Invalid overridden filter value");
        assertEquals(300, config.getResize(), "Invalid overridden resize value");
        assertTrue(config.isApplyRotation(), "Invalid default applyRotation value");
    }

    @Test
    public void testValidateValidLanguage() {
        List<String> validLanguages =
                Arrays.asList("eng", "slk_frak", "chi_tra", "eng+fra", "tgk+chi_tra+slk_frak",
                        "chi_tra_vert", "tgk+chi_tra_vert+slk_frak", "eng+script/Arabic",
                        "script/HanT_vert");

        TesseractOCRConfig config = new TesseractOCRConfig();

        for (String language : validLanguages) {
            config.setLanguage(language);
            assertEquals(language, config.getLanguage(), "Valid language not set");
        }
    }

    @Test
    public void testValidateInvalidLanguage() {
        List<String> invalidLanguages = Arrays.asList(
                //"", allow empty string
                "+", "en", "en+", "eng+fra+", "Arabic", "/script/Arabic", "rm -rf *");

        TesseractOCRConfig config = new TesseractOCRConfig();

        for (String language : invalidLanguages) {
            try {
                config.setLanguage(language);
                fail("Invalid language set: " + language);
            } catch (IllegalArgumentException e) {
                // expected exception thrown
            }
        }
    }

    @Test
    public void testValidatePageSegMode() {
        TesseractOCRConfig config = new TesseractOCRConfig();
        config.setPageSegMode("0");
        config.setPageSegMode("10");
        assertTrue(true, "Couldn't set valid values");
        assertThrows(IllegalArgumentException.class, () -> {
            config.setPageSegMode("14");
        });
    }

    @Test
    public void testValidateDensity() {
        TesseractOCRConfig config = new TesseractOCRConfig();
        config.setDensity(300);
        config.setDensity(400);
        assertTrue(true, "Couldn't set valid values");
        assertThrows(IllegalArgumentException.class, () -> {
            config.setDensity(1);
        });
    }

    @Test
    public void testValidateDepth() {
        TesseractOCRConfig config = new TesseractOCRConfig();
        config.setDepth(4);
        config.setDepth(8);
        assertTrue(true, "Couldn't set valid values");
        assertThrows(IllegalArgumentException.class, () -> {
            config.setDepth(6);
        });
    }

    @Test
    public void testValidateFilter() {
        TesseractOCRConfig config = new TesseractOCRConfig();
        config.setFilter("Triangle");
        config.setFilter("box");
        assertTrue(true, "Couldn't set valid values");
        assertThrows(IllegalArgumentException.class, () -> {
            config.setFilter("abc");
        });
    }

    @Test
    public void testValidateResize() {
        TesseractOCRConfig config = new TesseractOCRConfig();
        config.setResize(200);
        config.setResize(400);
        assertTrue(true, "Couldn't set valid values");
        assertThrows(IllegalArgumentException.class, () -> {
            config.setResize(1000);
        });
    }

    @Test
    public void testDataPathCheck() {
        TesseractOCRParser parser = new TesseractOCRParser();
        assertThrows(IllegalArgumentException.class, () -> {
            parser.setTessdataPath("blah\u0000deblah");
        });
    }

    @Test
    public void testPathCheck() {
        TesseractOCRParser parser = new TesseractOCRParser();
        assertThrows(IllegalArgumentException.class, () -> {
            parser.setTesseractPath("blah\u0000deblah");
        });
    }

    @Test
    public void testBadOtherKey() {
        TesseractOCRConfig config = new TesseractOCRConfig();
        assertThrows(IllegalArgumentException.class, () -> {
            config.addOtherTesseractConfig("bad bad", "bad");
        });
    }

    @Test
    public void testBadOtherValue() {
        TesseractOCRConfig config = new TesseractOCRConfig();
        assertThrows(IllegalArgumentException.class, () -> {
            config.addOtherTesseractConfig("bad", "bad bad");
        });
    }

    @Test
    public void testBadOtherValueSlash() {
        TesseractOCRConfig config = new TesseractOCRConfig();
        assertThrows(IllegalArgumentException.class, () -> {
            config.addOtherTesseractConfig("bad", "bad\\bad");
        });
    }

    @Test
    public void testBadOtherValueControl() {
        TesseractOCRConfig config = new TesseractOCRConfig();
        assertThrows(IllegalArgumentException.class, () -> {
            config.addOtherTesseractConfig("bad", "bad\u0001bad");
        });
    }

    @Test
    public void testGoodOtherParameters() {
        TesseractOCRConfig config = new TesseractOCRConfig();
        config.addOtherTesseractConfig("good", "good");
    }

    @Test
    public void testBadLanguageCode() throws Exception {
        TesseractOCRConfig tesseractOCRConfig = new TesseractOCRConfig();
        assertThrows(IllegalArgumentException.class, () -> {
            tesseractOCRConfig.setLanguage("kerplekistani");
        });
    }

    @Test
    public void testBadColorSpace() {
        TesseractOCRConfig config = new TesseractOCRConfig();
        assertThrows(IllegalArgumentException.class, () -> {
            config.setColorspace("someth!ng");
        });
    }

    @Test
    public void testNullFilter() {
        TesseractOCRConfig config = new TesseractOCRConfig();
        assertThrows(IllegalArgumentException.class, () -> {
            config.setFilter(null);
        });
    }

    @Test
    public void testUpdatingConfigs() throws Exception {
        TesseractOCRConfig configA = new TesseractOCRConfig();
        configA.setLanguage("eng");
        configA.setMinFileSizeToOcr(100);
        configA.setOutputType(TesseractOCRConfig.OUTPUT_TYPE.TXT);
        configA.addOtherTesseractConfig("k1", "a1");
        configA.addOtherTesseractConfig("k2", "a2");

        TesseractOCRConfig configB = new TesseractOCRConfig();
        configB.setLanguage("fra");
        configB.setMinFileSizeToOcr(1000);
        configB.setOutputType(TesseractOCRConfig.OUTPUT_TYPE.HOCR);
        configB.addOtherTesseractConfig("k1", "b1");
        configB.addOtherTesseractConfig("k2", "b2");

        TesseractOCRConfig clone = configA.cloneAndUpdate(configB);
        assertEquals("fra", clone.getLanguage());
        assertEquals(1000, clone.getMinFileSizeToOcr());
        assertEquals(TesseractOCRConfig.OUTPUT_TYPE.HOCR, clone.getOutputType());
        assertEquals("b1", clone.getOtherTesseractConfig().get("k1"));
        assertEquals("b2", clone.getOtherTesseractConfig().get("k2"));
    }
}
