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
package org.apache.tika;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.net.URL;

import org.apache.tika.config.TikaConfig;
import org.apache.tika.detect.Detector;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.MetadataHelper;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.parser.ParsingReader;
import org.apache.tika.sax.BodyContentHandler;
import org.apache.tika.sax.WriteOutContentHandler;
import org.xml.sax.SAXException;

/**
 * Facade class for accessing Tika functionality. This class hides much of
 * the underlying complexity of the lower level Tika classes and provides
 * simple methods for many common parsing and type detection operations.
 *
 * @since Apache Tika 0.5
 * @see Parser
 * @see Detector
 */
public class Tika {

    /**
     * The detector instance used by this facade.
     */
    private final Detector detector;

    /**
     * The parser instance used by this facade.
     */
    private final Parser parser;

    /**
     * Maximum length of the strings returned by the parseToString methods.
     * Used to prevent out of memory problems with huge input documents.
     * The default setting is 100k characters.
     */
    private int maxStringLength = 100 * 1000;

    /**
     * Creates a Tika facade using the given configuration.
     *
     * @param config Tika configuration
     */
    public Tika(TikaConfig config) {
        this.detector = config.getMimeRepository();
        this.parser = new AutoDetectParser(config);
    }

    /**
     * Creates a Tika facade using the default configuration.
     */
    public Tika() {
        this(TikaConfig.getDefaultConfig());
    }

    /**
     * Detects the media type of the given document. The type detection is
     * based on the content of the given document stream and any given
     * document metadata. The document stream can be <code>null</code>,
     * in which case only the given document metadata is used for type
     * detection.
     * <p>
     * If the document stream supports the
     * {@link InputStream#markSupported() mark feature}, then the stream is
     * marked and reset to the original position before this method returns.
     * Only a limited number of bytes are read from the stream.
     * <p>
     * The given document stream is <em>not</em> closed by this method.
     * <p>
     * Unlike in the {@link #parse(InputStream, Metadata)} method, the
     * given document metadata is <em>not</em> modified by this method.
     *
     * @param stream the document stream, or <code>null</code>
     * @param metadata document metadata
     * @return detected media type
     * @throws IOException if the stream can not be read
     */
    public String detect(InputStream stream, Metadata metadata)
            throws IOException {
        if (stream == null || stream.markSupported()) {
            return detector.detect(stream, metadata).toString();
        } else {
            return detector.detect(
                    new BufferedInputStream(stream), metadata).toString();
        }
    }

    /**
     * Detects the media type of the given document. The type detection is
     * based on the content of the given document stream.
     * <p>
     * If the document stream supports the
     * {@link InputStream#markSupported() mark feature}, then the stream is
     * marked and reset to the original position before this method returns.
     * Only a limited number of bytes are read from the stream.
     * <p>
     * The given document stream is <em>not</em> closed by this method.
     *
     * @param stream the document stream
     * @return detected media type
     * @throws IOException if the stream can not be read
     */
    public String detect(InputStream stream) throws IOException {
        return detect(stream, new Metadata());
    }

    /**
     * Detects the media type of the given file. The type detection is
     * based on the document content and a potential known file extension.
     * <p>
     * Use the {@link #detect(String)} method when you want to detect the
     * type of the document without actually accessing the file.
     *
     * @param file the file
     * @return detected media type
     * @throws IOException if the file can not be read
     */
    public String detect(File file) throws IOException {
        return detect(file.toURI().toURL());
    }

    /**
     * Detects the media type of the resource at the given URL. The type
     * detection is based on the document content and a potential known
     * file extension included in the URL.
     * <p>
     * Use the {@link #detect(String)} method when you want to detect the
     * type of the document without actually accessing the URL.
     *
     * @param url the URL of the resource
     * @return detected media type
     * @throws IOException if the resource can not be read
     */
    public String detect(URL url) throws IOException {
        Metadata metadata = new Metadata();
        InputStream stream = MetadataHelper.getInputStream(url, metadata);
        try {
            return detect(stream, metadata);
        } finally {
            stream.close();
        }
    }

    /**
     * Detects the media type of a document with the given file name.
     * The type detection is based on known file name extensions.
     * <p>
     * The given name can also be a URL or a full file path. In such cases
     * only the file name part of the string is used for type detection. 
     *
     * @param name the file name of the document
     * @return detected media type
     */
    public String detect(String name) {
        Metadata metadata = new Metadata();
        metadata.set(Metadata.RESOURCE_NAME_KEY, name);
        try {
            return detect(null, metadata);
        } catch (IOException e) {
            throw new IllegalStateException("Unexpected IOException", e);
        }
    }

    /**
     * Parses the given document and returns the extracted text content.
     * Input metadata like a file name or a content type hint can be passed
     * in the given metadata instance. Metadata information extracted from
     * the document is returned in that same metadata instance.
     *
     * @param stream the document to be parsed
     * @return extracted text content
     * @throws IOException if the document can not be read or parsed
     */
    public Reader parse(InputStream stream, Metadata metadata)
            throws IOException {
        ParseContext context = new ParseContext();
        context.set(Parser.class, parser);
        return new ParsingReader(parser, stream, metadata, context);
    }

    /**
     * Parses the given document and returns the extracted text content.
     *
     * @param stream the document to be parsed
     * @return extracted text content
     * @throws IOException if the document can not be read or parsed
     */
    public Reader parse(InputStream stream) throws IOException {
        return parse(stream, new Metadata());
    }

    /**
     * Parses the given file and returns the extracted text content.
     *
     * @param file the file to be parsed
     * @return extracted text content
     * @throws IOException if the file can not be read or parsed
     */
    public Reader parse(File file) throws IOException {
        return parse(file.toURI().toURL());
    }

    /**
     * Parses the resource at the given URL and returns the extracted
     * text content.
     *
     * @param url the URL of the resource to be parsed
     * @return extracted text content
     * @throws IOException if the resource can not be read or parsed
     */
    public Reader parse(URL url) throws IOException {
        Metadata metadata = new Metadata();
        InputStream stream = MetadataHelper.getInputStream(url, metadata);
        return parse(stream, metadata);
    }

    /**
     * Parses the given document and returns the extracted text content.
     * The given input stream is closed by this method.
     * <p>
     * To avoid unpredictable excess memory use, the returned string contains
     * only up to {@link #getMaxStringLength()} first characters extracted
     * from the input document. Use the {@link #setMaxStringLength(int)}
     * method to adjust this limitation.
     *
     * @param stream the document to be parsed
     * @param metadata document metadata
     * @return extracted text content
     * @throws IOException if the document can not be read
     * @throws TikaException if the document can not be parsed
     */
    public String parseToString(InputStream stream, Metadata metadata)
            throws IOException, TikaException {
        WriteOutContentHandler handler =
            new WriteOutContentHandler(maxStringLength);
        try {
            ParseContext context = new ParseContext();
            context.set(Parser.class, parser);
            parser.parse(
                    stream, new BodyContentHandler(handler), metadata, context);
        } catch (SAXException e) {
            if (!handler.isWriteLimitReached(e)) {
                // This should never happen with BodyContentHandler...
                throw new TikaException("Unexpected SAX processing failure", e);
            }
        } finally {
            stream.close();
        }
        return handler.toString();
    }

    /**
     * Parses the given document and returns the extracted text content.
     * The given input stream is closed by this method.
     * <p>
     * To avoid unpredictable excess memory use, the returned string contains
     * only up to {@link #getMaxStringLength()} first characters extracted
     * from the input document. Use the {@link #setMaxStringLength(int)}
     * method to adjust this limitation.
     *
     * @param stream the document to be parsed
     * @return extracted text content
     * @throws IOException if the document can not be read
     * @throws TikaException if the document can not be parsed
     */
    public String parseToString(InputStream stream)
            throws IOException, TikaException {
        return parseToString(stream, new Metadata());
    }

    /**
     * Parses the given file and returns the extracted text content.
     * <p>
     * To avoid unpredictable excess memory use, the returned string contains
     * only up to {@link #getMaxStringLength()} first characters extracted
     * from the input document. Use the {@link #setMaxStringLength(int)}
     * method to adjust this limitation.
     *
     * @param file the file to be parsed
     * @return extracted text content
     * @throws IOException if the file can not be read
     * @throws TikaException if the file can not be parsed
     */
    public String parseToString(File file) throws IOException, TikaException {
        return parseToString(file.toURI().toURL());
    }

    /**
     * Parses the resource at the given URL and returns the extracted
     * text content.
     * <p>
     * To avoid unpredictable excess memory use, the returned string contains
     * only up to {@link #getMaxStringLength()} first characters extracted
     * from the input document. Use the {@link #setMaxStringLength(int)}
     * method to adjust this limitation.
     *
     * @param url the URL of the resource to be parsed
     * @return extracted text content
     * @throws IOException if the resource can not be read
     * @throws TikaException if the resource can not be parsed
     */
    public String parseToString(URL url) throws IOException, TikaException {
        Metadata metadata = new Metadata();
        InputStream stream = MetadataHelper.getInputStream(url, metadata);
        return parseToString(stream, metadata);
    }

    /**
     * Returns the maximum length of strings returned by the
     * parseToString methods.
     *
     * @since Apache Tika 0.7
     * @return maximum string length, or -1 if the limit has been disabled
     */
    public int getMaxStringLength() {
        return maxStringLength;
    }

    /**
     * Sets the maximum length of strings returned by the parseToString
     * methods.
     *
     * @since Apache Tika 0.7
     * @param maxStringLength maximum string length,
     *                        or -1 to disable this limit
     */
    public void setMaxStringLength(int maxStringLength) {
        this.maxStringLength = maxStringLength;
    }

}
