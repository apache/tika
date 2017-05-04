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

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.util.Collections;
import java.util.List;

import org.apache.tika.config.LoadErrorHandler;
import org.apache.tika.config.ServiceLoader;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;
import org.apache.tika.utils.CharsetUtils;
import org.xml.sax.InputSource;

/**
 * An input stream reader that automatically detects the character encoding
 * to be used for converting bytes to characters.
 *
 * @since Apache Tika 1.2
 */
public class AutoDetectReader extends BufferedReader {

    private static final ServiceLoader DEFAULT_LOADER =
            new ServiceLoader(AutoDetectReader.class.getClassLoader());

    private static Charset detect(
            InputStream input, Metadata metadata,
            List<EncodingDetector> detectors, LoadErrorHandler handler)
            throws IOException, TikaException {
        // Ask all given detectors for the character encoding
        for (EncodingDetector detector : detectors) {
            try {
                Charset charset = detector.detect(input, metadata);
                if (charset != null) {
                    return charset;
                }
            } catch (NoClassDefFoundError e) {
                // TIKA-1041: Detector dependencies not present.
                handler.handleLoadError(detector.getClass().getName(), e);
            }
        }

        // Try determining the encoding based on hints in document metadata
        MediaType type = MediaType.parse(metadata.get(Metadata.CONTENT_TYPE));
        if (type != null) {
            String charset = type.getParameters().get("charset");
            if (charset != null) {
                try {
                    return CharsetUtils.forName(charset);
                } catch (Exception e) {
                    // ignore
                }
            }
        }

        throw new TikaException(
                "Failed to detect the character encoding of a document");
    }

    private final Charset charset;

    private AutoDetectReader(InputStream stream, Charset charset)
            throws IOException {
        super(new InputStreamReader(stream, charset));
        this.charset = charset;

        // TIKA-240: Drop the BOM if present
        mark(1);
        if (read() != '\ufeff') { // zero-width no-break space
            reset();
        }
    }

    /**
     *
     * @param stream stream from which to read -- make sure that it supports mark!
     * @param metadata
     * @param detectors
     * @param handler
     * @throws IOException
     * @throws TikaException
     */
    private AutoDetectReader(
            InputStream stream, Metadata metadata,
            List<EncodingDetector> detectors, LoadErrorHandler handler)
            throws IOException, TikaException {
        this(stream, detect(stream, metadata, detectors, handler));
    }

    public AutoDetectReader(
            InputStream stream, Metadata metadata,
            EncodingDetector encodingDetector) throws IOException, TikaException {
        this(getBuffered(stream), metadata, Collections.singletonList(encodingDetector),
                DEFAULT_LOADER.getLoadErrorHandler());
    }

    public AutoDetectReader(
            InputStream stream, Metadata metadata,
            ServiceLoader loader) throws IOException, TikaException {
        this(getBuffered(stream), metadata,
                loader.loadServiceProviders(EncodingDetector.class),
                loader.getLoadErrorHandler());
    }

    public AutoDetectReader(InputStream stream, Metadata metadata)
            throws IOException, TikaException {
        this(stream, metadata, DEFAULT_LOADER);
    }

    public AutoDetectReader(InputStream stream)
            throws IOException, TikaException {
        this(stream, new Metadata());
    }

    private static InputStream getBuffered(InputStream stream) {
        if (stream.markSupported()) {
            return stream;
        }
        return new BufferedInputStream(stream);
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
