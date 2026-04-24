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
 * Full WHATWG prescan charset detector for HTML: HTTP Content-Type header →
 * {@code <meta charset>} / {@code <meta http-equiv>} tag, per
 * https://html.spec.whatwg.org/multipage/parsing.html#the-input-byte-stream.
 *
 * <p>BOM detection is <em>not</em> performed here; {@code BOMDetector} handles
 * that as a separate, earlier step in the detector chain.
 *
 * <p>Opt-in: register explicitly in a {@code <encodingDetectors>} config to use
 * this detector in place of the lenient {@link org.apache.tika.parser.html.HtmlEncodingDetector}
 * default.
 */
@TikaComponent(name = "standard-html-encoding-detector", spi = false)
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

    @Override
    public List<EncodingResult> detect(TikaInputStream tis, Metadata metadata,
                                       ParseContext context) throws IOException {
        int limit = getMarkLimit();
        tis.mark(limit);
        InputStream limitedStream = BoundedInputStream.builder()
                .setInputStream(tis).setMaxCount(limit).get();
        PreScanner preScanner = new PreScanner(limitedStream);

        Charset detectedCharset = MetadataCharsetDetector.charsetFromContentType(metadata);
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
}
