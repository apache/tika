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
package org.apache.tika.metadata;

/**
 * Properties of Windows icon (ICO) and cursor (CUR) files, which hold
 * several images of one motif in different sizes and colour depths.
 * The dimensions of the largest image go to the {@link TIFF} properties.
 *
 * @since Apache Tika 4.2.0
 */
public interface Icon {

    String ICON_PREFIX = "icon" + TikaCoreProperties.NAMESPACE_PREFIX_DELIMITER;

    /**
     * Number of images found in the container, the ones {@link #IMAGES}
     * lists. A damaged container may claim more than it holds.
     */
    Property IMAGE_COUNT = Property.internalInteger(ICON_PREFIX + "image-count");

    /**
     * One value per image, in container order: {@code WIDTHxHEIGHT@BITSbpp ENCODING},
     * for example {@code 32x32@32bpp bmp} or {@code 256x256@32bpp png}. An
     * image whose own header cannot be read is listed with the container's
     * values for it and the encoding {@code unknown}; a colour depth that is
     * not known is left out, as in {@code 32x32 unknown}.
     */
    Property IMAGES = Property.internalTextBag(ICON_PREFIX + "images");

    /**
     * Cursors only: the x coordinate of the hotspot of the image whose
     * dimensions are reported, the pixel that clicks.
     */
    Property HOTSPOT_X = Property.internalInteger(ICON_PREFIX + "hotspot-x");

    /**
     * Cursors only: the y coordinate of the hotspot.
     */
    Property HOTSPOT_Y = Property.internalInteger(ICON_PREFIX + "hotspot-y");
}
