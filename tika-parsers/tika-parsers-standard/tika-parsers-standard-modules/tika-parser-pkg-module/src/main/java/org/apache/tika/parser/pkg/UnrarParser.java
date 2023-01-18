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
package org.apache.tika.parser.pkg;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Collections;
import java.util.Set;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import org.apache.tika.exception.EncryptedDocumentException;
import org.apache.tika.exception.TikaException;
import org.apache.tika.exception.TikaTimeoutException;
import org.apache.tika.extractor.EmbeddedDocumentExtractor;
import org.apache.tika.extractor.EmbeddedDocumentUtil;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.AbstractParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.XHTMLContentHandler;
import org.apache.tika.utils.FileProcessResult;
import org.apache.tika.utils.ProcessUtils;

/**
 * Parser for Rar files.  This relies on 'unrar' being installed
 * and on the path.  This is not the default rar parser and must
 * be selected via the tika-config.xml.
 */
public class UnrarParser extends AbstractParser {
    private static final long serialVersionUID = 6157727985054451501L;

    private static final Set<MediaType> SUPPORTED_TYPES =
            Collections.singleton(MediaType.application("x-rar-compressed"));

    @Override
    public Set<MediaType> getSupportedTypes(ParseContext arg0) {
        return SUPPORTED_TYPES;
    }
    private long timeoutMillis = 60000;

    @Override
    public void parse(InputStream stream, ContentHandler handler, Metadata metadata,
                      ParseContext context) throws IOException, SAXException, TikaException {

        XHTMLContentHandler xhtml = new XHTMLContentHandler(handler, metadata);
        xhtml.startDocument();

        EmbeddedDocumentExtractor extractor =
                EmbeddedDocumentUtil.getEmbeddedDocumentExtractor(context);

        Path cwd = Files.createTempDirectory("tika-unrar-");
        try {
            Path tmp = Files.createTempFile(cwd, "input", ".rar");
            try (OutputStream os = Files.newOutputStream(tmp, StandardOpenOption.WRITE)) {
                IOUtils.copy(stream, os);
            }
            FileProcessResult result = unrar(cwd, tmp);
            //delete the tmp rar file so that we don't recursively parse it in the next step
            try {
                Files.delete(tmp);
            } catch (IOException e) {
                //warn failed to delete tmp
            }
            if (result.isTimeout()) {
                throw new TikaTimeoutException("timed out unrarring");
            } else if (result.getExitValue() != 0) {
                if (result.getStderr().contains("error in the encrypted file")) {
                    throw new EncryptedDocumentException();
                }
                String msg = result.getStderr();
                if (msg.length() > 100) {
                    msg = msg.substring(0, 100);
                }
                throw new TikaException("Unrecoverable problem with rar file, exitValue=" +
                        result.getExitValue() + " : " + msg);
            }
            //TODO: process stdout to extract status for each file:
            //e.g. Extracting  test-documents/testEXCEL.xls                              OK
            processDirectory(cwd, cwd, xhtml, extractor, context);
        } finally {
            FileUtils.deleteDirectory(cwd.toFile());
        }
        xhtml.endDocument();
    }

    private void processDirectory(Path baseDir, Path path,
                               XHTMLContentHandler xhtml,
                               EmbeddedDocumentExtractor extractor, ParseContext context)
            throws IOException, SAXException {
        for (File f : path.toFile().listFiles()) {
            if (f.isDirectory()) {
                processDirectory(baseDir, f.toPath(), xhtml, extractor,
                        context);
            } else {
                processFile(baseDir, f.toPath(), xhtml, extractor, context);
            }
        }
    }

    private void processFile(Path base, Path embeddedFile,
                             XHTMLContentHandler xhtml, EmbeddedDocumentExtractor extractor, ParseContext context)
            throws IOException, SAXException {
        String relPath = base.relativize(embeddedFile).toString();
        Metadata metadata = new Metadata();
        String fName = FilenameUtils.getName(relPath);
        metadata.set(TikaCoreProperties.RESOURCE_NAME_KEY, fName);
        metadata.set(TikaCoreProperties.ORIGINAL_RESOURCE_NAME, relPath);
        if (extractor.shouldParseEmbedded(metadata)) {
            try (InputStream is = TikaInputStream.get(embeddedFile)) {
                extractor.parseEmbedded(is, xhtml, metadata, true);
            }
        }
    }

    private FileProcessResult unrar(Path cwd, Path tmp) throws IOException {
        //we could use the -l option to check for potentially bad file names
        //e.g. path traversals
        ProcessBuilder pb = new ProcessBuilder();
        pb.directory(cwd.toFile());
        pb.command(
                "unrar",
                "x",  //extract with paths...hope that unrar protects against path traversals
                "-kb", // keep broken files
                "-p-", // we don't support passwords yet -- don't hang waiting for password on stdin
                ProcessUtils.escapeCommandLine(tmp.toAbsolutePath().toString())

        );
        return ProcessUtils.execute(pb, timeoutMillis, 10000, 1000);
    }
}
