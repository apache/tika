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

/**
 * Utility for converting Java class names to kebab-case.
 * Used for automatic component name generation from class names.
 *
 * <p>Examples:
 * <ul>
 *   <li>PDFParser → pdf-parser</li>
 *   <li>OCRParser → ocr-parser</li>
 *   <li>HTMLParser → html-parser</li>
 *   <li>DefaultParser → default-parser</li>
 *   <li>TesseractOCRParser → tesseract-ocr-parser</li>
 * </ul>
 */
public class KebabCaseConverter {

    private KebabCaseConverter() {
        // Utility class
    }

    /**
     * Converts a Java class name to kebab-case.
     *
     * @param className the simple class name (without package)
     * @return the kebab-case version of the name
     */
    public static String toKebabCase(String className) {
        if (className == null || className.isEmpty()) {
            return className;
        }

        // Insert hyphen before uppercase letters that follow lowercase letters
        // or before uppercase letters that are followed by lowercase letters
        String result = className
                // Insert hyphen between lowercase and uppercase: "aB" -> "a-B"
                .replaceAll("([a-z])([A-Z])", "$1-$2")
                // Insert hyphen before uppercase letter followed by lowercase
                // in a sequence of uppercase letters: "HTMLParser" -> "HTML-Parser"
                .replaceAll("([A-Z]+)([A-Z][a-z])", "$1-$2")
                // Insert hyphen between letter and digit: "PDF2Text" -> "PDF2-Text"
                .replaceAll("([a-zA-Z])(\\d)", "$1-$2")
                // Insert hyphen between digit and letter: "2Text" -> "2-Text"
                .replaceAll("(\\d)([a-zA-Z])", "$1-$2")
                .toLowerCase();

        return result;
    }
}
