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

import java.io.Serializable;
import java.util.List;
import java.util.regex.Pattern;

import org.apache.tika.annotation.TikaComponent;
import org.apache.tika.config.Initializable;
import org.apache.tika.config.OperatorOnly;
import org.apache.tika.exception.TikaConfigException;
import org.apache.tika.parser.ParseContext;

/**
 * The {@code "media-formats"} parse-context block: the ffmpeg demuxers audio and video may be
 * read with before a MEDIA binding sees them. A file in any other format is refused.
 * <p>
 * Loaded from the {@code parse-context} section of the config and not settable per request.
 */
@OperatorOnly
@TikaComponent(name = "media-formats", spi = false)
public class MediaFormats implements Serializable, Initializable {

    private static final long serialVersionUID = 1L;

    public static final List<String> DEFAULT_ALLOWED_FORMATS = List.of("mov", "matroska",
            "avi", "asf", "flv", "mpegts", "mpeg", "mp3", "wav", "w64", "aiff", "flac", "ogg",
            "aac", "ac3", "eac3", "amr", "caf", "au", "wv", "ape", "mxf", "rm", "dv", "m4v",
            "mpegvideo", "h264", "hevc");

    private static final Pattern FORMAT_NAME = Pattern.compile("[A-Za-z0-9_]+");

    private List<String> allowedFormats = DEFAULT_ALLOWED_FORMATS;

    public List<String> getAllowedFormats() {
        return allowedFormats;
    }

    /**
     * This replaces {@link #DEFAULT_ALLOWED_FORMATS}, it does not add to it.
     */
    public void setAllowedFormats(List<String> allowedFormats) {
        this.allowedFormats =
                allowedFormats == null ? DEFAULT_ALLOWED_FORMATS : List.copyOf(allowedFormats);
    }

    @Override
    public void initialize() throws TikaConfigException {
        if (allowedFormats.isEmpty()) {
            throw new TikaConfigException(
                    "media-formats.allowedFormats must name at least one format");
        }
        for (String format : allowedFormats) {
            if (!FORMAT_NAME.matcher(format).matches()) {
                throw new TikaConfigException(
                        "media-formats.allowedFormats: not a format name: " + format);
            }
        }
    }

    /**
     * @return the context's formats, or a new instance with the defaults
     */
    public static MediaFormats get(ParseContext context) {
        MediaFormats formats = context == null ? null : context.get(MediaFormats.class);
        return formats != null ? formats : new MediaFormats();
    }
}
