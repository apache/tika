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
package org.apache.tika.parser.html.charsetdetector;

import static java.nio.charset.StandardCharsets.UTF_16BE;
import static java.nio.charset.StandardCharsets.UTF_16LE;
import static java.nio.charset.StandardCharsets.UTF_8;

import java.nio.charset.Charset;


/**
 * A detection may either not find a charset, find an invalid charset, or find a valid charset
 */
class CharsetDetectionResult {
    private boolean found = false;
    private Charset charset = null;

    private CharsetDetectionResult() { /* default result: not found */}

    static CharsetDetectionResult notFound() {
        return new CharsetDetectionResult();
    }

    public boolean isFound() {
        return found;
    }

    public void find(String charsetName) {
        this.found = true;
        charsetName = charsetName.trim();
        if ("x-user-defined".equals(charsetName)) {
            charsetName = "windows-1252";
        }
        this.charset = CharsetAliases.getCharsetByLabel(charsetName);
        // The specification states: If charset is a UTF-16 encoding, then set charset to UTF-8.
        if (UTF_16LE.equals(charset) || UTF_16BE.equals(charset)) {
            charset = UTF_8;
        }
    }

    public Charset getCharset() {
        // the result may be null even if found is true, in the case there is a charset specified,
        // but it is invalid
        return charset;
    }

    public void setCharset(Charset charset) {
        this.found = true;
        this.charset = charset;
    }
}
