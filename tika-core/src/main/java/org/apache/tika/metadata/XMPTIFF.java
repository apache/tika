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
 * Metadata keys for values that derive strictly from the XMP {@code tiff:} / {@code exif:}
 * schemas, mirroring {@link TIFF}. Tika folds these into the canonical {@link TIFF} keys, where
 * the same value can also arrive from binary EXIF; these parallel keys preserve provenance so a
 * consumer can distinguish an XMP-sourced value from a binary one.
 * <p>
 * Same pattern as {@link XMPDC}. Deliberately not implemented by {@link Metadata}; reference it
 * directly.
 */
public interface XMPTIFF {

    String PREFIX_TIFF = "xmp" + TikaCoreProperties.NAMESPACE_PREFIX_DELIMITER + "tiff";
    String PREFIX_EXIF = "xmp" + TikaCoreProperties.NAMESPACE_PREFIX_DELIMITER + "exif";

    Property EQUIPMENT_MAKE = Property.internalText(
            PREFIX_TIFF + TikaCoreProperties.NAMESPACE_PREFIX_DELIMITER + "Make");

    Property EQUIPMENT_MODEL = Property.internalText(
            PREFIX_TIFF + TikaCoreProperties.NAMESPACE_PREFIX_DELIMITER + "Model");

    Property SOFTWARE = Property.internalText(
            PREFIX_TIFF + TikaCoreProperties.NAMESPACE_PREFIX_DELIMITER + "Software");

    Property IMAGE_WIDTH = Property.internalInteger(
            PREFIX_TIFF + TikaCoreProperties.NAMESPACE_PREFIX_DELIMITER + "ImageWidth");

    Property IMAGE_LENGTH = Property.internalInteger(
            PREFIX_TIFF + TikaCoreProperties.NAMESPACE_PREFIX_DELIMITER + "ImageLength");

    Property BITS_PER_SAMPLE = Property.internalIntegerSequence(
            PREFIX_TIFF + TikaCoreProperties.NAMESPACE_PREFIX_DELIMITER + "BitsPerSample");

    Property SAMPLES_PER_PIXEL = Property.internalInteger(
            PREFIX_TIFF + TikaCoreProperties.NAMESPACE_PREFIX_DELIMITER + "SamplesPerPixel");

    Property ORIENTATION = Property.internalText(
            PREFIX_TIFF + TikaCoreProperties.NAMESPACE_PREFIX_DELIMITER + "Orientation");

    Property ORIGINAL_DATE = Property.internalDate(
            PREFIX_EXIF + TikaCoreProperties.NAMESPACE_PREFIX_DELIMITER + "DateTimeOriginal");

    Property ISO_SPEED_RATINGS = Property.internalIntegerSequence(
            PREFIX_EXIF + TikaCoreProperties.NAMESPACE_PREFIX_DELIMITER + "IsoSpeedRatings");

    /** Valid image width; folded into canonical {@link TIFF#IMAGE_WIDTH}. */
    Property PIXEL_X_DIMENSION = Property.internalInteger(
            PREFIX_EXIF + TikaCoreProperties.NAMESPACE_PREFIX_DELIMITER + "PixelXDimension");

    /** Valid image height; folded into canonical {@link TIFF#IMAGE_LENGTH}. */
    Property PIXEL_Y_DIMENSION = Property.internalInteger(
            PREFIX_EXIF + TikaCoreProperties.NAMESPACE_PREFIX_DELIMITER + "PixelYDimension");
}
