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
     * Did the Flash fire when taking this image?
     */
    Property FLASH_FIRED =
       Property.internalBoolean("exif:Flash");
    
    /**
     * "Exposure time in seconds."
     */
    Property EXPOSURE_TIME =
       Property.internalRational("exif:ExposureTime");
    
    /**
     * "F-Number."
     * The f-number is the focal length divided by the "effective" aperture 
     *  diameter. It is a dimensionless number that is a measure of lens speed. 
     */
    Property F_NUMBER =
       Property.internalRational("exif:FNumber");
    
    /**
     * "Focal length of the lens, in millimeters."
     */
    Property FOCAL_LENGTH =
       Property.internalRational("exif:FocalLength");
    
    /**
     * "ISO Speed and ISO Latitude of the input device as specified in ISO 12232"
     */
    Property ISO_SPEED_RATINGS =
       Property.internalIntegerSequence("exif:IsoSpeedRatings");
    
    /**
     * "Manufacturer of the recording equipment."
     */
    Property EQUIPMENT_MAKE =
       Property.internalText("tiff:Make");
    
    /**
     * "Model name or number of the recording equipment."
     */
    Property EQUIPMENT_MODEL =
       Property.internalText("tiff:Model");
    
    /**
     * "Software or firmware used to generate the image."
     */
    Property SOFTWARE =
       Property.internalText("tiff:Software");

    /**
     * "The Orientation of the image."
     *  1 = 0th row at top, 0th column at left
     *  2 = 0th row at top, 0th column at right
     *  3 = 0th row at bottom, 0th column at right
     *  4 = 0th row at bottom, 0th column at left
     *  5 = 0th row at left, 0th column at top
     *  6 = 0th row at right, 0th column at top
     *  7 = 0th row at right, 0th column at bottom
     *  8 = 0th row at left, 0th column at bottom
     */
    Property ORIENTATION =
       Property.internalClosedChoise("tiff:Orientation", "1", "2", "3", "4", "5", "6", "7", "8");
    
    /**
     * "Horizontal resolution in pixels per unit."
     */
    Property RESOLUTION_HORIZONTAL =
       Property.internalRational("tiff:XResolution");
    
    /**
     * "Vertical resolution in pixels per unit."
     */
    Property RESOLUTION_VERTICAL =
       Property.internalRational("tiff:YResolution");
    
    /**
     * "Units used for Horizontal and Vertical Resolutions."
     * One of "Inch" or "cm"
     */
    Property RESOLUTION_UNIT =
       Property.internalClosedChoise("tiff:ResolutionUnit", "Inch", "cm"); 
    
    /**
     * "Date and time when original image was generated"
     */
    Property ORIGINAL_DATE =
       Property.internalDate("exif:DateTimeOriginal");
}
