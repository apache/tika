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

import java.util.List;
import java.util.Locale;

import org.apache.tika.metadata.HttpHeaders;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;

/**
 * Picks the document thumbnail among the embedded documents of a parse, for
 * {@code /unpack/thumbnail}. In order of preference:
 * <ol>
 *   <li>a raster {@code THUMBNAIL} directly below the document: the stored
 *       thumbnail of most formats;</li>
 *   <li>a raster image directly below a {@code THUMBNAIL} of the document:
 *       the rendering of an EMF/WMF thumbnail, which the metafile parsers
 *       emit as a {@code THUMBNAIL} as well;</li>
 *   <li>a raster {@code RENDERING} directly below the document: the first
 *       page of a PDF.</li>
 * </ol>
 * Only the document's own children count, so the thumbnail of a document
 * inside an archive is not the archive's, and the picture of an embedded
 * object is never mistaken for the document's rendering.
 */
final class ThumbnailSelector {

    private ThumbnailSelector() {
    }

    /**
     * @param embedded the metadata of the embedded documents, in the order
     *                 the parser emitted them
     * @return the metadata of the thumbnail, or null if there is none
     */
    static Metadata select(List<Metadata> embedded) {
        for (Metadata m : embedded) {
            if (depth(m) == 1 && isThumbnail(m) && isRaster(m)) {
                return m;
            }
        }
        for (Metadata thumbnail : embedded) {
            if (depth(thumbnail) != 1 || !isThumbnail(thumbnail)) {
                continue;
            }
            String path = thumbnail.get(TikaCoreProperties.EMBEDDED_RESOURCE_PATH);
            if (path == null) {
                continue;
            }
            for (Metadata m : embedded) {
                String candidatePath = m.get(TikaCoreProperties.EMBEDDED_RESOURCE_PATH);
                if (depth(m) == 2 && isRaster(m) && candidatePath != null
                        && candidatePath.startsWith(path + "/")
                        && (isThumbnail(m) || isRendering(m))) {
                    return m;
                }
            }
        }
        for (Metadata m : embedded) {
            if (depth(m) == 1 && isRendering(m) && isRaster(m)) {
                return m;
            }
        }
        return null;
    }

    private static int depth(Metadata m) {
        Integer depth = m.getInt(TikaCoreProperties.EMBEDDED_DEPTH);
        return depth == null ? -1 : depth;
    }

    private static boolean isThumbnail(Metadata m) {
        return TikaCoreProperties.EmbeddedResourceType.THUMBNAIL.name()
                .equals(m.get(TikaCoreProperties.EMBEDDED_RESOURCE_TYPE));
    }

    private static boolean isRendering(Metadata m) {
        return TikaCoreProperties.EmbeddedResourceType.RENDERING.name()
                .equals(m.get(TikaCoreProperties.EMBEDDED_RESOURCE_TYPE));
    }

    /**
     * An image a client can display without a rasterizer: PNG, JPEG, GIF,
     * WebP, ..., but not a metafile or SVG.
     */
    private static boolean isRaster(Metadata m) {
        String contentType = m.get(HttpHeaders.CONTENT_TYPE);
        if (contentType == null) {
            return false;
        }
        int semicolon = contentType.indexOf(';');
        String type = (semicolon > 0 ? contentType.substring(0, semicolon) : contentType)
                .trim().toLowerCase(Locale.ROOT);
        return type.startsWith("image/") && !type.equals("image/emf")
                && !type.equals("image/x-emf") && !type.equals("image/wmf")
                && !type.equals("image/x-wmf") && !type.equals("image/svg+xml");
    }
}
