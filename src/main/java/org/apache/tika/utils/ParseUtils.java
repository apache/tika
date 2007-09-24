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

// JDK imports
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;

// TIKA imports
import org.apache.tika.config.TikaConfig;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.TikaMimeKeys;
import org.apache.tika.parser.Parser;
import org.apache.tika.parser.ParserFactory;

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
     * @param inputStream
     *            stream containing document data to parse
     * @param config
     * @param mimeType
     *            the document's MIME type
     * @return a parser appropriate to this MIME type and ready to read input
     *         from the specified document
     * @throws TikaException
     * @throws IOException
     */
    public static Parser getParser(InputStream inputStream, TikaConfig config,
            String mimeType) throws TikaException, IOException {

        if (inputStream == null) {
            throw new TikaException("Document input stream not provided.");
        }

        return ParserFactory.getParser(inputStream, mimeType, config);
    }

    // Note that we cannot provide a method that takes an InputStream
    // but not a MIME type, since we will not have a resource
    // name from which to derive it.

    /**
     * Returns a parser that can handle the specified MIME type, and is set to
     * receive input from a stream opened from the specified URL. NB: Close the
     * input stream when it is no longer needed!
     * 
     * @param documentUrl
     *            URL pointing to the document to parse
     * @param config
     * @param mimeType
     *            the document's MIME type
     * @return a parser appropriate to this MIME type and ready to read input
     *         from the specified document
     * @throws TikaException
     * @throws IOException
     */
    public static Parser getParser(URL documentUrl, TikaConfig config,
            String mimeType) throws TikaException, IOException {

        if (documentUrl == null) {
            throw new TikaException("Document URL not provided.");
        }

        return ParserFactory.getParser(documentUrl.openStream(), mimeType,
                config);
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
     * @throws IOException
     */
    public static Parser getParser(URL documentUrl, TikaConfig config)
            throws TikaException, IOException {

        String mimetype = config.getMimeRepository().getMimeType(documentUrl)
                .getName();
        return getParser(documentUrl, config, mimetype);
    }

    /**
     * Returns a parser that can handle the specified MIME type, and is set to
     * receive input from a stream opened from the specified URL. NB: Close the
     * input stream when it is no longer needed!
     * 
     * @param documentFile
     *            File object pointing to the document to parse
     * @param config
     * @param mimeType
     *            the document's MIME type
     * @return a parser appropriate to this MIME type and ready to read input
     *         from the specified document
     * @throws TikaException
     * @throws IOException
     */
    public static Parser getParser(File documentFile, TikaConfig config,
            String mimeType) throws TikaException, IOException {

        if (documentFile == null) {
            throw new TikaException("Document file not provided.");
        }

        if (!documentFile.canRead()) {
            throw new TikaException(
                    "Document file does not exist or is not readable.");
        }

        FileInputStream inputStream = new FileInputStream(documentFile);
        // TODO: Do we want to wrap a BufferedInputStream, or does the
        // file's buffering suffice?

        return ParserFactory.getParser(inputStream, mimeType, config);
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
     * @throws IOException
     */
    public static Parser getParser(File documentFile, TikaConfig config)
            throws TikaException, IOException {

        String mimetype = config.getMimeRepository().getMimeType(documentFile)
                .getName();
        return getParser(documentFile, config, mimetype);
    }

    /**
     * Gets the string content of a document read from an input stream.
     * 
     * @param inputStream
     *            the stream from which to read document data
     * @param config
     * @param mimeType
     *            MIME type of the data
     * @return the string content parsed from the document
     * @throws TikaException
     * @throws IOException
     */
    public static String getStringContent(InputStream inputStream,
            TikaConfig config, String mimeType) throws TikaException,
            IOException {

        Parser parser = getParser(inputStream, config, mimeType);
        return getStringContent(parser);
    }

    /**
     * Gets the string content of a document read from an input stream.
     * 
     * @param documentUrl
     *            URL pointing to the document to parse
     * @param config
     * @return the string content parsed from the document
     * @throws TikaException
     * @throws IOException
     */
    public static String getStringContent(URL documentUrl, TikaConfig config)
            throws TikaException, IOException {

        Parser parser = getParser(documentUrl, config);
        return getStringContent(parser);
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
     * @throws TikaException
     * @throws IOException
     */
    public static String getStringContent(URL documentUrl, TikaConfig config,
            String mimeType) throws TikaException, IOException {

        Parser parser = getParser(documentUrl, config, mimeType);
        return getStringContent(parser);
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
     * @throws TikaException
     * @throws IOException
     */
    public static String getStringContent(File documentFile, TikaConfig config,
            String mimeType) throws TikaException, IOException {

        Parser parser = getParser(documentFile, config, mimeType);
        return getStringContent(parser);
    }

    /**
     * Gets the string content of a document read from an input stream.
     * 
     * @param documentFile
     *            File object pointing to the document to parse
     * @param config
     * @return the string content parsed from the document
     * @throws TikaException
     * @throws IOException
     */
    public static String getStringContent(File documentFile, TikaConfig config)
            throws TikaException, IOException {

        Parser parser = getParser(documentFile, config);
        return getStringContent(parser);
    }

    private static String getStringContent(Parser parser) throws IOException {
        String content = parser.getStrContent();
        parser.getInputStream().close();
        return content;
    }
}
