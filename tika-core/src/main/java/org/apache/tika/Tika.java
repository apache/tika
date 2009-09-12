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

import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.Parser;
import org.apache.tika.parser.ParsingReader;

/**
 * Facade class for accessing Tika functionality. This class hides much of
 * the underlying complexity of the lower level Tika classes and provides
 * simple methods for many common parsing operations.
 *
 * @since Apache Tika 0.5
 */
public class Tika {

    private final Parser parser = new AutoDetectParser();

    /**
     * Parses the given document and returns the extracted text content.
     *
     * @param stream the document to be parsed
     * @result extracted text content
     * @throws IOException if the document can not be read or parsed
     */
    public Reader parse(InputStream stream) throws IOException {
        return new ParsingReader(parser, stream, new Metadata());
    }

    /**
     * Parses the given file and returns the extracted text content.
     *
     * @param file the file to be parsed
     * @result extracted text content
     * @throws FileNotFoundException if the given file does not exist
     * @throws IOException if the file can not be read or parsed
     */
    public Reader parse(File file) throws FileNotFoundException, IOException {
        Metadata metadata = new Metadata();
        metadata.set(Metadata.RESOURCE_NAME_KEY, file.getName());
        return new ParsingReader(parser, new FileInputStream(file), metadata);
    }

    /**
     * Parses the resource at the given URL and returns the extracted
     * text content.
     *
     * @param url the URL of the resource to be parsed
     * @result extracted text content
     * @throws IOException if the resource can not be read or parsed
     */
    public Reader parse(URL url) throws IOException {
        Metadata metadata = new Metadata();
        String path = url.getPath();
        int slash = path.lastIndexOf('/');
        if (slash + 1 < path.length()) {
            metadata.set(Metadata.RESOURCE_NAME_KEY, path.substring(slash + 1));
        }
        return new ParsingReader(parser, url.openStream(), metadata);
    }

}
