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
 * Properties of icon containers that hold several images of one motif in
 * different sizes and colour depths, such as Windows ICO and CUR files.
 * The dimensions of the largest image go to the {@link TIFF} properties.
 *
 * @since Apache Tika 4.1.1
 */
public interface Icon {

    String ICON_PREFIX = "icon" + TikaCoreProperties.NAMESPACE_PREFIX_DELIMITER;

    /**
     * Number of images in the container.
     */
    Property IMAGE_COUNT = Property.internalInteger(ICON_PREFIX + "image-count");

    /**
     * One value per image, in container order: {@code WIDTHxHEIGHT@BITSbpp ENCODING},
     * for example {@code 32x32@32bpp bmp} or {@code 256x256@32bpp png}.
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
