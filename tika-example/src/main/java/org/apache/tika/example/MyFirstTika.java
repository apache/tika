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

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.File;
import java.io.InputStream;

import org.apache.commons.io.FileUtils;
import org.apache.tika.config.TikaConfig;
import org.apache.tika.detect.Detector;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.langdetect.OptimaizeLangDetector;
import org.apache.tika.language.detect.LanguageDetector;
import org.apache.tika.language.detect.LanguageResult;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;
import org.apache.tika.mime.MimeTypes;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.sax.BodyContentHandler;
import org.xml.sax.ContentHandler;

/**
 * Demonstrates how to call the different components within Tika: its
 * {@link Detector} framework (aka MIME identification and repository), its
 * {@link Parser} interface, its {@link LanguageIdentifier} and other goodies.
 * <p>
 * It also shows the "easy way" via {@link AutoDetectParser}
 */
public class MyFirstTika {
    public static void main(String[] args) throws Exception {
        String filename = args[0];
        TikaConfig tikaConfig = TikaConfig.getDefaultConfig();

        Metadata metadata = new Metadata();
        String text = parseUsingComponents(filename, tikaConfig, metadata);
        System.out.println("Parsed Metadata: ");
        System.out.println(metadata);
        System.out.println("Parsed Text: ");
        System.out.println(text);

        System.out.println("-------------------------");

        metadata = new Metadata();
        text = parseUsingAutoDetect(filename, tikaConfig, metadata);
        System.out.println("Parsed Metadata: ");
        System.out.println(metadata);
        System.out.println("Parsed Text: ");
        System.out.println(text);
    }

    public static String parseUsingAutoDetect(String filename, TikaConfig tikaConfig,
                                              Metadata metadata) throws Exception {
        System.out.println("Handling using AutoDetectParser: [" + filename + "]");

        AutoDetectParser parser = new AutoDetectParser(tikaConfig);
        ContentHandler handler = new BodyContentHandler();
        TikaInputStream stream = TikaInputStream.get(new File(filename), metadata);
        parser.parse(stream, handler, metadata, new ParseContext());
        return handler.toString();
    }

    public static String parseUsingComponents(String filename, TikaConfig tikaConfig,
                                              Metadata metadata) throws Exception {
        MimeTypes mimeRegistry = tikaConfig.getMimeRepository();

        System.out.println("Examining: [" + filename + "]");

        metadata.set(Metadata.RESOURCE_NAME_KEY, filename);
        System.out.println("The MIME type (based on filename) is: ["
                + mimeRegistry.detect(null, metadata) + "]");

        InputStream stream = TikaInputStream.get(new File(filename));
        System.out.println("The MIME type (based on MAGIC) is: ["
                + mimeRegistry.detect(stream, metadata) + "]");

        stream = TikaInputStream.get(new File(filename));
        Detector detector = tikaConfig.getDetector();
        System.out.println("The MIME type (based on the Detector interface) is: ["
                + detector.detect(stream, metadata) + "]");

        LanguageDetector langDetector = new OptimaizeLangDetector().loadModels();
        LanguageResult lang = langDetector.detect(FileUtils.readFileToString(new File(filename), UTF_8));

        System.out.println("The language of this content is: ["
                + lang.getLanguage() + "]");

        // Get a non-detecting parser that handles all the types it can
        Parser parser = tikaConfig.getParser();
        // Tell it what we think the content is
        MediaType type = detector.detect(stream, metadata);
        metadata.set(Metadata.CONTENT_TYPE, type.toString());
        // Have the file parsed to get the content and metadata
        ContentHandler handler = new BodyContentHandler();
        parser.parse(stream, handler, metadata, new ParseContext());

        return handler.toString();
    }
}
