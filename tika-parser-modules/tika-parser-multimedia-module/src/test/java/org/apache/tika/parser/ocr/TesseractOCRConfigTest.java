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

import java.io.File;
import java.io.InputStream;
import java.util.Arrays;
import java.util.List;

import org.apache.tika.TikaTest;
import org.junit.Test;

public class TesseractOCRConfigTest extends TikaTest {

    @Test
    public void testNoConfig() throws Exception {
        TesseractOCRConfig config = new TesseractOCRConfig();
        assertEquals("Invalid default tesseractPath value", "", config.getTesseractPath());
        assertEquals("Invalid default tessdataPath value", "", config.getTessdataPath());
        assertEquals("Invalid default language value", "eng", config.getLanguage());
        assertEquals("Invalid default pageSegMode value", "1", config.getPageSegMode());
        assertEquals("Invalid default minFileSizeToOcr value", 0, config.getMinFileSizeToOcr());
        assertEquals("Invalid default maxFileSizeToOcr value", Integer.MAX_VALUE, config.getMaxFileSizeToOcr());
        assertEquals("Invalid default timeout value", 120, config.getTimeout());
    }

    @Test
    public void testPartialConfig() throws Exception {

        InputStream stream = TesseractOCRConfigTest.class.getResourceAsStream(
                "/test-properties/TesseractOCRConfig-partial.properties");

        TesseractOCRConfig config = new TesseractOCRConfig(stream);
        assertEquals("Invalid default tesseractPath value", "", config.getTesseractPath());
        assertEquals("Invalid default tessdataPath value", "", config.getTessdataPath());
        assertEquals("Invalid overridden language value", "fra+deu", config.getLanguage());
        assertEquals("Invalid default pageSegMode value", "1", config.getPageSegMode());
        assertEquals("Invalid overridden minFileSizeToOcr value", 1, config.getMinFileSizeToOcr());
        assertEquals("Invalid default maxFileSizeToOcr value", Integer.MAX_VALUE, config.getMaxFileSizeToOcr());
        assertEquals("Invalid overridden timeout value", 240, config.getTimeout());
    }

    @Test
    public void testFullConfig() throws Exception {

        InputStream stream = TesseractOCRConfigTest.class.getResourceAsStream(
                "/test-properties/TesseractOCRConfig-full.properties");

        TesseractOCRConfig config = new TesseractOCRConfig(stream);
        assertEquals("Invalid overridden tesseractPath value", "/opt/tesseract" + File.separator, config.getTesseractPath());
        assertEquals("Invalid overridden tesseractPath value", "/usr/local/share" + File.separator, config.getTessdataPath());
        assertEquals("Invalid overridden language value", "fra+deu", config.getLanguage());
        assertEquals("Invalid overridden pageSegMode value", "2", config.getPageSegMode());
        assertEquals("Invalid overridden minFileSizeToOcr value", 1, config.getMinFileSizeToOcr());
        assertEquals("Invalid overridden maxFileSizeToOcr value", 2000000, config.getMaxFileSizeToOcr());
        assertEquals("Invalid overridden timeout value", 240, config.getTimeout());
    }

    @Test
    public void testValidateValidLanguage() {
        List<String> validLanguages = Arrays.asList(
                "eng", "slk_frak", "chi_tra", "eng+fra", "tgk+chi_tra+slk_frak");

        TesseractOCRConfig config = new TesseractOCRConfig();

        for (String language : validLanguages) {
            config.setLanguage(language);
            assertEquals("Valid language not set", language, config.getLanguage());
        }
    }

    @Test
    public void testValidateInvalidLanguage() {
        List<String> invalidLanguages = Arrays.asList(
                "", "+", "en", "en+", "eng+fra+", "rm -rf *");

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

    @Test(expected=IllegalArgumentException.class)
    public void testValidatePageSegMode() {
        TesseractOCRConfig config = new TesseractOCRConfig();
        config.setPageSegMode("0");
        config.setPageSegMode("10");
        assertTrue("Couldn't set valid values", true);
        config.setPageSegMode("11");
    }

}
