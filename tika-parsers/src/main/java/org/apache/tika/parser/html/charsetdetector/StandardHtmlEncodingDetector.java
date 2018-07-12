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

import org.apache.commons.io.input.BoundedInputStream;
import org.apache.tika.detect.CompositeEncodingDetector;
import org.apache.tika.detect.EncodingDetector;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;

import static java.util.Arrays.asList;
import static org.apache.tika.parser.html.charsetdetector.CharsetAliases.getCharsetByLabel;

/**
 * An encoding detector that tries to respect the spirit of the HTML spec
 * part 12.2.3 "The input byte stream", or at least the part that is compatible with
 * the implementation of tika.
 * <p>
 * https://html.spec.whatwg.org/multipage/parsing.html#the-input-byte-stream
 * <p>
 * If a resource was fetched over HTTP, then HTTP headers should be added to tika metadata
 * when using {@link #detect}, especially {@link Metadata.CONTENT_TYPE}, as it may contain charset information.
 * <p>
 * This encoding detector may return null if no encoding is detected.
 * It is meant to be used inside a {@link org.apache.tika.detect.CompositeDetector}.
 * For instance:
 * <pre> {@code
 *     EncodingDetector detector = new CompositeDetector(
 *         new StandardHtmlEncodingDetector(),
 *         new Icu4jEncodingDetector()
 *     );
 * }</pre>
 * <p>
 * This detector is stateless, and the same instance can be used on several different input streams.
 */
public final class StandardHtmlEncodingDetector implements EncodingDetector {

    /**
     * A composite encoding detector chaining a {@link StandardHtmlEncodingDetector}
     * (that may return null) and a {@link StandardIcu4JEncodingDetector} (that always return a value)
     * This full thus always returns an encoding, and still works very well with data coming
     * from the web.
     */
    public static final EncodingDetector FULL_DETECTOR = new CompositeEncodingDetector(asList(
            new StandardHtmlEncodingDetector(),
            new StandardIcu4JEncodingDetector()
    ));
    private static final int META_TAG_BUFFER_SIZE = 8192;

    /**
     * Extracts a charset from a Content-Type HTTP header.
     *
     * @param metadata parser metadata
     * @return a charset if there is one specified, or null
     */
    private static Charset charsetFromContentType(Metadata metadata) {
        String contentType = metadata.get(Metadata.CONTENT_TYPE);
        MediaType mediatype = MediaType.parse(contentType);
        if (mediatype == null) return null;
        String charsetLabel = mediatype.getParameters().get("charset");
        return getCharsetByLabel(charsetLabel);
    }

    @Override
    public Charset detect(InputStream input, Metadata metadata) throws IOException {
        input.mark(META_TAG_BUFFER_SIZE);
        // Never read more than the first META_TAG_BUFFER_SIZE bytes
        InputStream limitedStream = new BoundedInputStream(input, META_TAG_BUFFER_SIZE);
        PreScanner preScanner = new PreScanner(limitedStream);

        // The order of priority for detection is:
        // 1. Byte Order Mark
        Charset detectedCharset = preScanner.detectBOM();
        // 2. Transport-level information (Content-Type HTTP header)
        if (detectedCharset == null) detectedCharset = charsetFromContentType(metadata);
        // 3. HTML <meta> tag
        if (detectedCharset == null) detectedCharset = preScanner.scan();

        input.reset();
        return detectedCharset;
    }
}
