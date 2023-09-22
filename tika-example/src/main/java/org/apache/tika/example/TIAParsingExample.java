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

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Reader;
import java.net.URL;
import java.nio.CharBuffer;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.zip.GZIPInputStream;

import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import org.apache.tika.Tika;
import org.apache.tika.exception.TikaException;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.CompositeParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.parser.ParserDecorator;
import org.apache.tika.parser.html.HtmlMapper;
import org.apache.tika.parser.html.IdentityHtmlMapper;
import org.apache.tika.parser.html.JSoupParser;
import org.apache.tika.parser.txt.TXTParser;
import org.apache.tika.parser.xml.XMLParser;
import org.apache.tika.sax.BodyContentHandler;
import org.apache.tika.sax.LinkContentHandler;
import org.apache.tika.sax.TeeContentHandler;

public class TIAParsingExample {
    public static String parseToStringExample() throws Exception {
        File document = new File("example.doc");
        String content = new Tika().parseToString(document);
        System.out.print(content);
        return content;
    }

    public static void parseToReaderExample() throws Exception {
        File document = new File("example.doc");
        try (Reader reader = new Tika().parse(document)) {
            char[] buffer = new char[1000];
            int n = reader.read(buffer);
            while (n != -1) {
                System.out.append(CharBuffer.wrap(buffer, 0, n));
                n = reader.read(buffer);
            }
        }
    }

    public static void parseFileInputStream(String filename) throws Exception {
        Parser parser = new AutoDetectParser();
        ContentHandler handler = new DefaultHandler();
        Metadata metadata = new Metadata();
        ParseContext context = new ParseContext();
        try (InputStream stream = new FileInputStream(new File(filename))) {
            parser.parse(stream, handler, metadata, context);
        }
    }

    public static void parseURLStream(String address) throws Exception {
        Parser parser = new AutoDetectParser();
        ContentHandler handler = new DefaultHandler();
        Metadata metadata = new Metadata();
        ParseContext context = new ParseContext();
        try (InputStream stream = new GZIPInputStream(new URL(address).openStream())) {
            parser.parse(stream, handler, metadata, context);
        }
    }

    public static void parseTikaInputStream(String filename) throws Exception {
        Parser parser = new AutoDetectParser();
        ContentHandler handler = new DefaultHandler();
        Metadata metadata = new Metadata();
        ParseContext context = new ParseContext();
        try (InputStream stream = TikaInputStream.get(new File(filename))) {
            parser.parse(stream, handler, metadata, context);
        }
    }

    public static File tikaInputStreamGetFile(String filename) throws Exception {
        try (InputStream stream = TikaInputStream.get(new File(filename))) {
            TikaInputStream tikaInputStream = TikaInputStream.get(stream);
            return tikaInputStream.getFile();
        }
    }

    public static void useHtmlParser() throws Exception {
        InputStream stream = new ByteArrayInputStream(new byte[0]);
        ContentHandler handler = new DefaultHandler();
        Metadata metadata = new Metadata();
        ParseContext context = new ParseContext();
        Parser parser = new JSoupParser();
        parser.parse(stream, handler, metadata, context);
    }

    public static void useCompositeParser() throws Exception {
        InputStream stream = new ByteArrayInputStream(new byte[0]);
        ContentHandler handler = new DefaultHandler();
        ParseContext context = new ParseContext();
        Map<MediaType, Parser> parsersByType = new HashMap<>();
        parsersByType.put(MediaType.parse("text/html"), new JSoupParser());
        parsersByType.put(MediaType.parse("application/xml"), new XMLParser());

        CompositeParser parser = new CompositeParser();
        parser.setParsers(parsersByType);
        parser.setFallback(new TXTParser());

        Metadata metadata = new Metadata();
        metadata.set(Metadata.CONTENT_TYPE, "text/html");
        parser.parse(stream, handler, metadata, context);
    }

    public static void useAutoDetectParser() throws Exception {
        InputStream stream = new ByteArrayInputStream(new byte[0]);
        ContentHandler handler = new DefaultHandler();
        Metadata metadata = new Metadata();
        ParseContext context = new ParseContext();
        Parser parser = new AutoDetectParser();
        parser.parse(stream, handler, metadata, context);
    }

    public static void testTeeContentHandler(String filename) throws Exception {
        InputStream stream = new ByteArrayInputStream(new byte[0]);
        Metadata metadata = new Metadata();
        ParseContext context = new ParseContext();
        Parser parser = new AutoDetectParser();
        LinkContentHandler linkCollector = new LinkContentHandler();
        try (OutputStream output = new FileOutputStream(new File(filename))) {
            ContentHandler handler =
                    new TeeContentHandler(new BodyContentHandler(output), linkCollector);
            parser.parse(stream, handler, metadata, context);
        }
    }

    public static void testLocale() throws Exception {
        InputStream stream = new ByteArrayInputStream(new byte[0]);
        ContentHandler handler = new DefaultHandler();
        Metadata metadata = new Metadata();
        Parser parser = new AutoDetectParser();
        ParseContext context = new ParseContext();
        context.set(Locale.class, Locale.ENGLISH);
        parser.parse(stream, handler, metadata, context);
    }

    public static void testHtmlMapper() throws Exception {
        InputStream stream = new ByteArrayInputStream(new byte[0]);
        ContentHandler handler = new DefaultHandler();
        Metadata metadata = new Metadata();
        Parser parser = new AutoDetectParser();
        ParseContext context = new ParseContext();
        context.set(HtmlMapper.class, new IdentityHtmlMapper());
        parser.parse(stream, handler, metadata, context);
    }

    public static void testCompositeDocument() throws Exception {
        InputStream stream = new ByteArrayInputStream(new byte[0]);
        ContentHandler handler = new DefaultHandler();
        Metadata metadata = new Metadata();
        Parser parser = new AutoDetectParser();
        ParseContext context = new ParseContext();
        context.set(Parser.class, new ParserDecorator(parser) {
            private static final long serialVersionUID = 4424210691523343833L;

            @Override
            public void parse(InputStream stream, ContentHandler handler, Metadata metadata,
                              ParseContext context)
                    throws IOException, SAXException, TikaException {
                // custom processing of the component document
            }
        });
        parser.parse(stream, handler, metadata, context);
    }
}
