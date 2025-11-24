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
package org.apache.tika.annotation;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for KebabCaseConverter.
 */
public class KebabCaseConverterTest {

    @Test
    public void testSimpleClassName() {
        assertEquals("parser", KebabCaseConverter.toKebabCase("Parser"));
        assertEquals("detector", KebabCaseConverter.toKebabCase("Detector"));
    }

    @Test
    public void testTwoWordClassName() {
        assertEquals("pdf-parser", KebabCaseConverter.toKebabCase("PDFParser"));
        assertEquals("html-parser", KebabCaseConverter.toKebabCase("HTMLParser"));
        assertEquals("ocr-parser", KebabCaseConverter.toKebabCase("OCRParser"));
    }

    @Test
    public void testMixedCase() {
        assertEquals("default-parser", KebabCaseConverter.toKebabCase("DefaultParser"));
        assertEquals("composite-detector", KebabCaseConverter.toKebabCase("CompositeDetector"));
    }

    @Test
    public void testAcronymsFollowedByWord() {
        assertEquals("html-parser", KebabCaseConverter.toKebabCase("HTMLParser"));
        assertEquals("xml-parser", KebabCaseConverter.toKebabCase("XMLParser"));
        assertEquals("tesseract-ocr-parser", KebabCaseConverter.toKebabCase("TesseractOCRParser"));
    }

    @Test
    public void testNumbersInName() {
        assertEquals("pdf-2-text-parser", KebabCaseConverter.toKebabCase("PDF2TextParser"));
        assertEquals("mp-3-parser", KebabCaseConverter.toKebabCase("MP3Parser"));
    }

    @Test
    public void testEdgeCases() {
        assertEquals("", KebabCaseConverter.toKebabCase(null));
        assertEquals("", KebabCaseConverter.toKebabCase(""));
        assertEquals("a", KebabCaseConverter.toKebabCase("A"));
        assertEquals("ab", KebabCaseConverter.toKebabCase("AB"));
    }

    @Test
    public void testAlreadyLowerCase() {
        assertEquals("parser", KebabCaseConverter.toKebabCase("parser"));
    }

    @Test
    public void testComplexNames() {
        assertEquals("microsoft-office-parser",
                KebabCaseConverter.toKebabCase("MicrosoftOfficeParser"));
        assertEquals("zip-container-detector",
                KebabCaseConverter.toKebabCase("ZipContainerDetector"));
    }
}
