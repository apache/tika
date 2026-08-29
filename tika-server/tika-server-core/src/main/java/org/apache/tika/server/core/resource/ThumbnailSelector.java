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
package org.apache.tika.server.core.resource;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.tika.metadata.HttpHeaders;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;

/**
 * Picks the document thumbnail among the embedded documents of a parse, for
 * {@code /unpack/thumbnail}. In order of preference:
 * <ol>
 *   <li>a raster {@code THUMBNAIL} directly below the document (the docProps
 *       thumbnail of an Office document, the cover art of an audio file, the
 *       preview of a raw camera file, ...);</li>
 *   <li>the {@code RENDERING} of a vector {@code THUMBNAIL} directly below the
 *       document, as the metafile renderer emits it for the EMF/WMF thumbnails
 *       of Office documents;</li>
 *   <li>a {@code RENDERING} directly below the document, as the PDF parser
 *       emits it for the first page.</li>
 * </ol>
 * Only embedded documents directly below the container are considered, so
 * the thumbnail of a document inside an archive is not the archive's, and a
 * rendering is only accepted where it renders the document or its thumbnail,
 * not the picture of some embedded object.
 */
final class ThumbnailSelector {

    /**
     * Image types a client cannot display without a rasterizer.
     */
    private static final Set<String> VECTOR_TYPES = Collections.unmodifiableSet(new HashSet<>(
            Arrays.asList("image/emf", "image/x-emf", "image/wmf", "image/x-wmf",
                    "image/svg+xml", "application/postscript")));

    private ThumbnailSelector() {
    }

    /**
     * @param embedded the metadata of the embedded documents, in the order
     *                 the parser emitted them
     * @return the metadata of the thumbnail, or null if there is none
     */
    static Metadata select(List<Metadata> embedded) {
        Metadata vectorThumbnail = null;
        for (Metadata m : embedded) {
            if (!isDirectChild(m) || !isType(m, TikaCoreProperties.EmbeddedResourceType.THUMBNAIL)) {
                continue;
            }
            if (isRaster(m)) {
                return m;
            }
            if (vectorThumbnail == null && isVector(m)) {
                vectorThumbnail = m;
            }
        }
        if (vectorThumbnail != null) {
            Metadata rendering = renderingOf(vectorThumbnail, embedded);
            if (rendering != null) {
                return rendering;
            }
        }
        for (Metadata m : embedded) {
            if (isDirectChild(m) && isType(m, TikaCoreProperties.EmbeddedResourceType.RENDERING)
                    && isRaster(m)) {
                return m;
            }
        }
        return null;
    }

    /**
     * The rendering emitted while parsing the thumbnail itself: a raster
     * {@code RENDERING} one level below it, on its embedded resource path.
     */
    private static Metadata renderingOf(Metadata thumbnail, List<Metadata> embedded) {
        String path = thumbnail.get(TikaCoreProperties.EMBEDDED_RESOURCE_PATH);
        if (path == null) {
            return null;
        }
        for (Metadata m : embedded) {
            String candidatePath = m.get(TikaCoreProperties.EMBEDDED_RESOURCE_PATH);
            if (depth(m) == depth(thumbnail) + 1
                    && isType(m, TikaCoreProperties.EmbeddedResourceType.RENDERING)
                    && isRaster(m)
                    && candidatePath != null && candidatePath.startsWith(path + "/")) {
                return m;
            }
        }
        return null;
    }

    private static boolean isDirectChild(Metadata m) {
        return depth(m) == 1;
    }

    private static int depth(Metadata m) {
        Integer depth = m.getInt(TikaCoreProperties.EMBEDDED_DEPTH);
        return depth == null ? -1 : depth;
    }

    private static boolean isType(Metadata m, TikaCoreProperties.EmbeddedResourceType type) {
        return type.name().equals(m.get(TikaCoreProperties.EMBEDDED_RESOURCE_TYPE));
    }

    private static boolean isRaster(Metadata m) {
        String contentType = contentType(m);
        return contentType.startsWith("image/") && !VECTOR_TYPES.contains(contentType);
    }

    private static boolean isVector(Metadata m) {
        return VECTOR_TYPES.contains(contentType(m));
    }

    private static String contentType(Metadata m) {
        String contentType = m.get(HttpHeaders.CONTENT_TYPE);
        if (contentType == null) {
            return "";
        }
        int semicolon = contentType.indexOf(';');
        return (semicolon > 0 ? contentType.substring(0, semicolon) : contentType).trim()
                .toLowerCase(java.util.Locale.ROOT);
    }
}
