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
package org.apache.tika.utils;

//JDK imports
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;

import org.apache.tika.config.TikaConfig;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaMimeKeys;
import org.apache.tika.parser.Parser;
import org.apache.tika.sax.BodyContentHandler;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

/**
 * Contains utility methods for parsing documents. Intended to provide simple
 * entry points into the Tika framework.
 */
public class ParseUtils implements TikaMimeKeys {

    /**
     * Returns a parser that can handle the specified MIME type, and is set to
     * receive input from a stream opened from the specified URL. NB: Close the
     * input stream when it is no longer needed!
     * 
     * @param config
     * @param mimeType
     *            the document's MIME type
     * @return a parser appropriate to this MIME type
     * @throws TikaException
     */
    public static Parser getParser(String mimeType, TikaConfig config)
            throws TikaException {
        return config.getParser(mimeType);
    }

    /**
     * Returns a parser that can handle the specified MIME type, and is set to
     * receive input from a stream opened from the specified URL. The MIME type
     * is determined automatically. NB: Close the input stream when it is no
     * longer needed!
     * 
     * @param documentUrl
     *            URL pointing to the document to parse
     * @param config
     * @return a parser appropriate to this MIME type and ready to read input
     *         from the specified document
     * @throws TikaException
     */
    public static Parser getParser(URL documentUrl, TikaConfig config)
            throws TikaException {
        String mimetype = config.getMimeRepository().getMimeType(documentUrl)
        .getName();
        return getParser(mimetype, config);
    }

    /**
     * Returns a parser that can handle the specified MIME type, and is set to
     * receive input from a stream opened from the specified URL. NB: Close the
     * input stream when it is no longer needed!
     * 
     * @param documentFile
     *            File object pointing to the document to parse
     * @param config
     * @return a parser appropriate to this MIME type and ready to read input
     *         from the specified document
     * @throws TikaException
     */
    public static Parser getParser(File documentFile, TikaConfig config)
            throws TikaException {
        String mimetype = config.getMimeRepository().getMimeType(documentFile)
        .getName();
        return getParser(mimetype, config);
    }

    /**
     * Gets the string content of a document read from an input stream.
     * 
     * @param stream the stream from which to read document data
     * @param config
     * @param mimeType MIME type of the data
     * @return the string content parsed from the document
     */
    public static String getStringContent(
            InputStream stream, TikaConfig config, String mimeType)
            throws TikaException, IOException {
        try {
            Parser parser = config.getParser(mimeType);
            ContentHandler handler = new BodyContentHandler();
            parser.parse(stream, handler, new Metadata());
            return handler.toString();
        } catch (SAXException e) {
            throw new TikaException("Unexpected SAX error", e);
        }
    }

    /**
     * Gets the string content of a document read from an input stream.
     * 
     * @param documentUrl
     *            URL pointing to the document to parse
     * @param config
     * @return the string content parsed from the document
     */
    public static String getStringContent(URL documentUrl, TikaConfig config)
            throws TikaException, IOException {
        String mime = config.getMimeRepository().getMimeType(documentUrl)
        .getName();
        return getStringContent(documentUrl, config, mime);
    }

    /**
     * Gets the string content of a document read from an input stream.
     * 
     * @param documentUrl
     *            URL pointing to the document to parse
     * @param config
     * @param mimeType
     *            MIME type of the data
     * @return the string content parsed from the document
     */
    public static String getStringContent(
            URL documentUrl, TikaConfig config, String mimeType)
            throws TikaException, IOException {
        InputStream stream = documentUrl.openStream();
        try {
            return getStringContent(stream, config, mimeType);
        } finally {
            stream.close();
        }
    }

    /**
     * Gets the string content of a document read from an input stream.
     * 
     * @param documentFile
     *            File object pointing to the document to parse
     * @param config
     * @param mimeType
     *            MIME type of the data
     * @return the string content parsed from the document
     */
    public static String getStringContent(
            File documentFile, TikaConfig config, String mimeType)
            throws TikaException, IOException {
        InputStream stream = new BufferedInputStream(new FileInputStream(
                documentFile));
        try {
            return getStringContent(stream, config, mimeType);
        } finally {
            stream.close();
        }
    }

    /**
     * Gets the string content of a document read from an input stream.
     * 
     * @param documentFile
     *            File object pointing to the document to parse
     * @param config
     * @return the string content parsed from the document
     */
    public static String getStringContent(File documentFile, TikaConfig config)
            throws TikaException, IOException {
        String mime =
            config.getMimeRepository().getMimeType(documentFile).getName();
        return getStringContent(documentFile, config, mime);
    }

}
