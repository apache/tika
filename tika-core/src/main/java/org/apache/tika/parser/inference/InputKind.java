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
package org.apache.tika.parser.inference;

import java.util.Set;

import org.apache.tika.mime.MediaType;

/** What a binding is fed: a document's text, its rendered pages, an image, or media bytes. */
public enum InputKind {
    TEXT, PAGES, IMAGES, MEDIA;

    // text that names other files; ffmpeg would open them
    private static final Set<String> PLAYLISTS = Set.of("audio/x-mpegurl",
            "application/vnd.apple.mpegurl", "video/vnd.mpegurl", "audio/x-scpls",
            "application/xspf+xml", "audio/x-ms-wax", "video/x-ms-wvx", "application/x-ms-asx");

    /** The kind a document's bytes are, by media type family; null for the rest and for playlists. */
    public static InputKind of(MediaType type) {
        if (type == null || PLAYLISTS.contains(type.getBaseType().toString())) {
            return null;
        }
        switch (type.getType()) {
            case "image":
                return IMAGES;
            case "audio":
            case "video":
                return MEDIA;
            default:
                return null;
        }
    }
}
