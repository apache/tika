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

import java.util.Locale;
import java.util.Set;

import org.apache.tika.annotation.TikaComponent;
import org.apache.tika.extractor.UnpackSelector;
import org.apache.tika.metadata.HttpHeaders;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.ParseContext;

/**
 * Unpacks the one embedded document a client would show as the document's thumbnail and
 * nothing else: the first raster image that is a {@code THUMBNAIL} at depth 1 or 2 (a stored
 * thumbnail, or the rendering of a vector one) or a {@code RENDERING} at depth 1 (the first
 * page of a PDF). Pair it with parser config that renders only those, as the catalog preset
 * {@code thumbnails} does.
 *
 * @since Apache Tika 4.1.0
 */
@TikaComponent
public class ThumbnailUnpackSelector implements UnpackSelector {

    private static final Set<String> VECTOR_TYPES = Set.of("image/emf", "image/x-emf",
            "image/wmf", "image/x-wmf", "image/svg+xml");

    /** Marks a parse whose thumbnail is taken; the selector itself may be shared. */
    private static final class Taken {
    }

    @Override
    public boolean select(Metadata metadata) {
        String type = metadata.get(TikaCoreProperties.EMBEDDED_RESOURCE_TYPE);
        Integer depth = metadata.getInt(TikaCoreProperties.EMBEDDED_DEPTH);
        if (type == null || depth == null || !isRaster(metadata)) {
            return false;
        }
        if (TikaCoreProperties.EmbeddedResourceType.THUMBNAIL.name().equals(type)) {
            return depth == 1 || depth == 2;
        }
        return TikaCoreProperties.EmbeddedResourceType.RENDERING.name().equals(type)
                && depth == 1;
    }

    @Override
    public boolean select(Metadata metadata, ParseContext context) {
        if (context.get(Taken.class) != null || !select(metadata)) {
            return false;
        }
        context.set(Taken.class, new Taken());
        return true;
    }

    private static boolean isRaster(Metadata metadata) {
        String contentType = metadata.get(HttpHeaders.CONTENT_TYPE);
        MediaType mediaType = contentType == null ? null : MediaType.parse(contentType);
        if (mediaType == null) {
            return false;
        }
        String base = mediaType.getBaseType().toString().toLowerCase(Locale.ROOT);
        return base.startsWith("image/") && !VECTOR_TYPES.contains(base);
    }
}
