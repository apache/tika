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

import java.util.Enumeration;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipFile;

import org.apache.tika.metadata.HttpHeaders;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;

/**
 * Picks the entry of a zip-based format that is its preview image, so
 * {@link ZipParser} can emit it as the
 * {@link org.apache.tika.metadata.TikaCoreProperties.EmbeddedResourceType#THUMBNAIL}.
 * Only the {@link ZipFile} path picks one: some formats need the whole central
 * directory to decide, which a streamed zip does not have up front.
 */
final class ZipPreview {

    static final MediaType COMIC_BOOK = MediaType.application("vnd.comicbook+zip");

    private static final Set<String> IMAGE_EXTENSIONS =
            Set.of("jpg", "jpeg", "png", "gif", "webp", "bmp", "avif", "jxl");

    private static final Map<MediaType, Function<ZipFile, String>> SELECTORS =
            Map.of(COMIC_BOOK, ZipPreview::firstImageByName);

    private ZipPreview() {
    }

    /**
     * @return the raw name of the preview entry, or null when the type has
     * no preview or the package does not carry one
     */
    static String thumbnailEntry(Metadata metadata, ZipFile zipFile) {
        MediaType type = MediaType.parse(metadata.get(HttpHeaders.CONTENT_TYPE));
        if (type == null) {
            return null;
        }
        Function<ZipFile, String> selector = SELECTORS.get(type.getBaseType());
        return selector == null ? null : selector.apply(zipFile);
    }

    /**
     * A comic's cover is its first page, and the pages are ordered by name.
     */
    private static String firstImageByName(ZipFile zipFile) {
        String first = null;
        String firstKey = null;
        Enumeration<ZipArchiveEntry> entries = zipFile.getEntries();
        while (entries.hasMoreElements()) {
            ZipArchiveEntry entry = entries.nextElement();
            String name = entry.getName();
            if (entry.isDirectory() || !isPage(name)) {
                continue;
            }
            String key = name.toLowerCase(Locale.ROOT);
            if (firstKey == null || key.compareTo(firstKey) < 0) {
                first = name;
                firstKey = key;
            }
        }
        return first;
    }

    private static boolean isPage(String name) {
        if (name.startsWith("__MACOSX/")) {
            return false;
        }
        String base = name.substring(name.lastIndexOf('/') + 1);
        int dot = base.lastIndexOf('.');
        if (base.startsWith(".") || dot < 0) {
            return false;
        }
        return IMAGE_EXTENSIONS.contains(base.substring(dot + 1).toLowerCase(Locale.ROOT));
    }
}
