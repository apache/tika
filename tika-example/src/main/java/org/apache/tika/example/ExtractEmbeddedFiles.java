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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import org.apache.commons.io.FilenameUtils;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import org.apache.tika.config.TikaConfig;
import org.apache.tika.detect.Detector;
import org.apache.tika.exception.TikaException;
import org.apache.tika.extractor.EmbeddedDocumentExtractor;
import org.apache.tika.extractor.ParsingEmbeddedDocumentExtractor;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.mime.MediaType;
import org.apache.tika.mime.MimeTypeException;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.sax.BodyContentHandler;

public class ExtractEmbeddedFiles {
    private Parser parser = new AutoDetectParser();
    private Detector detector = ((AutoDetectParser) parser).getDetector();
    private TikaConfig config = TikaConfig.getDefaultConfig();

    public void extract(InputStream is, Path outputDir) throws SAXException, TikaException, IOException {
        Metadata m = new Metadata();
        ParseContext c = new ParseContext();
        ContentHandler h = new BodyContentHandler(-1);

        c.set(Parser.class, parser);
        EmbeddedDocumentExtractor ex = new MyEmbeddedDocumentExtractor(outputDir, c);
        c.set(EmbeddedDocumentExtractor.class, ex);

        parser.parse(is, h, m, c);
    }

    private class MyEmbeddedDocumentExtractor extends ParsingEmbeddedDocumentExtractor {
        private final Path outputDir;
        private int fileCount = 0;

        private MyEmbeddedDocumentExtractor(Path outputDir, ParseContext context) {
            super(context);
            this.outputDir = outputDir;
        }

        @Override
        public boolean shouldParseEmbedded(Metadata metadata) {
            return true;
        }

        @Override
        public void parseEmbedded(InputStream stream, ContentHandler handler, Metadata metadata, boolean outputHtml) throws SAXException, IOException {

            //try to get the name of the embedded file from the metadata
            String name = metadata.get(TikaCoreProperties.RESOURCE_NAME_KEY);

            if (name == null) {
                name = "file_" + fileCount++;
            } else {
                //make sure to select only the file name (not any directory paths
                //that might be included in the name) and make sure
                //to normalize the name
                name = name.replaceAll("\u0000", " ");
                int prefix = FilenameUtils.getPrefixLength(name);
                if (prefix > -1) {
                    name = name.substring(prefix);
                }
                name = FilenameUtils.normalize(FilenameUtils.getName(name));
            }

            //now try to figure out the right extension for the embedded file
            MediaType contentType = detector.detect(stream, metadata);

            if (name.indexOf('.') == -1 && contentType != null) {
                try {
                    name += config
                            .getMimeRepository()
                            .forName(contentType.toString())
                            .getExtension();
                } catch (MimeTypeException e) {
                    e.printStackTrace();
                }
            }

            Path outputFile = outputDir.resolve(name);
            if (Files.exists(outputFile)) {
                outputFile = outputDir.resolve(UUID
                        .randomUUID()
                        .toString() + "-" + name);
            }
            Files.createDirectories(outputFile.getParent());
            Files.copy(stream, outputFile);
        }
    }
}
