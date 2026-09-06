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
import org.apache.tika.io.FilenameUtils;
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
 * metafile through a {@link Renderer} and emits the result as an embedded
 * document, the way the PDF parser emits page renderings. The rendering of
 * a {@link TikaCoreProperties.EmbeddedResourceType#THUMBNAIL} is itself a
 * THUMBNAIL (it is the same picture in a form a client can display); any
 * other rendering is a {@link TikaCoreProperties.EmbeddedResourceType#RENDERING}.
 */
final class MetafileRendering {

    private MetafileRendering() {
    }

    /**
     * @param injected the renderer set on the parser, or null
     * @param picture  the parsed {@code HemfPicture} or {@code HwmfPicture}
     */
    static void render(Renderer injected, MetafileParserConfig config, MediaType type,
                       TikaInputStream source, Object picture, XHTMLContentHandler xhtml,
                       Metadata metadata, ParseContext context) throws IOException, SAXException {
        //like the PDF parser: the injected renderer if it handles the type,
        //the default one otherwise
        Renderer renderer = injected != null && injected.getSupportedTypes(context).contains(type)
                ? injected : defaultRenderer(config);
        Metadata renderMetadata = Metadata.newInstance(context);
        renderMetadata.set(TikaCoreProperties.TYPE, type.toString());
        EmbeddedDocumentExtractor extractor =
                EmbeddedDocumentUtil.getEmbeddedDocumentExtractor(context);
        //rendering is the expensive part: ask before paying for it. The name
        //assumes the renderer's default format; the result's own metadata
        //replaces it below.
        Metadata gate = Metadata.newInstance(context);
        gate.set(TikaCoreProperties.RESOURCE_NAME_KEY, renderingName(metadata, "png"));
        gate.set(HttpHeaders.CONTENT_TYPE, "image/png");
        gate.set(TikaCoreProperties.EMBEDDED_RESOURCE_TYPE, renderingType(metadata).name());
        if (!extractor.shouldParseEmbedded(gate, context)) {
            return;
        }
        try (TikaInputStream pictureStream = pictureStream(source);
             RenderResults results = render(renderer, pictureStream, picture, renderMetadata,
                     metadata, context)) {
            if (results == null) {
                return;
            }
            for (RenderResult result : results.getResults()) {
                if (result.getStatus() != RenderResult.STATUS.SUCCESS) {
                    //carry the renderer's diagnostics over to the metafile
                    String[] warnings = result.getMetadata()
                            .getValues(TikaCoreProperties.TIKA_META_EXCEPTION_WARNING);
                    if (warnings.length == 0) {
                        EmbeddedDocumentUtil.recordException(
                                new TikaException("metafile rendering failed"), metadata, context);
                    }
                    for (String warning : warnings) {
                        metadata.add(TikaCoreProperties.TIKA_META_EXCEPTION_WARNING, warning);
                    }
                    continue;
                }
                Metadata renderingMetadata = result.getMetadata();
                renderingMetadata.set(TikaCoreProperties.RESOURCE_NAME_KEY,
                        renderingName(metadata, extension(renderingMetadata)));
                renderingMetadata.set(TikaCoreProperties.EMBEDDED_RESOURCE_TYPE,
                        renderingType(metadata).name());
                if (extractor.shouldParseEmbedded(renderingMetadata, context)) {
                    try (TikaInputStream tis = result.getInputStream()) {
                        extractor.parseEmbedded(tis, new EmbeddedContentHandler(xhtml),
                                renderingMetadata, context, false);
                    }
                }
            }
        }
    }

    /**
     * The stream the renderer reads the picture from: the metafile itself
     * where the parser spooled it, so a renderer that does not know the
     * open-container shortcut still sees the bytes.
     */
    private static TikaInputStream pictureStream(TikaInputStream source) throws IOException {
        if (source != null && source.hasFile()) {
            return TikaInputStream.get(source.getPath());
        }
        return TikaInputStream.getPlaceholder();
    }

    /**
     * The rendering of a THUMBNAIL is itself a THUMBNAIL: it is the same
     * picture in a form a client can display. Any other rendering is a
     * RENDERING.
     */
    private static TikaCoreProperties.EmbeddedResourceType renderingType(Metadata metadata) {
        return TikaCoreProperties.EmbeddedResourceType.THUMBNAIL.name()
                .equals(metadata.get(TikaCoreProperties.EMBEDDED_RESOURCE_TYPE))
                ? TikaCoreProperties.EmbeddedResourceType.THUMBNAIL
                : TikaCoreProperties.EmbeddedResourceType.RENDERING;
    }

    private static String extension(Metadata renderingMetadata) {
        String contentType = renderingMetadata.get(HttpHeaders.CONTENT_TYPE);
        return contentType != null && contentType.startsWith("image/")
                ? contentType.substring("image/".length()) : "png";
    }

    /**
     * @param renderMetadata what the renderer is told about the picture (its
     *                       type, so a composite renderer can route it)
     * @param parentMetadata the metafile's metadata; a rendering failure is
     *                       recorded there, so it is not silently swallowed
     */
    private static RenderResults render(Renderer renderer, TikaInputStream pictureStream,
                                        Object picture, Metadata renderMetadata,
                                        Metadata parentMetadata, ParseContext context)
            throws IOException {
        //hand the parsed picture over instead of re-reading the stream
        pictureStream.setOpenContainer(picture);
        try {
            return renderer.render(pictureStream, renderMetadata, context);
        } catch (SecurityException e) {
            throw e;
        } catch (Exception e) {
            EmbeddedDocumentUtil.recordException(e, parentMetadata, context);
            return null;
        }
    }

    private static Renderer defaultRenderer(MetafileParserConfig config) {
        POIMetafileRenderer renderer = new POIMetafileRenderer();
        renderer.setWidth(config.getRenderWidth());
        return renderer;
    }

    /**
     * The rendering is named after the image, with the rendering's format as
     * its extension.
     */
    private static String renderingName(Metadata metadata, String extension) {
        String name = FilenameUtils.getName(metadata.get(TikaCoreProperties.RESOURCE_NAME_KEY));
        if (name == null || name.isEmpty()) {
            return "rendering." + extension;
        }
        int dot = name.lastIndexOf('.');
        return (dot > 0 ? name.substring(0, dot) : name) + "." + extension;
    }
}
