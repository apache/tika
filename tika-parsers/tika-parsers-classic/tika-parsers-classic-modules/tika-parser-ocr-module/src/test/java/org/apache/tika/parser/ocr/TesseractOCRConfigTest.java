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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.InputStream;
import java.util.Arrays;
import java.util.List;

import org.junit.Test;

import org.apache.tika.TikaTest;
import org.apache.tika.config.TikaConfig;
import org.apache.tika.parser.CompositeParser;

public class TesseractOCRConfigTest extends TikaTest {

    @Test
    public void testNoConfig() throws Exception {
        TesseractOCRConfig config = new TesseractOCRConfig();
        assertEquals("Invalid default language value", "eng", config.getLanguage());
        assertEquals("Invalid default pageSegMode value", "1", config.getPageSegMode());
        assertEquals("Invalid default minFileSizeToOcr value", 0, config.getMinFileSizeToOcr());
        assertEquals("Invalid default maxFileSizeToOcr value", Integer.MAX_VALUE,
                config.getMaxFileSizeToOcr());
        assertEquals("Invalid default timeout value", 120, config.getTimeoutSeconds());
        assertEquals("Invalid default density value", 300, config.getDensity());
        assertEquals("Invalid default depth value", 4, config.getDepth());
        assertEquals("Invalid default colorpsace value", "gray", config.getColorspace());
        assertEquals("Invalid default filter value", "triangle", config.getFilter());
        assertEquals("Invalid default resize value", 200, config.getResize());
        assertEquals("Invalid default applyRotation value", false, config.isApplyRotation());
    }

    @Test
    public void testPartialConfig() throws Exception {

        InputStream stream = getResourceAsStream("/test-configs/tika-config-tesseract-partial.xml");

        TesseractOCRParser parser =
                (TesseractOCRParser) ((CompositeParser) new TikaConfig(stream).getParser())
                        .getAllComponentParsers().get(0);
        TesseractOCRConfig config = parser.getDefaultConfig();
        assertEquals("Invalid overridden language value", "fra+deu", config.getLanguage());
        assertEquals("Invalid default pageSegMode value", "1", config.getPageSegMode());
        assertEquals("Invalid overridden minFileSizeToOcr value", 1, config.getMinFileSizeToOcr());
        assertEquals("Invalid default maxFileSizeToOcr value", Integer.MAX_VALUE,
                config.getMaxFileSizeToOcr());
        assertEquals("Invalid overridden timeout value", 240, config.getTimeoutSeconds());
        assertEquals("Invalid overridden density value", 200, config.getDensity());
        assertEquals("Invalid overridden depth value", 8, config.getDepth());
        assertEquals("Invalid overridden filter value", "box", config.getFilter());
        assertEquals("Invalid overridden resize value", 300, config.getResize());
        assertEquals("Invalid default applyRotation value", false, config.isApplyRotation());
    }

    @Test
    public void testFullConfig() throws Exception {

        InputStream stream = getResourceAsStream("/test-configs/tika-config-tesseract-full.xml");

        TesseractOCRParser parser =
                (TesseractOCRParser) ((CompositeParser) new TikaConfig(stream).getParser())
                        .getAllComponentParsers().get(0);
        TesseractOCRConfig config = parser.getDefaultConfig();
        assertEquals("Invalid overridden language value", "ceb", config.getLanguage());
        assertEquals("Invalid overridden pageSegMode value", "2", config.getPageSegMode());
        assertEquals("Invalid overridden minFileSizeToOcr value", 1, config.getMinFileSizeToOcr());
        assertEquals("Invalid overridden maxFileSizeToOcr value", 2000000,
                config.getMaxFileSizeToOcr());
        assertEquals("Invalid overridden timeout value", 240, config.getTimeoutSeconds());
        assertEquals("Invalid overridden density value", 200, config.getDensity());
        assertEquals("Invalid overridden depth value", 8, config.getDepth());
        assertEquals("Invalid overridden filter value", "box", config.getFilter());
        assertEquals("Invalid overridden resize value", 300, config.getResize());
        assertEquals("Invalid overridden applyRotation value", true, config.isApplyRotation());
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
            assertEquals("Valid language not set", language, config.getLanguage());
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

    @Test(expected = IllegalArgumentException.class)
    public void testValidatePageSegMode() {
        TesseractOCRConfig config = new TesseractOCRConfig();
        config.setPageSegMode("0");
        config.setPageSegMode("10");
        assertTrue("Couldn't set valid values", true);
        config.setPageSegMode("14");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testValidateDensity() {
        TesseractOCRConfig config = new TesseractOCRConfig();
        config.setDensity(300);
        config.setDensity(400);
        assertTrue("Couldn't set valid values", true);
        config.setDensity(1);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testValidateDepth() {
        TesseractOCRConfig config = new TesseractOCRConfig();
        config.setDepth(4);
        config.setDepth(8);
        assertTrue("Couldn't set valid values", true);
        config.setDepth(6);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testValidateFilter() {
        TesseractOCRConfig config = new TesseractOCRConfig();
        config.setFilter("Triangle");
        config.setFilter("box");
        assertTrue("Couldn't set valid values", true);
        config.setFilter("abc");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testValidateResize() {
        TesseractOCRConfig config = new TesseractOCRConfig();
        config.setResize(200);
        config.setResize(400);
        assertTrue("Couldn't set valid values", true);
        config.setResize(1000);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testDataPathCheck() {
        TesseractOCRParser parser = new TesseractOCRParser();
        parser.setTessdataPath("blah\u0000deblah");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testPathCheck() {
        TesseractOCRParser parser = new TesseractOCRParser();
        parser.setTesseractPath("blah\u0000deblah");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testBadOtherKey() {
        TesseractOCRConfig config = new TesseractOCRConfig();
        config.addOtherTesseractConfig("bad bad", "bad");

    }

    @Test(expected = IllegalArgumentException.class)
    public void testBadOtherValue() {
        TesseractOCRConfig config = new TesseractOCRConfig();
        config.addOtherTesseractConfig("bad", "bad bad");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testBadOtherValueSlash() {
        TesseractOCRConfig config = new TesseractOCRConfig();
        config.addOtherTesseractConfig("bad", "bad\\bad");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testBadOtherValueControl() {
        TesseractOCRConfig config = new TesseractOCRConfig();
        config.addOtherTesseractConfig("bad", "bad\u0001bad");
    }

    @Test
    public void testGoodOtherParameters() {
        TesseractOCRConfig config = new TesseractOCRConfig();
        config.addOtherTesseractConfig("good", "good");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testBadLanguageCode() throws Exception {
        TesseractOCRConfig tesseractOCRConfig = new TesseractOCRConfig();
        tesseractOCRConfig.setLanguage("kerplekistani");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testBadColorSpace() {
        TesseractOCRConfig config = new TesseractOCRConfig();
        config.setColorspace("someth!ng");
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
