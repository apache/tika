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
package org.apache.tika.extractor;

import static org.apache.tika.sax.XHTMLContentHandler.XHTML;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import org.apache.commons.io.input.CloseShieldInputStream;
import org.apache.tika.exception.CorruptedFileException;
import org.apache.tika.exception.EncryptedDocumentException;
import org.apache.tika.exception.TikaException;
import org.apache.tika.io.BoundedInputStream;
import org.apache.tika.io.TemporaryResources;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.BodyContentHandler;
import org.apache.tika.sax.EmbeddedContentHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.AttributesImpl;

/**
 * Recursive Unpacker and text and metadata extractor.
 *
 * @since Apache Tika 3.0.0
 */
public class RUnpackExtractor extends ParsingEmbeddedDocumentExtractor {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(ParsingEmbeddedDocumentExtractor.class);

    private static final File ABSTRACT_PATH = new File("");

    private EmbeddedBytesSelector embeddedBytesSelector = EmbeddedBytesSelector.ACCEPT_ALL;

    private long bytesExtracted = 0;
    private final long maxEmbeddedBytesForExtraction;

    public RUnpackExtractor(ParseContext context, long maxEmbeddedBytesForExtraction) {
        super(context);
        this.maxEmbeddedBytesForExtraction = maxEmbeddedBytesForExtraction;
    }

    @Override
    public void parseEmbedded(
            InputStream stream, ContentHandler handler, Metadata metadata, boolean outputHtml)
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
        try (TemporaryResources tmp = new TemporaryResources()) {
            final TikaInputStream newStream =
                    TikaInputStream.get(CloseShieldInputStream.wrap(stream), tmp, metadata);
            if (stream instanceof TikaInputStream) {
                final Object container = ((TikaInputStream) stream).getOpenContainer();
                if (container != null) {
                    newStream.setOpenContainer(container);
                }
            }
            EmbeddedDocumentBytesHandler bytesHandler =
                    context.get(EmbeddedDocumentBytesHandler.class);
            if (bytesHandler != null) {
                parseWithBytes(newStream, handler, metadata);
            } else {
                parse(newStream, handler, metadata);
            }
        } catch (EncryptedDocumentException ede) {
            recordException(ede, context);
        } catch (CorruptedFileException e) {
            // necessary to stop the parse to avoid infinite loops
            // on corrupt sqlite3 files
            throw new IOException(e);
        } catch (TikaException e) {
            recordException(e, context);
        }

        if (outputHtml) {
            handler.endElement(XHTML, "div", "div");
        }
    }

    private void parseWithBytes(TikaInputStream stream, ContentHandler handler, Metadata metadata)
            throws TikaException, IOException, SAXException {
        // TODO -- improve the efficiency of this so that we're not
        // literally writing out a file per request
        Path p = stream.getPath();
        try {
            parse(stream, handler, metadata);
        } finally {
            storeEmbeddedBytes(p, metadata);
        }
    }

    private void parse(TikaInputStream stream, ContentHandler handler, Metadata metadata)
            throws TikaException, IOException, SAXException {
        getDelegatingParser()
                .parse(
                        stream,
                        new EmbeddedContentHandler(new BodyContentHandler(handler)),
                        metadata,
                        context);
    }

    private void storeEmbeddedBytes(Path p, Metadata metadata) {
        if (!embeddedBytesSelector.select(metadata)) {
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug(
                        "skipping embedded bytes {} <-> {}",
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
                throw new IOException(
                        "Bytes extracted ("
                                + bytesExtracted
                                + ") >= max allowed ("
                                + maxEmbeddedBytesForExtraction
                                + ")");
            }
            long maxToRead = maxEmbeddedBytesForExtraction - bytesExtracted;

            try (BoundedInputStream boundedIs = new BoundedInputStream(maxToRead, is)) {
                embeddedDocumentBytesHandler.add(id, metadata, boundedIs);
                bytesExtracted += boundedIs.getPos();
                if (boundedIs.hasHitBound()) {
                    throw new IOException(
                            "Bytes extracted ("
                                    + bytesExtracted
                                    + ") >= max allowed ("
                                    + maxEmbeddedBytesForExtraction
                                    + "). Truncated "
                                    + "bytes");
                }
            }
        } catch (IOException e) {
            LOGGER.warn("problem writing out embedded bytes", e);
            // info in metadata doesn't actually make it back to the metadata list
            // because we're filtering and cloning the metadata at the end of the parse
            // which happens before we try to copy out the files.
            // TODO fix this
            // metadata.set(TikaCoreProperties.EMBEDDED_BYTES_EXCEPTION,
            //      ExceptionUtils.getStackTrace(e));
        }
    }

    public void setEmbeddedBytesSelector(EmbeddedBytesSelector embeddedBytesSelector) {
        this.embeddedBytesSelector = embeddedBytesSelector;
    }

    public EmbeddedBytesSelector getEmbeddedBytesSelector() {
        return embeddedBytesSelector;
    }
}
