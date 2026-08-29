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
package org.apache.tika.parser.microsoft;

import java.io.IOException;

import org.xml.sax.SAXException;

import org.apache.tika.exception.TikaException;
import org.apache.tika.extractor.EmbeddedDocumentExtractor;
import org.apache.tika.extractor.EmbeddedDocumentUtil;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.HttpHeaders;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.renderer.RenderResult;
import org.apache.tika.renderer.RenderResults;
import org.apache.tika.renderer.Renderer;
import org.apache.tika.renderer.microsoft.POIMetafileRenderer;
import org.apache.tika.sax.EmbeddedContentHandler;
import org.apache.tika.sax.XHTMLContentHandler;

/**
 * Shared by {@link EMFParser} and {@link WMFParser}: renders a parsed
 * metafile through a {@link Renderer} and emits the result as a
 * {@link TikaCoreProperties.EmbeddedResourceType#RENDERING} embedded
 * document, the way the PDF parser emits page renderings.
 */
final class MetafileRendering {

    private MetafileRendering() {
    }

    /**
     * @param injected the renderer set on the parser, or null
     * @param picture  the parsed {@code HemfPicture} or {@code HwmfPicture}
     */
    static void render(Renderer injected, MetafileParserConfig config, MediaType type,
                       Object picture, XHTMLContentHandler xhtml, Metadata metadata,
                       ParseContext context) throws IOException, SAXException {
        //like the PDF parser: the injected renderer if it handles the type,
        //the default one otherwise
        Renderer renderer = injected != null && injected.getSupportedTypes(context).contains(type)
                ? injected : defaultRenderer(config);
        Metadata renderMetadata = Metadata.newInstance(context);
        renderMetadata.set(TikaCoreProperties.TYPE, type.toString());
        EmbeddedDocumentExtractor extractor =
                EmbeddedDocumentUtil.getEmbeddedDocumentExtractor(context);
        try (TikaInputStream pictureStream = TikaInputStream.get(new byte[0]);
             RenderResults results = render(renderer, pictureStream, picture, renderMetadata,
                     context)) {
            if (results == null) {
                return;
            }
            for (RenderResult result : results.getResults()) {
                if (result.getStatus() != RenderResult.STATUS.SUCCESS) {
                    EmbeddedDocumentUtil.recordException(
                            new TikaException("metafile rendering failed"), metadata, context);
                    continue;
                }
                Metadata renderingMetadata = result.getMetadata();
                renderingMetadata.set(TikaCoreProperties.RESOURCE_NAME_KEY,
                        renderingName(metadata, renderingMetadata));
                if (extractor.shouldParseEmbedded(renderingMetadata, context)) {
                    try (TikaInputStream tis = result.getInputStream()) {
                        extractor.parseEmbedded(tis, new EmbeddedContentHandler(xhtml),
                                renderingMetadata, context, false);
                    }
                }
            }
        }
    }

    private static RenderResults render(Renderer renderer, TikaInputStream pictureStream,
                                        Object picture, Metadata renderMetadata,
                                        ParseContext context) throws IOException {
        //hand the parsed picture over instead of re-reading the stream
        pictureStream.setOpenContainer(picture);
        try {
            return renderer.render(pictureStream, renderMetadata, context);
        } catch (SecurityException e) {
            throw e;
        } catch (Exception e) {
            EmbeddedDocumentUtil.recordException(e, renderMetadata, context);
            return null;
        }
    }

    private static Renderer defaultRenderer(MetafileParserConfig config) {
        POIMetafileRenderer renderer = new POIMetafileRenderer();
        renderer.setWidth(config.getRenderWidth());
        return renderer;
    }

    /**
     * The rendering is named after the image, with the rendering's format
     * as its extension.
     */
    private static String renderingName(Metadata metadata, Metadata renderingMetadata) {
        String contentType = renderingMetadata.get(HttpHeaders.CONTENT_TYPE);
        String extension = contentType != null && contentType.startsWith("image/")
                ? contentType.substring("image/".length()) : "png";
        String name = metadata.get(TikaCoreProperties.RESOURCE_NAME_KEY);
        if (name == null || name.isEmpty()) {
            return "rendering." + extension;
        }
        int slash = Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\'));
        String base = name.substring(slash + 1);
        int dot = base.lastIndexOf('.');
        return (dot > 0 ? base.substring(0, dot) : base) + "." + extension;
    }
}
