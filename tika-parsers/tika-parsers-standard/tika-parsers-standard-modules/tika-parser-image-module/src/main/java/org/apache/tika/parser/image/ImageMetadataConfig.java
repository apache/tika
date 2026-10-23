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
package org.apache.tika.parser.image;

import java.io.IOException;
import java.io.Serializable;

import org.apache.tika.config.ParseContextConfig;
import org.apache.tika.exception.TikaException;
import org.apache.tika.parser.ParseContext;

/**
 * Options for the metadata-extractor pass shared by the JPEG, TIFF, HEIF, BPG and WebP
 * parsers. Each parser reads it from its own component name in the config, or from a
 * class-keyed instance in the {@link ParseContext}.
 */
public class ImageMetadataConfig implements Serializable {

    private static final long serialVersionUID = 1L;

    private boolean includeIccCurvesAndLuts = false;

    /**
     * Whether ICC profile tags whose data is a tone curve or lookup table (curv, para, mft1,
     * mft2, mAB, mBA) are written to {@code icc:*}. Off by default: the values are long
     * numeric dumps that nobody queries, and formatting them dominates the cost of reading an
     * ICC-profiled image. Header and text tags (profile description, color space, device
     * class, ...) are always kept.
     */
    public boolean isIncludeIccCurvesAndLuts() {
        return includeIccCurvesAndLuts;
    }

    public void setIncludeIccCurvesAndLuts(boolean includeIccCurvesAndLuts) {
        this.includeIccCurvesAndLuts = includeIccCurvesAndLuts;
    }

    static ImageMetadataConfig resolve(ParseContext context, String configKey,
                                       ImageMetadataConfig defaultConfig)
            throws TikaException, IOException {
        return ParseContextConfig.getConfig(context, configKey, ImageMetadataConfig.class,
                defaultConfig);
    }
}
