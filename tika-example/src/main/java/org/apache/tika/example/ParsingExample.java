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

import org.apache.tika.Tika;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.sax.BodyContentHandler;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.io.InputStream;

public class ParsingExample {

    /**
     * Example of how to use Tika's parseToString method to parse the content of a file,
     * and return any text found.
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
     * Example of how to use Tika to parse an file when you do not know its file type
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
}
