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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import org.apache.tika.parser.ParseContext;

/** otherTesseractConfig is accepted from operator JSON (config file, presets), refused per request. */
public class TesseractOCRConfigTrustTest {

    private static final String JSON =
            "{\"otherTesseractConfig\": {\"tessedit_char_whitelist\": \"0123456789\"}}";

    @Test
    public void operatorJsonIsAccepted() throws Exception {
        ParseContext context = new ParseContext();
        context.setJsonConfig("tesseract-ocr-parser", JSON, true);
        assertEquals("0123456789", new TesseractOCRParser().getConfig(context)
                .getOtherTesseractConfig().get("tessedit_char_whitelist"));
    }

    @Test
    public void requestJsonIsRefused() {
        ParseContext context = new ParseContext();
        context.setJsonConfig("tesseract-ocr-parser", JSON);
        Exception e = assertThrows(Exception.class, () -> new TesseractOCRParser().getConfig(context));
        assertTrue(String.valueOf(e).contains("otherTesseractConfig")
                || String.valueOf(e.getCause()).contains("otherTesseractConfig"), String.valueOf(e));
    }
}
