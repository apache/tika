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
package org.apache.tika.parser.pkg;

import java.io.Serializable;
import java.nio.charset.Charset;
import java.nio.charset.UnsupportedCharsetException;

import org.apache.tika.exception.TikaConfigException;

/**
 * Configuration for {@link ZipParser}.
 */
public class ZipParserConfig implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * Whether to run charset detection on entry names to handle
     * non-Unicode filenames. Default is true.
     */
    private boolean detectCharsetsInEntryNames = true;

    /**
     * The charset to use for reading entry names. If null, the parser
     * will use the platform default or auto-detect based on
     * {@link #detectCharsetsInEntryNames}.
     */
    private Charset entryEncoding = null;

    /**
     * Whether to perform integrity checking by comparing the central directory
     * (read via file-based access) against local file headers (read via streaming).
     * This can detect:
     * <ul>
     *   <li>Duplicate entry names (potential attack vector)</li>
     *   <li>Entries in central directory but not in local headers</li>
     *   <li>Entries in local headers but not in central directory</li>
     * </ul>
     * Default is true. When enabled, the ZIP is parsed twice if file-based access
     * succeeds. If only streaming is possible, duplicate detection is still performed
     * but central directory comparison is skipped (result will be "PARTIAL" if no
     * duplicates are found).
     */
    private boolean integrityCheck = true;

    public ZipParserConfig() {
    }

    public boolean isDetectCharsetsInEntryNames() {
        return detectCharsetsInEntryNames;
    }

    public void setDetectCharsetsInEntryNames(boolean detectCharsetsInEntryNames) {
        this.detectCharsetsInEntryNames = detectCharsetsInEntryNames;
    }

    public Charset getEntryEncoding() {
        return entryEncoding;
    }

    public void setEntryEncoding(Charset entryEncoding) {
        this.entryEncoding = entryEncoding;
    }

    /**
     * Set the entry encoding from a string (for JSON deserialization).
     *
     * @param charsetName the charset name
     * @throws TikaConfigException if the charset is not supported
     */
    public void setEntryEncodingName(String charsetName) throws TikaConfigException {
        if (charsetName == null || charsetName.isEmpty()) {
            this.entryEncoding = null;
            return;
        }
        try {
            this.entryEncoding = Charset.forName(charsetName);
        } catch (UnsupportedCharsetException e) {
            throw new TikaConfigException("Unsupported charset: " + charsetName, e);
        }
    }

    public boolean isIntegrityCheck() {
        return integrityCheck;
    }

    public void setIntegrityCheck(boolean integrityCheck) {
        this.integrityCheck = integrityCheck;
    }
}
