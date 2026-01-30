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
package org.apache.tika.pipes.core.extractor;

import static org.apache.tika.sax.XHTMLContentHandler.XHTML;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.AttributesImpl;

import org.apache.tika.exception.CorruptedFileException;
import org.apache.tika.exception.EncryptedDocumentException;
import org.apache.tika.exception.TikaException;
import org.apache.tika.extractor.DefaultEmbeddedStreamTranslator;
import org.apache.tika.extractor.EmbeddedDocumentBytesHandler;
import org.apache.tika.extractor.EmbeddedStreamTranslator;
import org.apache.tika.extractor.ParsingEmbeddedDocumentExtractor;
import org.apache.tika.io.BoundedInputStream;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.BodyContentHandler;
import org.apache.tika.sax.EmbeddedContentHandler;

/**
 * Embedded document extractor that parses and unpacks embedded documents,
 * extracting both text/metadata and raw bytes.
 *
 * @since Apache Tika 3.0.0
 */
public class UnpackExtractor extends ParsingEmbeddedDocumentExtractor {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(ParsingEmbeddedDocumentExtractor.class);

    private static final File ABSTRACT_PATH = new File("");

    private final EmbeddedStreamTranslator embeddedStreamTranslator = new DefaultEmbeddedStreamTranslator();
    private long bytesExtracted = 0;
    private final long maxEmbeddedBytesForExtraction;

    public UnpackExtractor(ParseContext context, long maxEmbeddedBytesForExtraction) {
        super(context);
        this.maxEmbeddedBytesForExtraction = maxEmbeddedBytesForExtraction;
    }


    @Override
    public void parseEmbedded(
            TikaInputStream tis, ContentHandler handler, Metadata metadata, ParseContext parseContext, boolean outputHtml)
            throws SAXException, IOException {
        if (outputHtml) {
            AttributesImpl attributes = new AttributesImpl();
            attributes.addAttribute("", "class", "class", "CDATA", "package-entry");
            handler.startElement(XHTML, "div", "div", attributes);
        }

        String name = metadata.get(TikaCoreProperties.RESOURCE_NAME_KEY);
        if (isWriteFileNameToContent() && name != null && name.length() > 0 && outputHtml) {
            handler.startElement(XHTML, "h1", "h1", new AttributesImpl());
            char[] chars = name.toCharArray();
            handler.characters(chars, 0, chars.length);
            handler.endElement(XHTML, "h1", "h1");
        }

        // Use the delegate parser to parse this entry
        try {
            EmbeddedDocumentBytesHandler bytesHandler = context.get(EmbeddedDocumentBytesHandler.class);
            tis.setCloseShield();
            if (bytesHandler != null) {
                parseWithBytes(tis, handler, metadata);
            } else {
                parse(tis, handler, metadata);
            }
        } catch (EncryptedDocumentException ede) {
            recordException(ede, context);
        } catch (CorruptedFileException e) {
            //necessary to stop the parse to avoid infinite loops
            //on corrupt sqlite3 files
            throw new IOException(e);
        } catch (TikaException e) {
            recordException(e, context);
        } finally {
            tis.removeCloseShield();
        }

        if (outputHtml) {
            handler.endElement(XHTML, "div", "div");
        }
    }

    private void parseWithBytes(TikaInputStream tis, ContentHandler handler, Metadata metadata) throws TikaException, IOException, SAXException {

        //trigger spool to disk
        Path rawBytes = tis.getPath();

        //There may be a "translated" path for OLE2 etc
        Path translated = null;
        try {
            //translate the stream or not
            if (embeddedStreamTranslator.shouldTranslate(tis, metadata)) {
                translated = Files.createTempFile("tika-tmp-", ".bin");
                try (OutputStream os = Files.newOutputStream(translated)) {
                    embeddedStreamTranslator.translate(tis, metadata, os);
                }
            }
            parse(tis, handler, metadata);
        } finally {
            try {
                if (translated != null) {
                    storeEmbeddedBytes(translated, metadata);
                } else {
                    storeEmbeddedBytes(rawBytes, metadata);
                }
            } finally {
                if (translated != null) {
                    Files.delete(translated);
                }
            }
        }
    }

    private void parse(TikaInputStream tis, ContentHandler handler, Metadata metadata)
            throws TikaException, IOException, SAXException {
        getDelegatingParser().parse(tis,
                new EmbeddedContentHandler(new BodyContentHandler(handler)),
                metadata, context);
    }

    private void storeEmbeddedBytes(Path p, Metadata metadata) {
        if (p == null) {
            return;
        }

        // Get UnpackSelector from ParseContext - if configured, use it to filter
        // If no selector configured, accept all embedded documents
        UnpackSelector selector = context.get(UnpackSelector.class);
        if (selector != null && !selector.select(metadata)) {
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("skipping embedded bytes {} <-> {}",
                        metadata.get(Metadata.CONTENT_TYPE),
                        metadata.get(TikaCoreProperties.EMBEDDED_RESOURCE_TYPE));
            }
            return;
        }
        EmbeddedDocumentBytesHandler embeddedDocumentBytesHandler =
                context.get(EmbeddedDocumentBytesHandler.class);
        int id = metadata.getInt(TikaCoreProperties.EMBEDDED_ID);
        try (InputStream is = Files.newInputStream(p)) {
            if (bytesExtracted >= maxEmbeddedBytesForExtraction) {
                throw new IOException("Bytes extracted (" + bytesExtracted +
                        ") >= max allowed (" + maxEmbeddedBytesForExtraction + ")");
            }
            long maxToRead = maxEmbeddedBytesForExtraction - bytesExtracted;

            try (BoundedInputStream boundedIs = new BoundedInputStream(maxToRead, is)) {
                embeddedDocumentBytesHandler.add(id, metadata, boundedIs);
                bytesExtracted += boundedIs.getPos();
                if (boundedIs.hasHitBound()) {
                    throw new IOException("Bytes extracted (" + bytesExtracted +
                            ") >= max allowed (" + maxEmbeddedBytesForExtraction + "). Truncated " +
                            "bytes");
                }
            }
        } catch (IOException e) {
            LOGGER.warn("problem writing out embedded bytes", e);
        }
    }
}
