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

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.Collections;
import java.util.List;

import org.apache.commons.io.input.BoundedInputStream;

import org.apache.tika.config.TikaComponent;
import org.apache.tika.detect.EncodingDetector;
import org.apache.tika.detect.EncodingResult;
import org.apache.tika.detect.MetadataCharsetDetector;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;

/**
 * An encoding detector that respects the HTML5 encoding-sniff algorithm
 * (https://html.spec.whatwg.org/multipage/parsing.html#the-input-byte-stream):
 * BOM → HTTP Content-Type header → {@code <meta charset>} / {@code <meta http-equiv>} tag.
 *
 * <p>When used standalone (outside a {@link org.apache.tika.detect.CompositeEncodingDetector}
 * chain) this detector handles the full spec algorithm including BOM detection.
 *
 * <p>When used inside the default Tika chain (with {@code BOMDetector} and
 * {@code MetadataCharsetDetector} already present), set {@code skipBOM=true} so that
 * this detector focuses exclusively on the HTML {@code <meta>} scan.  That lets
 * {@code CharSoupEncodingDetector} arbitrate between a BOM declaration and a
 * contradicting {@code <meta>} declaration instead of silently suppressing one.
 *
 * <p>HTTP/MIME Content-Type and Content-Encoding metadata are always read here for
 * standalone compatibility; in the chain they will already have been returned by
 * {@code MetadataCharsetDetector} and {@code CharSoup} will handle the duplication
 * gracefully (identical DECLARATIVE results agree, so no harm done).
 */
@TikaComponent(name = "standard-html-encoding-detector")
public final class StandardHtmlEncodingDetector implements EncodingDetector {
    /**
     * Default number of bytes to scan for a {@code <meta charset>} declaration.
     * 65536 is large enough to cover typical {@code <script>} or {@code <style>}
     * blocks in the {@code <head>} without significant overhead (encoding detection
     * already buffers the stream). Users who need to handle even deeper declarations
     * can raise this via {@link #setMarkLimit(int)}.
     */
    private static final int META_TAG_BUFFER_SIZE = 65536;

    private int markLimit = META_TAG_BUFFER_SIZE;

    /**
     * When {@code true}, the BOM check is skipped and the detector goes directly to
     * the Content-Type header and {@code <meta>} scan.  Use this when
     * {@code BOMDetector} is already present in the chain so that
     * {@code CharSoupEncodingDetector} can arbitrate between a BOM declaration and a
     * contradicting {@code <meta charset>} rather than having the BOM silently win.
     *
     * <p>Default: {@code false} (HTML5 spec-compliant standalone behaviour).</p>
     */
    private boolean skipBOM = false;

    @Override
    public List<EncodingResult> detect(TikaInputStream tis, Metadata metadata,
                                       ParseContext context) throws IOException {
        int limit = getMarkLimit();
        tis.mark(limit);
        InputStream limitedStream = BoundedInputStream.builder()
                .setInputStream(tis).setMaxCount(limit).get();
        PreScanner preScanner = new PreScanner(limitedStream);

        Charset detectedCharset = null;

        if (!skipBOM) {
            // HTML5 spec: BOM overrides everything.  When used standalone this
            // detector is responsible for BOM detection; when used in the chain with
            // BOMDetector, setting skipBOM=true lets CharSoup arbitrate.
            detectedCharset = preScanner.detectBOM();
        }
        if (detectedCharset == null) {
            detectedCharset = MetadataCharsetDetector.charsetFromContentType(metadata);
        }
        if (detectedCharset == null) {
            detectedCharset = MetadataCharsetDetector.charsetFromContentEncoding(metadata);
        }
        if (detectedCharset == null) {
            detectedCharset = preScanner.scan();
        }

        tis.reset();
        if (detectedCharset == null) {
            return Collections.emptyList();
        }
        return List.of(new EncodingResult(detectedCharset, 1.0f,
                detectedCharset.name(), EncodingResult.ResultType.DECLARATIVE));
    }

    public int getMarkLimit() {
        return markLimit;
    }

    /**
     * How far into the stream to scan for a {@code <meta charset>} declaration.
     * Default is {@value #META_TAG_BUFFER_SIZE} bytes.
     */
    public void setMarkLimit(int markLimit) {
        this.markLimit = markLimit;
    }

    public boolean isSkipBOM() {
        return skipBOM;
    }

    /**
     * When {@code true}, skip the BOM check and rely on {@code BOMDetector} in the
     * chain.  This allows {@code CharSoupEncodingDetector} to arbitrate between a
     * BOM and a contradicting {@code <meta charset>} declaration.
     * Default is {@code false}.
     */
    public void setSkipBOM(boolean skipBOM) {
        this.skipBOM = skipBOM;
    }
}
