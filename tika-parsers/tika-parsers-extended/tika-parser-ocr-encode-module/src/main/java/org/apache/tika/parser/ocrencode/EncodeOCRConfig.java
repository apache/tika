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

import java.io.Serializable;

/**
 * Configuration for {@link EncodeOCRParser}. This parser base64-encodes image
 * bytes into the XHTML output instead of running OCR text extraction locally,
 * so the size/count limits below govern which images are accepted for
 * encoding, not text recognition.
 * <p>
 * The {@code *Ocr} field and setter names are retained to keep the
 * tika-config/JSON parameter names stable; treat them as "OCR-encode".
 * <p>
 * This class is not thread safe and must be synchronized externally.
 */
public class EncodeOCRConfig implements Serializable {

    private static final long serialVersionUID = -1761942486845717891L;

    // Default maximum size of a single image accepted for base64 encoding.
    // Base64 output is ~4/3 the input size, and the encoded characters are
    // buffered in the XHTML content handler, so keep this conservative by
    // default. Override via config to raise for larger workloads.
    public static final long DEFAULT_MAX_FILE_SIZE_TO_OCR = 100L * 1024 * 1024;

    private long maxFileSizeToOcr = DEFAULT_MAX_FILE_SIZE_TO_OCR;
    private long minFileSizeToOcr = 0;
    private boolean skipOcr = false;
    private int maxImagesToOcr = 50;
    private boolean inlineContent = false;

    public void setInlineContent(boolean inlineContent) {
        this.inlineContent = inlineContent;
    }

    public boolean isInlineContent() {
        return inlineContent;
    }

    /**
     * @see #setMinFileSizeToOcr(long minFileSizeToOcr)
     */
    public long getMinFileSizeToOcr() {
        return minFileSizeToOcr;
    }

    /**
     * Set the minimum image size (in bytes) accepted for base64 encoding.
     * Images smaller than this are skipped. Default is 0 (no lower bound).
     */
    public void setMinFileSizeToOcr(long minFileSizeToOcr) {
        this.minFileSizeToOcr = minFileSizeToOcr;
    }

    /**
     * @see #setMaxFileSizeToOcr(long maxFileSizeToOcr)
     */
    public long getMaxFileSizeToOcr() {
        return maxFileSizeToOcr;
    }

    /**
     * Set the maximum image size (in bytes) accepted for base64 encoding.
     * Images larger than this are skipped. Default is
     * {@value #DEFAULT_MAX_FILE_SIZE_TO_OCR} bytes (100 MB).
     */
    public void setMaxFileSizeToOcr(long maxFileSizeToOcr) {
        this.maxFileSizeToOcr = maxFileSizeToOcr;
    }

    public boolean isSkipOcr() {
        return skipOcr;
    }

    /**
     * If set to <code>true</code>, disables base64 encoding at runtime: the
     * parser reports no supported types and parse() is a no-op. Use this to
     * turn the parser off for a specific file without rewiring tika-config.
     *
     * @param skipOcr
     */
    public void setSkipOcr(boolean skipOcr) {
        this.skipOcr = skipOcr;
    }

    public int getMaxImagesToOcr() {
        return maxImagesToOcr;
    }

    /**
     * Sets the maximum number of images to base64-encode per parse (across
     * the whole document, tracked via ParseContext). Further images beyond
     * this count are skipped. Default is 50.
     *
     * @param maxImagesToOcr maximum number of images to encode; must be &gt;= 0
     */
    public void setMaxImagesToOcr(int maxImagesToOcr) {
        if (maxImagesToOcr < 0) {
            throw new IllegalArgumentException(
                "maxImagesToOcr must be >= 0"
            );
        }
        this.maxImagesToOcr = maxImagesToOcr;
    }
}
