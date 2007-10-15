/**
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
package org.apache.tika.parser;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

import org.apache.tika.config.ParserConfig;
import org.apache.tika.config.TikaConfig;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MimeType;
import org.apache.tika.mime.MimeTypeException;
import org.apache.tika.mime.MimeTypes;
import org.jdom.JDOMException;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

public class AutoDetectParser implements Parser {

    private TikaConfig config;

    /**
     * Creates an auto-detecting parser instance using the default Tika
     * configuration.
     */
    public AutoDetectParser() {
        try {
            config = TikaConfig.getDefaultConfig();
        } catch (IOException e) {
            // FIXME: This should never happen
            throw new RuntimeException(e);
        } catch (JDOMException e) {
            // FIXME: This should never happen
            throw new RuntimeException(e);
        }
    }

    public AutoDetectParser(TikaConfig config) {
        this.config = config;
    }

    public TikaConfig getConfig() {
        return config;
    }

    public void setConfig(TikaConfig config) {
        this.config = config;
    }

    public void parse(
            InputStream stream, ContentHandler handler, Metadata metadata)
            throws IOException, SAXException, TikaException {
        // We need buffering to enable MIME magic detection before parsing
        if (!stream.markSupported()) {
            stream = new BufferedInputStream(stream);
        }

        // Automatically detect the MIME type of the document 
        MimeType type = getMimeType(stream, metadata);
        metadata.set(Metadata.CONTENT_TYPE, type.getName());

        // Get the parser configuration for the detected MIME type
        ParserConfig pc = config.getParserConfig(type.getName());
        if (pc == null) {
            pc = config.getParserConfig(MimeTypes.DEFAULT);
        }
        if (pc == null) {
            throw new TikaException("No parsers available for this document");
        }

        // Instantiate the configured parser and use it to parse the document
        Parser parser = ParserFactory.getParser(pc);
        parser.parse(stream, handler, metadata);
    }

    /**
     * Automatically detects the MIME type of a document based on magic
     * markers in the stream prefix and any given metadata hints.
     * <p>
     * The given stream is expected to support marks, so that this method
     * can reset the stream to the position it was in before this method
     * was called.
     *
     * @param stream document stream
     * @param metadata metadata hints
     * @return MIME type of the document
     * @throws IOException if the document stream could not be read
     */
    private MimeType getMimeType(InputStream stream, Metadata metadata)
            throws IOException {
        MimeTypes types = config.getMimeRepository();
        MimeType type = null;

        // Get type based on metadata hint (if available)
        String typename = metadata.get(Metadata.CONTENT_TYPE);
        if (typename != null) {
            try {
                typename = MimeType.clean(typename);
                type = types.forName(typename);
            } catch (MimeTypeException e) {
                // Malformed type name, ignore
            }
        }

        // Get (or verify) type based on filename hint (if available)
        String filename = metadata.get("filename");
        if (filename != null) {
            MimeType match = types.getMimeType(filename);
            if (match != null && (type == null || !type.matches(filename))) {
                type = match;
            }
        }

        // Get (or verify) type based on magic prefix
        stream.mark(types.getMinLength());
        try {
            byte[] prefix = getPrefix(stream, types.getMinLength());
            MimeType match = types.getMimeType(prefix);
            if (match != null && (type == null || !type.matches(prefix))) {
                type = match;
            }
        } finally {
            stream.reset();
        }

        // Finally, use the default type if no matches found
        if (type == null) {
            type = types.forName(MimeTypes.DEFAULT);
        }

        return type;
    }

    /**
     * Reads and returns the first <code>length</code> bytes from the
     * given stream. If the stream ends before that, returns all bytes
     * from the stream.
     * 
     * @param input input stream
     * @param length number of bytes to read and return
     * @return stream prefix
     * @throws IOException if the stream could not be read
     */
    private byte[] getPrefix(InputStream input, int length) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[Math.min(1024, length)];
        int n = input.read(buffer);
        while (n != -1) {
            output.write(buffer, 0, n);
            int remaining = length - output.size();
            if (remaining > 0) {
                n = input.read(buffer, 0, Math.min(buffer.length, remaining));
            } else {
                n = -1;
            }
        }
        return output.toByteArray();
    }

}
