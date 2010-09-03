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
 * XMP Exif TIFF schema. This is a collection of
 * {@link Property property definition} constants for the Exif TIFF
 * properties defined in the XMP standard.
 *
 * @since Apache Tika 0.8
 * @see <a href="http://www.adobe.com/devnet/xmp/pdfs/XMPSpecificationPart2.pdf"
 *        >XMP Specification, Part 2: Standard Schemas</a>
 */
public interface TIFF {

    /**
     * "Number of bits per component in each channel."
     */
    Property BITS_PER_SAMPLE =
        Property.internalIntegerSequence("tiff:BitsPerSample");

    /**
     * "Image height in pixels."
     */
    Property IMAGE_LENGTH =
        Property.internalInteger("tiff:ImageLength");

    /**
     * "Image width in pixels."
     */
    Property IMAGE_WIDTH =
        Property.internalInteger("tiff:ImageWidth");

    /**
     * "Number of components per pixel."
     */
    Property SAMPLES_PER_PIXEL =
        Property.internalInteger("tiff:SamplesPerPixel");

    /**
     * "Date and time when original image was generated"
     */
    Property ORIGINAL_DATE =
       Property.internalDate("exif:DateTimeOriginal");
}
