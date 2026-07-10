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
package org.apache.tika.grpc.mapper.transform;

import java.util.Locale;

import org.apache.tika.grpc.v1.FormatCategory;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;

/**
 * Detects the coarse {@link FormatCategory} routing hint from Tika metadata. This is
 * independent of which {@link DocumentTransformer}s actually run -- transformers
 * self-select via {@link DocumentTransformer#appliesTo(String)} and are not mutually
 * exclusive (e.g. Creative Commons rights can coexist with any category here), so this
 * enum exists purely as a cheap client-side routing hint.
 */
public final class FormatCategoryDetector {

    private FormatCategoryDetector() {
    }

    public static FormatCategory detect(Metadata metadata) {
        String mimeType = metadata.get(Metadata.CONTENT_TYPE);
        String resourceName = metadata.get(TikaCoreProperties.RESOURCE_NAME_KEY);

        if (mimeType != null) {
            String mime = mimeType.toLowerCase(Locale.ROOT);
            if (mime.contains("pdf")) {
                return FormatCategory.FORMAT_CATEGORY_PDF;
            }
            if (mime.contains("officedocument") || mime.contains("msword")
                    || mime.contains("ms-excel") || mime.contains("ms-powerpoint")
                    || mime.contains("opendocument") || mime.contains("vnd.ms-")
                    || mime.contains("vnd.openxmlformats")) {
                return FormatCategory.FORMAT_CATEGORY_OFFICE;
            }
            if (mime.startsWith("image/")) {
                return FormatCategory.FORMAT_CATEGORY_IMAGE;
            }
            if (mime.contains("text/html") || mime.contains("application/xhtml")) {
                return FormatCategory.FORMAT_CATEGORY_HTML;
            }
            if (mime.contains("rtf")) {
                return FormatCategory.FORMAT_CATEGORY_RTF;
            }
            if (mime.contains("epub")) {
                return FormatCategory.FORMAT_CATEGORY_EPUB;
            }
            if (mime.contains("warc")) {
                return FormatCategory.FORMAT_CATEGORY_WARC;
            }
        }

        if (resourceName != null) {
            String name = resourceName.toLowerCase(Locale.ROOT);
            if (name.endsWith(".pdf")) {
                return FormatCategory.FORMAT_CATEGORY_PDF;
            }
            if (name.endsWith(".doc") || name.endsWith(".docx") || name.endsWith(".xls")
                    || name.endsWith(".xlsx") || name.endsWith(".ppt") || name.endsWith(".pptx")
                    || name.endsWith(".odt") || name.endsWith(".ods") || name.endsWith(".odp")) {
                return FormatCategory.FORMAT_CATEGORY_OFFICE;
            }
            if (name.endsWith(".jpg") || name.endsWith(".jpeg") || name.endsWith(".png")
                    || name.endsWith(".gif") || name.endsWith(".tiff") || name.endsWith(".tif")
                    || name.endsWith(".bmp")) {
                return FormatCategory.FORMAT_CATEGORY_IMAGE;
            }
            if (name.endsWith(".html") || name.endsWith(".htm") || name.endsWith(".xhtml")) {
                return FormatCategory.FORMAT_CATEGORY_HTML;
            }
            if (name.endsWith(".rtf")) {
                return FormatCategory.FORMAT_CATEGORY_RTF;
            }
            if (name.endsWith(".epub")) {
                return FormatCategory.FORMAT_CATEGORY_EPUB;
            }
            if (name.endsWith(".warc") || name.endsWith(".arc")) {
                return FormatCategory.FORMAT_CATEGORY_WARC;
            }
        }

        return FormatCategory.FORMAT_CATEGORY_GENERIC;
    }
}
