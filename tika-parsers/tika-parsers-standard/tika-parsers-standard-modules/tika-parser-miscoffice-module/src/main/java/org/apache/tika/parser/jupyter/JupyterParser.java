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
package org.apache.tika.parser.jupyter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Set;

import com.fasterxml.jackson.core.JacksonException;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import org.apache.tika.annotation.TikaComponent;
import org.apache.tika.exception.TikaException;
import org.apache.tika.extractor.EmbeddedDocumentExtractor;
import org.apache.tika.extractor.EmbeddedDocumentUtil;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.HttpHeaders;
import org.apache.tika.metadata.Jupyter;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.Property;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.sax.XHTMLContentHandler;

/**
 * Parser for Jupyter notebooks (nbformat 4). The text is the cell sources, a
 * markdown or raw cell as a paragraph and a code cell as preformatted text,
 * followed by its plain-text outputs (streams, results, errors). The image
 * outputs are embedded documents; the one {@link NbsphinxThumbnail} picks is the
 * {@link TikaCoreProperties.EmbeddedResourceType#THUMBNAIL}, the others are
 * {@link TikaCoreProperties.EmbeddedResourceType#INLINE}. Kernel, language and
 * format version go to the {@link Jupyter} keys.
 */
@TikaComponent
public class JupyterParser implements Parser {

    private static final long serialVersionUID = 6620473093245861874L;

    static final MediaType NOTEBOOK = MediaType.application("x-ipynb+json");
    private static final Set<MediaType> SUPPORTED_TYPES = Set.of(NOTEBOOK);

    /** The notebook is read into memory up to this size; large ones are a few tens of MB. */
    static final int MAX_DOCUMENT_LENGTH = 256 * 1024 * 1024;
    /** One string, a cell's source or an encoded image, is read up to this length. */
    static final int MAX_STRING_LENGTH = 64 * 1024 * 1024;

    @Override
    public Set<MediaType> getSupportedTypes(ParseContext context) {
        return SUPPORTED_TYPES;
    }

    @Override
    public void parse(TikaInputStream tis, ContentHandler handler, Metadata metadata,
                      ParseContext context) throws IOException, SAXException, TikaException {
        Notebook notebook;
        try {
            notebook = Notebook.read(tis, MAX_DOCUMENT_LENGTH, MAX_STRING_LENGTH);
        } catch (JacksonException e) {
            throw new TikaException("Unreadable notebook", e);
        }
        set(metadata, Jupyter.KERNEL, notebook.kernel());
        set(metadata, Jupyter.LANGUAGE, notebook.language());
        set(metadata, Jupyter.NBFORMAT, notebook.nbformat());

        NbsphinxThumbnail.Choice thumbnail = NbsphinxThumbnail.choose(notebook.cells());
        if (thumbnail.problem() != null) {
            EmbeddedDocumentUtil.recordException(new TikaException(thumbnail.problem()),
                    metadata, context);
        }

        EmbeddedDocumentExtractor extractor =
                EmbeddedDocumentUtil.getEmbeddedDocumentExtractor(context);
        XHTMLContentHandler xhtml = new XHTMLContentHandler(handler, metadata, context);
        xhtml.startDocument();
        int inlineImages = 0;
        List<Notebook.Cell> cells = notebook.cells();
        for (int i = 0; i < cells.size(); i++) {
            Notebook.Cell cell = cells.get(i);
            if (cell.source() != null && !cell.source().isEmpty()) {
                xhtml.element("code".equals(cell.type()) ? "pre" : "p", cell.source());
            }
            List<Notebook.Output> outputs = cell.outputs();
            for (int j = 0; j < outputs.size(); j++) {
                Notebook.Output output = outputs.get(j);
                String text = output.text() != null ? output.text()
                        : output.data().get("text/plain");
                if (text != null && !text.isEmpty()) {
                    xhtml.element("pre", text);
                }
                String imageType = NbsphinxThumbnail.imageType(output);
                if (imageType != null) {
                    boolean isThumbnail = thumbnail.isAt(i, j);
                    emitImage(output.data().get(imageType), imageType, isThumbnail,
                            isThumbnail ? 0 : inlineImages++, extractor, xhtml, metadata, context);
                }
            }
        }
        xhtml.endDocument();
    }

    private static void emitImage(String data, String mimeType, boolean thumbnail, int index,
                                  EmbeddedDocumentExtractor extractor,
                                  XHTMLContentHandler xhtml, Metadata parentMetadata,
                                  ParseContext context) throws IOException, SAXException {
        byte[] bytes;
        if ("image/svg+xml".equals(mimeType)) {
            bytes = data.getBytes(StandardCharsets.UTF_8);
        } else {
            try {
                bytes = Base64.getMimeDecoder().decode(data);
            } catch (IllegalArgumentException e) {
                EmbeddedDocumentUtil.recordEmbeddedStreamException(e, parentMetadata, context);
                return;
            }
        }
        Metadata imageMetadata = Metadata.newInstance(context);
        imageMetadata.set(HttpHeaders.CONTENT_TYPE, mimeType);
        imageMetadata.set(HttpHeaders.CONTENT_LENGTH, Integer.toString(bytes.length));
        imageMetadata.set(TikaCoreProperties.EMBEDDED_RESOURCE_TYPE, thumbnail
                ? TikaCoreProperties.EmbeddedResourceType.THUMBNAIL.toString()
                : TikaCoreProperties.EmbeddedResourceType.INLINE.toString());
        EmbeddedDocumentUtil.setGeneratedResourceName(imageMetadata, thumbnail
                ? EmbeddedDocumentUtil.EmbeddedResourcePrefix.THUMBNAIL
                : EmbeddedDocumentUtil.EmbeddedResourcePrefix.IMAGE, index, mimeType);
        if (!extractor.shouldParseEmbedded(imageMetadata, context)) {
            return;
        }
        try (TikaInputStream image = TikaInputStream.get(bytes)) {
            extractor.parseEmbedded(image, xhtml, imageMetadata, context, true);
        }
    }

    private static void set(Metadata metadata, Property property, String value) {
        if (value != null && !value.isEmpty()) {
            metadata.set(property, value);
        }
    }
}
