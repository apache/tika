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

import org.apache.commons.lang.SystemUtils;
import org.apache.tika.TikaTest;
import org.junit.Test;

import java.io.File;
import java.io.InputStream;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class TesseractOCRConfigTest extends TikaTest {

    private TesseractOCRConfig config = new TesseractOCRConfig();

    @Test
    public void testDefaults() {
        assertEquals("Invalid default tesseractPath value", "", config.getTesseractPath());
        assertEquals("Invalid default tessdataPath value", "", config.getTessdataPath());
        assertEquals("Invalid default language value", "eng", config.getLanguage());
        assertEquals("Invalid default pageSegMode value", "1", config.getPageSegMode());
        assertEquals("Invalid default minFileSizeToOcr value", 0, config.getMinFileSizeToOcr());
        assertEquals("Invalid default maxFileSizeToOcr value", Integer.MAX_VALUE, config.getMaxFileSizeToOcr());
        assertEquals("Invalid default timeout value", 120, config.getTimeout());
        assertEquals("Invalid default ImageMagickPath value", "", config.getImageMagickPath());
        assertEquals("Invalid default density value", 300, config.getDensity());
        assertEquals("Invalid default depth value", 4, config.getDepth());
        assertEquals("Invalid default colorpsace value", "gray", config.getColorspace());
        assertEquals("Invalid default filter value", "triangle", config.getFilter());
        assertEquals("Invalid default resize value", 900, config.getResize());
    }

    @Test
    public void partialConfig() {
        InputStream stream = TesseractOCRConfigTest.class.getResourceAsStream(
                "/test-properties/TesseractOCRConfig-partial.properties");

        config = new TesseractOCRConfig(stream);
        assertEquals("Invalid default tesseractPath value", "", config.getTesseractPath());
        assertEquals("Invalid default tessdataPath value", "", config.getTessdataPath());
        assertEquals("Invalid overridden language value", "fra+deu", config.getLanguage());
        assertEquals("Invalid default pageSegMode value", "1", config.getPageSegMode());
        assertEquals("Invalid overridden minFileSizeToOcr value", 1, config.getMinFileSizeToOcr());
        assertEquals("Invalid default maxFileSizeToOcr value", Integer.MAX_VALUE, config.getMaxFileSizeToOcr());
        assertEquals("Invalid overridden timeout value", 240, config.getTimeout());
        assertEquals("Invalid default ImageMagickPath value", "", config.getImageMagickPath());
        assertEquals("Invalid overridden density value", 200, config.getDensity());
        assertEquals("Invalid overridden depth value", 8, config.getDepth());
        assertEquals("Invalid overridden filter value", "box", config.getFilter());
        assertEquals("Invalid overridden resize value", 300, config.getResize());
    }

    @Test
    public void fullConfig() {
        InputStream stream = TesseractOCRConfigTest.class.getResourceAsStream(
                "/test-properties/TesseractOCRConfig-full.properties");

        config = new TesseractOCRConfig(stream);
        if (SystemUtils.IS_OS_UNIX) {
            assertEquals("Invalid overridden tesseractPath value", "/opt/tesseract" + File.separator, config.getTesseractPath());
            assertEquals("Invalid overridden tesseractPath value", "/usr/local/share" + File.separator, config.getTessdataPath());
            assertEquals("Invalid overridden ImageMagickPath value", "/usr/local/bin/", config.getImageMagickPath());
        }
        assertEquals("Invalid overridden language value", "fra+deu", config.getLanguage());
        assertEquals("Invalid overridden pageSegMode value", "2", config.getPageSegMode());
        assertEquals("Invalid overridden minFileSizeToOcr value", 1, config.getMinFileSizeToOcr());
        assertEquals("Invalid overridden maxFileSizeToOcr value", 2000000, config.getMaxFileSizeToOcr());
        assertEquals("Invalid overridden timeout value", 240, config.getTimeout());
        assertEquals("Invalid overridden density value", 200, config.getDensity());
        assertEquals("Invalid overridden depth value", 8, config.getDepth());
        assertEquals("Invalid overridden filter value", "box", config.getFilter());
        assertEquals("Invalid overridden resize value", 300, config.getResize());
    }

    @Test
    public void validateValidLanguage() {
        List<String> validLanguages = Arrays.asList(
                "eng", "slk_frak", "chi_tra", "eng+fra", "tgk+chi_tra+slk_frak");

        for (String language : validLanguages) {
            config.setLanguage(language);
            assertEquals("Valid language not set", language, config.getLanguage());
        }
    }

    @Test
    public void validateInvalidLanguage() {
        List<String> invalidLanguages = Arrays.asList(
                "", "+", "en", "en+", "eng+fra+", "rm -rf *");

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
    public void validateValidPageSegMode() {
        List<String> validPageSegModes = Arrays.asList("1", "9", "10");

        for (String pageSegMode : validPageSegModes) {
            config.setPageSegMode(pageSegMode);
            assertEquals("Valid page segmentation mode not set",
                    pageSegMode, config.getPageSegMode());
        }
    }

    @Test
    public void validateInvalidPageSegMode() {
        List<String> invalidPageSegModes = Arrays.asList("", "-1", "0", "11");

        for (String pageSegMode : invalidPageSegModes) {
            try {
                config.setPageSegMode(pageSegMode);
                fail("Invalid page segmentation mode set: " + pageSegMode);
            } catch (IllegalArgumentException e) {
                // expected exception thrown
            }
        }
    }

    @Test
    public void validateValidDensity() {
        List<Integer> validDensities = Arrays.asList(150, 1200);

        for (int density : validDensities) {
            config.setDensity(density);
            assertEquals("Valid density not set",
                    density, config.getDensity());
        }
    }

    @Test
    public void validateInvalidDensity() {
        List<Integer> invalidDensities = Arrays.asList(149, 1201, -1, 0);

        for (int density : invalidDensities) {
            try {
                config.setDensity(density);
                fail("Invalid density set: " + density);
            } catch (IllegalArgumentException e) {
                // expected exception thrown
            }
        }
    }

    @Test
    public void validateValidDepth() {
        List<Integer> validDepths = Arrays.asList(2, 4, 8, 16, 32, 64, 256, 4096);

        for (int depth : validDepths) {
            config.setDepth(depth);
            assertEquals("Valid depth not set",
                    depth, config.getDepth());
        }
    }

    @Test
    public void validateInvalidDepth() {
        List<Integer> invalidDepths = Arrays.asList(-1, 0, 1, 3, Integer.MAX_VALUE);

        for (int depth : invalidDepths) {
            try {
                config.setDepth(depth);
                fail("Invalid depth set: " + depth);
            } catch (IllegalArgumentException e) {
                // expected exception thrown
            }
        }
    }

    @Test
    public void validateValidFilter() {
        List<String> validFilters = Arrays.asList(
                "POINT", "hermite", "CuBiC", "Box", "Gaussian",
                "Catrom", "Triangle", "Quadratic", "Mitchell");

        for (String filter : validFilters) {
            config.setFilter(filter);
            assertEquals("Valid filter not set",
                    filter, config.getFilter());
        }
    }

    @Test
    public void validateInvalidFilter() {
        List<String> invalidFilters = Arrays.asList("", "box ", "abc");

        for (String filter : invalidFilters) {
            try {
                config.setFilter(filter);
                fail("Invalid filter set: " + filter);
            } catch (IllegalArgumentException e) {
                // expected exception thrown
            }
        }
    }

    @Test
    public void validateValidResize() {
        // TODO: why only 100, 200, ..., 900?
        List<Integer> validResizes = Arrays.asList(100, 500, 900);

        for (int resize : validResizes) {
            config.setResize(resize);
            assertEquals("Valid resize not set",
                    resize, config.getResize());
        }
    }

    @Test
    public void validateInvalidResize() {
        List<Integer> invalidResizes = Arrays.asList(-1, 0, 1, 3, Integer.MAX_VALUE);

        for (int resize : invalidResizes) {
            try {
                config.setResize(resize);
                fail("Invalid resize set: " + resize);
            } catch (IllegalArgumentException e) {
                // expected exception thrown
            }
        }
    }

}
