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

import org.apache.tika.config.TikaConfig;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MimeType;
import org.apache.tika.mime.MimeTypeException;
import org.apache.tika.mime.MimeTypes;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

public class AutoDetectParser extends CompositeParser {

    private MimeTypes types;

    /**
     * Creates an auto-detecting parser instance using the default Tika
     * configuration.
     */
    public AutoDetectParser() {
        try {
            setConfig(TikaConfig.getDefaultConfig());
        } catch (TikaException e) {
            // FIXME: This should never happen
            throw new RuntimeException(e);
        }
    }

    public AutoDetectParser(TikaConfig config) {
        setConfig(config);
    }

    public void setConfig(TikaConfig config) {
        setParsers(config.getParsers());
        setMimeTypes(config.getMimeRepository());
    }

    public MimeTypes getMimeTypes() {
        return types;
    }

    public void setMimeTypes(MimeTypes types) {
        this.types = types;
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

        // Parse the document
        super.parse(stream, handler, metadata);
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
        // Get type based on magic prefix
        stream.mark(types.getMinLength());
        try {
            byte[] prefix = getPrefix(stream, types.getMinLength());
            MimeType type = types.getMimeType(prefix);
            if (type != null) {
                return type;
            }
        } finally {
            stream.reset();
        }

        // Get type based on resourceName hint (if available)
        String resourceName = metadata.get(Metadata.RESOURCE_NAME_KEY);
        if (resourceName != null) {
            MimeType type = types.getMimeType(resourceName);
            if (type != null) {
                return type;
            }
        }

        // Get type based on metadata hint (if available)
        String typename = metadata.get(Metadata.CONTENT_TYPE);
        if (typename != null) {
            try {
                return types.forName(typename);
            } catch (MimeTypeException e) {
                // Malformed type name, ignore
            }
        }

        // Finally, use the default type if no matches found
        try {
            return types.forName(MimeTypes.DEFAULT);
        } catch (MimeTypeException e) {
            // Should never happen
            return null;
        }
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
