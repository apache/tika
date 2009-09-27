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

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

import org.apache.tika.config.TikaConfig;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.Parser;
import org.apache.tika.parser.ParsingReader;
import org.apache.tika.sax.BodyContentHandler;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

/**
 * Facade class for accessing Tika functionality. This class hides much of
 * the underlying complexity of the lower level Tika classes and provides
 * simple methods for many common parsing operations.
 *
 * @since Apache Tika 0.5
 */
public class Tika {

    /**
     * The parser instance used by this facade.
     */
    private final Parser parser;

    /**
     * Creates a Tika facade using the given configuration.
     * @param config
     */
    public Tika(TikaConfig config) {
        this.parser = new AutoDetectParser(config);
    }

    /**
     * Creates a Tika facade using the default configuration.
     */
    public Tika() {
        this(TikaConfig.getDefaultConfig());
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
        Map<String, Object> context = new HashMap<String, Object>();
        context.put(Parser.class.getName(), parser);
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
     * @throws FileNotFoundException if the given file does not exist
     * @throws IOException if the file can not be read or parsed
     */
    public Reader parse(File file) throws FileNotFoundException, IOException {
        return parse(new FileInputStream(file), getFileMetadata(file));
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
        return parse(url.openStream(), getUrlMetadata(url));
    }

    /**
     * Parses the given document and returns the extracted text content.
     * The given input stream is closed by this method.
     *
     * @param stream the document to be parsed
     * @param metadata document metadata
     * @return extracted text content
     * @throws IOException if the document can not be read
     * @throws TikaException if the document can not be parsed
     */
    public String parseToString(InputStream stream, Metadata metadata)
            throws IOException, TikaException {
        try {
            ContentHandler handler = new BodyContentHandler();
            Map<String, Object> context = new HashMap<String, Object>();
            context.put(Parser.class.getName(), parser);
            parser.parse(stream, handler, metadata, context);
            return handler.toString();
        } catch (SAXException e) {
            // This should never happen with BodyContentHandler...
            throw new TikaException("Unexpected SAX processing failure", e);
        } finally {
            stream.close();
        }
    }

    /**
     * Parses the given document and returns the extracted text content.
     * The given input stream is closed by this method.
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
     *
     * @param file the file to be parsed
     * @return extracted text content
     * @throws FileNotFoundException if the file does not exist
     * @throws IOException if the file can not be read
     * @throws TikaException if the file can not be parsed
     */
    public String parseToString(File file)
            throws FileNotFoundException, IOException, TikaException {
        return parseToString(new FileInputStream(file), getFileMetadata(file));
    }

    /**
     * Parses the resource at the given URL and returns the extracted
     * text content.
     *
     * @param url the URL of the resource to be parsed
     * @return extracted text content
     * @throws IOException if the resource can not be read
     * @throws TikaException if the resource can not be parsed
     */
    public String parseToString(URL url) throws IOException, TikaException {
        return parseToString(url.openStream(), getUrlMetadata(url));
    }

    private static Metadata getFileMetadata(File file) {
        Metadata metadata = new Metadata();
        metadata.set(Metadata.RESOURCE_NAME_KEY, file.getName());
        return metadata;
    }

    private static Metadata getUrlMetadata(URL url) {
        Metadata metadata = new Metadata();
        String path = url.getPath();
        int slash = path.lastIndexOf('/');
        if (slash + 1 < path.length()) {
            metadata.set(Metadata.RESOURCE_NAME_KEY, path.substring(slash + 1));
        }
        return metadata;
    }

}
