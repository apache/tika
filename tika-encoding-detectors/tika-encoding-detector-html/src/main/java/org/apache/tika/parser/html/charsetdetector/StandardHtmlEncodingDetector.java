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

import static org.apache.tika.parser.html.charsetdetector.CharsetAliases.getCharsetByLabel;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.Collections;
import java.util.List;

import org.apache.commons.io.input.BoundedInputStream;

import org.apache.tika.config.TikaComponent;
import org.apache.tika.detect.EncodingDetector;
import org.apache.tika.detect.EncodingResult;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.ParseContext;

/**
 * An encoding detector that tries to respect the spirit of the HTML spec
 * part 12.2.3 "The input byte stream", or at least the part that is compatible with
 * the implementation of tika.
 * <p>
 * https://html.spec.whatwg.org/multipage/parsing.html#the-input-byte-stream
 * <p>
 * If a resource was fetched over HTTP, then HTTP headers should be added to tika metadata
 * when using {@link #detect}, especially {@link Metadata#CONTENT_TYPE}, as it may contain
 * charset information.
 * <p>
 * This encoding detector may return null if no encoding is detected.
 * It is meant to be used inside a {@link org.apache.tika.detect.CompositeEncodingDetector}.
 * For instance:
 * <pre> {@code
 *     EncodingDetector detector = new CompositeEncodingDetector(
 *       Arrays.asList(
 *         new StandardHtmlEncodingDetector(),
 *         new Icu4jEncodingDetector()));
 * }</pre>
 * <p>
 */
@TikaComponent(name = "standard-html-encoding-detector")
public final class StandardHtmlEncodingDetector implements EncodingDetector {
    private static final int META_TAG_BUFFER_SIZE = 8192;

    private int markLimit = META_TAG_BUFFER_SIZE;

    /**
     * Extracts a charset from a Content-Type HTTP header.
     *
     * @param metadata parser metadata
     * @return a charset if there is one specified, or null
     */
    private static Charset charsetFromContentType(Metadata metadata) {
        String contentType = metadata.get(Metadata.CONTENT_TYPE);
        MediaType mediatype = MediaType.parse(contentType);
        if (mediatype == null) {
            return null;
        }
        String charsetLabel = mediatype.getParameters().get("charset");
        return getCharsetByLabel(charsetLabel);
    }

    @Override
    public List<EncodingResult> detect(TikaInputStream tis, Metadata metadata,
                                       ParseContext context) throws IOException {
        int limit = getMarkLimit();
        tis.mark(limit);
        InputStream limitedStream = BoundedInputStream.builder()
                .setInputStream(tis).setMaxCount(limit).get();
        PreScanner preScanner = new PreScanner(limitedStream);

        // Priority: 1. BOM  2. Content-Type HTTP header  3. HTML <meta> tag
        Charset detectedCharset = preScanner.detectBOM();
        if (detectedCharset == null) {
            detectedCharset = charsetFromContentType(metadata);
        }
        if (detectedCharset == null) {
            detectedCharset = preScanner.scan();
        }

        tis.reset();
        if (detectedCharset == null) {
            return Collections.emptyList();
        }
        return List.of(new EncodingResult(detectedCharset, EncodingResult.CONFIDENCE_DEFINITIVE));
    }

    public int getMarkLimit() {
        return markLimit;
    }

    /**
     * How far into the stream to read for charset detection.
     * Default is 8192.
     */
    public void setMarkLimit(int markLimit) {
        this.markLimit = markLimit;
    }
}
