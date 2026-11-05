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

import java.util.Locale;
import java.util.Set;

import org.apache.tika.metadata.HttpHeaders;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;
import org.apache.tika.mime.MediaTypeRegistry;

/**
 * Comic book archives: a zip, RAR, 7z or tar of page images, whose cover is the first page
 * in name order.
 */
final class ComicBook {

    static final MediaType CBZ = MediaType.application("vnd.comicbook+zip");
    static final MediaType CBR = MediaType.application("vnd.comicbook-rar");
    static final MediaType CB7 = MediaType.application("x-cb7");
    static final MediaType CBT = MediaType.application("x-cbt");

    private static final Set<String> IMAGE_EXTENSIONS =
            Set.of("jpg", "jpeg", "png", "gif", "webp", "bmp", "avif", "jxl");

    private ComicBook() {
    }

    /** Whether the document's content type, its aliases resolved, is the given comic type. */
    static boolean is(Metadata metadata, MediaType comic) {
        MediaType type = MediaType.parse(metadata.get(HttpHeaders.CONTENT_TYPE));
        return type != null && comic.equals(
                MediaTypeRegistry.getDefaultRegistry().normalize(type.getBaseType()));
    }

    /** The cover among the archive's file names: the first page in name order, or null. */
    static String cover(Iterable<String> names) {
        String first = null;
        String firstKey = null;
        for (String name : names) {
            if (name == null || !isPage(name)) {
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
        String base = name.substring(Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\')) + 1);
        int dot = base.lastIndexOf('.');
        if (base.startsWith(".") || dot < 0) {
            return false;
        }
        return IMAGE_EXTENSIONS.contains(base.substring(dot + 1).toLowerCase(Locale.ROOT));
    }
}
