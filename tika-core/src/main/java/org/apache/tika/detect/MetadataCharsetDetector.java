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
package org.apache.tika.detect;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;

import org.apache.tika.config.TikaComponent;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.ParseContext;

/**
 * Encoding detector that extracts a declared charset from Tika metadata without
 * reading any bytes from the stream.  Returns a single
 * {@link EncodingResult.ResultType#DECLARATIVE} result when a charset is found.
 *
 * <p>Two metadata keys are consulted in order:
 * <ol>
 *   <li>{@link Metadata#CONTENT_TYPE} — the {@code charset} parameter of the
 *       HTTP/MIME Content-Type header (e.g. {@code text/html; charset=UTF-8}).</li>
 *   <li>{@link Metadata#CONTENT_ENCODING} — a bare charset label set by parsers
 *       such as {@code RFC822Parser}, which splits Content-Type into a bare
 *       media-type key and a separate charset key.</li>
 * </ol>
 *
 * <p>This detector is SPI-loaded in {@code tika-core} and therefore always present
 * in the default encoding-detector chain.  Its DECLARATIVE result is visible to
 * {@code CharSoupEncodingDetector}, which can weigh it against structural or
 * statistical evidence from other detectors.</p>
 *
 * @since Apache Tika 4.0
 */
@TikaComponent(name = "metadata-charset-detector")
public class MetadataCharsetDetector implements EncodingDetector {

    @Override
    public List<EncodingResult> detect(TikaInputStream tis, Metadata metadata,
                                       ParseContext context) throws IOException {
        Charset cs = charsetFromContentType(metadata);
        if (cs == null) {
            cs = charsetFromContentEncoding(metadata);
        }
        if (cs == null) {
            return Collections.emptyList();
        }
        return List.of(new EncodingResult(cs, 1.0f, cs.name(),
                EncodingResult.ResultType.DECLARATIVE));
    }

    /**
     * Returns the charset named in the {@code charset} parameter of the
     * {@link Metadata#CONTENT_TYPE} value, or {@code null} if absent or unparseable.
     */
    public static Charset charsetFromContentType(Metadata metadata) {
        String contentType = metadata.get(Metadata.CONTENT_TYPE);
        if (contentType == null) {
            return null;
        }
        MediaType mediaType = MediaType.parse(contentType);
        if (mediaType == null) {
            return null;
        }
        String label = mediaType.getParameters().get("charset");
        return parseCharset(label);
    }

    /**
     * Returns the charset named in {@link Metadata#CONTENT_ENCODING}, or
     * {@code null} if absent or unparseable.  This key is used by
     * {@code RFC822Parser} to expose the charset declared in MIME body-part
     * headers when the bare media type is stored separately in
     * {@link Metadata#CONTENT_TYPE}.
     */
    public static Charset charsetFromContentEncoding(Metadata metadata) {
        return parseCharset(metadata.get(Metadata.CONTENT_ENCODING));
    }

    private static Charset parseCharset(String label) {
        if (label == null || label.isBlank()) {
            return null;
        }
        Charset cs;
        try {
            cs = Charset.forName(label.trim());
        } catch (IllegalArgumentException e) {
            return null;
        }
        return normalizeWhatwg(cs);
    }

    /**
     * Applies the critical WHATWG encoding-label normalizations that are universally
     * applicable regardless of content type.  The WHATWG encoding spec
     * (https://encoding.spec.whatwg.org/) maps {@code ISO-8859-1}, {@code US-ASCII},
     * and their aliases to {@code windows-1252} because real-world content labeled
     * with these names is almost always actually windows-1252.
     */
    private static Charset normalizeWhatwg(Charset cs) {
        if (cs == null) {
            return null;
        }
        String name = cs.name();
        if (StandardCharsets.ISO_8859_1.name().equals(name)
                || StandardCharsets.US_ASCII.name().equals(name)) {
            try {
                return Charset.forName("windows-1252");
            } catch (IllegalArgumentException e) {
                return cs;
            }
        }
        return cs;
    }
}
