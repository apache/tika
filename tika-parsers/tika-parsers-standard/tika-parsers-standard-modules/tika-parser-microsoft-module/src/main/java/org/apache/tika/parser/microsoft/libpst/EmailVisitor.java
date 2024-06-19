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
package org.apache.tika.parser.microsoft.libpst;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileVisitResult;
import java.nio.file.FileVisitor;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;

import org.apache.commons.io.IOExceptionWithCause;
import org.xml.sax.SAXException;

import org.apache.tika.extractor.EmbeddedDocumentExtractor;
import org.apache.tika.extractor.EmbeddedDocumentUtil;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.PST;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.XHTMLContentHandler;

public class EmailVisitor implements FileVisitor<Path> {

    private final Path root;
    private final boolean processEmailAsMsg;
    private final XHTMLContentHandler xhtml;
    private final Metadata parentMetadata;
    private final EmbeddedDocumentExtractor embeddedDocumentExtractor;

    public EmailVisitor(Path root, boolean processEmailAsMsg, XHTMLContentHandler xhtml, Metadata parentMetadata, ParseContext parseContext) {
        this.root = root;
        this.processEmailAsMsg = processEmailAsMsg;
        this.xhtml = xhtml;
        this.parentMetadata = parentMetadata;
        this.embeddedDocumentExtractor = EmbeddedDocumentUtil.getEmbeddedDocumentExtractor(parseContext);
    }

    @Override
    public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
        return FileVisitResult.CONTINUE;
    }

    @Override
    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
        if (processEmailAsMsg) {
            if (file
                    .getFileName()
                    .toString()
                    .endsWith(".msg")) {
                process(file);
            }
        } else if (file
                .getFileName()
                .toString()
                .endsWith(".eml")) {
            process(file);
        }
        return FileVisitResult.CONTINUE;
    }

    private void process(Path file) throws IOException {
        Metadata emailMetadata = new Metadata();
        String pstPath = root
                .relativize(file.getParent())
                .toString();
        emailMetadata.set(PST.PST_FOLDER_PATH, pstPath);
        try (InputStream is = TikaInputStream.get(file)) {
            try {
                embeddedDocumentExtractor.parseEmbedded(is, xhtml, emailMetadata, true);
            } catch (SAXException e) {
                throw new IOExceptionWithCause(e);
            }
        }
    }

    @Override
    public FileVisitResult visitFileFailed(Path file, IOException exc) throws IOException {
        return FileVisitResult.CONTINUE;
    }

    @Override
    public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
        return FileVisitResult.CONTINUE;
    }
}
