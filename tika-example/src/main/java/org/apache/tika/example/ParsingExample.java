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
package org.apache.tika.example;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.util.List;

import org.apache.tika.Tika;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.serialization.JsonMetadataList;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.parser.RecursiveParserWrapper;
import org.apache.tika.sax.BasicContentHandlerFactory;
import org.apache.tika.sax.BodyContentHandler;
import org.apache.tika.sax.ContentHandlerFactory;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

public class ParsingExample {

    /**
     * Example of how to use Tika's parseToString method to parse the content of a file,
     * and return any text found.
     *
     * Note: Tika.parseToString() will extract content from the outer container
     * document and any embedded/attached documents.
     *
     * @return The content of a file.
     */
    public String parseToStringExample() throws IOException, SAXException, TikaException {
        InputStream stream = ParsingExample.class.getResourceAsStream("test.doc");
        Tika tika = new Tika();
        try {
            return tika.parseToString(stream);
        } finally {
            stream.close();
        }
    }

    /**
     * Example of how to use Tika to parse a file when you do not know its file type
     * ahead of time.
     *
     * AutoDetectParser attempts to discover the file's type automatically, then call
     * the exact Parser built for that file type.
     *
     * The stream to be parsed by the Parser. In this case, we get a file from the
     * resources folder of this project.
     *
     * Handlers are used to get the exact information you want out of the host of
     * information gathered by Parsers. The body content handler, intuitively, extracts
     * everything that would go between HTML body tags.
     *
     * The Metadata object will be filled by the Parser with Metadata discovered about
     * the file being parsed.
     *
     * Note: This example will extract content from the outer document and all
     * embedded documents.  However, if you choose to use a {@link ParseContext},
     * make sure to set a {@link Parser} or else embedded content will not be
     * parsed.
     *
     * @return The content of a file.
     */
    public String parseExample() throws IOException, SAXException, TikaException {
        InputStream stream = ParsingExample.class.getResourceAsStream("test.doc");
        AutoDetectParser parser = new AutoDetectParser();
        BodyContentHandler handler = new BodyContentHandler();
        Metadata metadata = new Metadata();
        try {
            parser.parse(stream, handler, metadata);
            return handler.toString();
        } finally {
            stream.close();
        }
    }

    /**
     * If you don't want content from embedded documents, send in
     * a {@link org.apache.tika.parser.ParseContext} that does not contain a
     * {@link Parser}.
     *
     * @return The content of a file.
     */
    public String parseNoEmbeddedExample() throws IOException, SAXException, TikaException {
        InputStream stream = ParsingExample.class.getResourceAsStream("test_recursive_embedded.docx");
        AutoDetectParser parser = new AutoDetectParser();
        BodyContentHandler handler = new BodyContentHandler();
        Metadata metadata = new Metadata();
        try {
            parser.parse(stream, handler, metadata, new ParseContext());
            return handler.toString();
        } finally {
            stream.close();
        }
    }


    /**
     * This example shows how to extract content from the outer document and all
     * embedded documents.  The key is to specify a {@link Parser} in the {@link ParseContext}.
     *
     * @return content, including from embedded documents
     * @throws IOException
     * @throws SAXException
     * @throws TikaException
     */
    public String parseEmbeddedExample() throws IOException, SAXException, TikaException {
        InputStream stream = ParsingExample.class.getResourceAsStream("test_recursive_embedded.docx");
        AutoDetectParser parser = new AutoDetectParser();
        BodyContentHandler handler = new BodyContentHandler();
        Metadata metadata = new Metadata();
        ParseContext context = new ParseContext();
        context.set(Parser.class, parser);
        try {
            parser.parse(stream, handler, metadata, context);
            return handler.toString();
        } finally {
            stream.close();
        }

    }

    /**
     * For documents that may contain embedded documents, it might be helpful
     * to create list of metadata objects, one for the container document and
     * one for each embedded document.  This allows easy access to both the
     * extracted content and the metadata of each embedded document.
     * Note that many document formats can contain embedded documents,
     * including traditional container formats -- zip, tar and others -- but also
     * common office document formats including: MSWord, MSExcel,
     * MSPowerPoint, RTF, PDF, MSG and several others.
     * <p>
     * The "content" format is determined by the ContentHandlerFactory, and
     * the content is stored in {@link org.apache.tika.parser.RecursiveParserWrapper#TIKA_CONTENT}
     * <p>
     * The drawback to the RecursiveParserWrapper is that it caches metadata and contents
     * in memory.  This should not be used on files whose contents are too big to be handled
     * in memory.
     *
     * @return a list of metadata object, one each for the container file and each embedded file
     * @throws IOException
     * @throws SAXException
     * @throws TikaException
     */
    public List<Metadata> recursiveParserWrapperExample() throws IOException,
            SAXException, TikaException {

        Parser p = new AutoDetectParser();
        ContentHandlerFactory factory = new BasicContentHandlerFactory(
                BasicContentHandlerFactory.HANDLER_TYPE.HTML, -1);

        RecursiveParserWrapper wrapper = new RecursiveParserWrapper(p, factory);
        InputStream stream = ParsingExample.class.getResourceAsStream("test_recursive_embedded.docx");
        Metadata metadata = new Metadata();
        metadata.set(Metadata.RESOURCE_NAME_KEY, "test_recursive_embedded.docx");
        ParseContext context = new ParseContext();

        try {
            wrapper.parse(stream, new DefaultHandler(), metadata, context);
        } finally {
            stream.close();
        }
        return wrapper.getMetadata();
    }

    /**
     * We include a simple JSON serializer for a list of metadata with
     * {@link org.apache.tika.metadata.serialization.JsonMetadataList}.
     * That class also includes a deserializer to convert from JSON
     * back to a List<Metadata>.
     * <p>
     * This functionality is also available in tika-app's GUI, and
     * with the -J option on tika-app's commandline.  For tika-server
     * users, there is the "rmeta" service that will return this format.
     *
     * @return a JSON representation of a list of Metadata objects
     * @throws IOException
     * @throws SAXException
     * @throws TikaException
     */
    public String serializedRecursiveParserWrapperExample() throws IOException,
            SAXException, TikaException {
        List metadataList = recursiveParserWrapperExample();
        StringWriter writer = new StringWriter();
        JsonMetadataList.toJson(metadataList, writer);
        return writer.toString();
    }
}
