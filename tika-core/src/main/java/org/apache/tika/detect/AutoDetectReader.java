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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.xml.sax.InputSource;

import org.apache.tika.config.ServiceLoader;
import org.apache.tika.exception.TikaException;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.parser.ParseContext;

/**
 * An input stream reader that automatically detects the character encoding
 * to be used for converting bytes to characters.
 *
 * @since Apache Tika 1.2
 */
public class AutoDetectReader extends BufferedReader {

    private static final ServiceLoader DEFAULT_LOADER =
            new ServiceLoader(AutoDetectReader.class.getClassLoader());

    private static final EncodingDetector DEFAULT_DETECTOR;

    static {
        // Use DefaultEncodingDetector so SPI-discovered detectors are run in the
        // pinned order (HtmlEncodingDetector, UniversalEncodingDetector, Icu4jEncodingDetector,
        // then anything else by class name). Otherwise the order would be whatever
        // ServiceLoader yields from classpath/jar order, which is fragile.
        DEFAULT_DETECTOR = new DefaultEncodingDetector(DEFAULT_LOADER);
    }

    private final Charset charset;

    private AutoDetectReader(InputStream stream, Charset charset) throws IOException {
        super(new InputStreamReader(stream, charset));
        this.charset = charset;

        // TIKA-240: Drop the BOM if present
        mark(1);
        if (read() != '\ufeff') { // zero-width no-break space
            reset();
        }
    }

    public AutoDetectReader(InputStream stream, Metadata metadata,
                            EncodingDetector encodingDetector) throws IOException, TikaException {
        // IMPORTANT: Only call getTikaInputStream once, then reuse the same instance.
        // Calling it twice creates two different TikaInputStreams sharing the same underlying
        // stream, causing the second one's reads to advance the position for both.
        this(getTikaInputStream(stream), metadata, encodingDetector);
    }

    private AutoDetectReader(TikaInputStream tis, Metadata metadata,
                             EncodingDetector encodingDetector) throws IOException, TikaException {
        this(tis, detect(tis, metadata, encodingDetector));
    }

    public AutoDetectReader(InputStream stream, Metadata metadata, ServiceLoader loader)
            throws IOException, TikaException {
        this(getTikaInputStream(stream), metadata, new DefaultEncodingDetector(loader));
    }

    public AutoDetectReader(InputStream stream, Metadata metadata)
            throws IOException, TikaException {
        this(stream, metadata, DEFAULT_DETECTOR);
    }

    public AutoDetectReader(InputStream stream) throws IOException, TikaException {
        this(stream, new Metadata());
    }

    private static Charset detect(TikaInputStream tis, Metadata metadata,
                                  EncodingDetector detector)
            throws IOException, TikaException {
        // Ask all given detectors for the character encoding
        List<EncodingResult> results = detector.detect(tis, metadata, new ParseContext());
        if (!results.isEmpty()) {
            Charset detected = results.get(0).getCharset();
            Charset superset = CharsetSupersets.supersetOf(detected);
            if (superset != null) {
                metadata.set(TikaCoreProperties.DECODED_CHARSET, superset.name());
                return superset;
            }
            return detected;
        }

        // Try determining the encoding based on hints in document metadata.
        // Two metadata keys are honoured (TIKA-4683 — restoring 3.x parser-layer
        // behaviour that consulted both): the charset parameter of CONTENT_TYPE
        // (e.g. "text/html; charset=UTF-8") and a bare charset label in
        // CONTENT_ENCODING (set by parsers such as RFC822Parser).
        Charset metaCharset = MetadataCharsetDetector.charsetFromContentType(metadata);
        if (metaCharset == null) {
            metaCharset = MetadataCharsetDetector.charsetFromContentEncoding(metadata);
        }
        if (metaCharset != null) {
            metadata.set(TikaCoreProperties.DETECTED_ENCODING, metaCharset.name());
            metadata.set(TikaCoreProperties.ENCODING_DETECTOR,
                    "AutoDetectReader-charset-metadata-fallback");
            return metaCharset;
        }

        // Final fallback (TIKA-4683): when the rolled-back 3.x-style chain
        // (Html, Universal, Icu4j) abstains on short/pure-ASCII inputs and
        // metadata carries no charset hint, default to ISO-8859-1 rather
        // than throwing.  This matches 3.x's default-charset behaviour:
        // pre-TIKA-4685 the chain effectively returned ISO-8859-1 for
        // ASCII-only content, and tests assert that.  4.x's TIKA-4685
        // refactor moved to windows-1252 via WHATWG normalisation; we
        // explicitly opt out of that here.
        Charset fallback = StandardCharsets.ISO_8859_1;
        metadata.set(TikaCoreProperties.DETECTED_ENCODING, fallback.name());
        metadata.set(TikaCoreProperties.ENCODING_DETECTOR,
                "AutoDetectReader-default-fallback");
        return fallback;
    }

    private static TikaInputStream getTikaInputStream(InputStream stream) {
        if (stream instanceof TikaInputStream) {
            return (TikaInputStream) stream;
        }
        return TikaInputStream.get(stream);
    }


    public Charset getCharset() {
        return charset;
    }

    public InputSource asInputSource() {
        InputSource source = new InputSource(this);
        source.setEncoding(charset.name());
        return source;
    }

}
