/*
 * Copyright 2016 The Apache Software Foundation.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.tika.parser.image;

import java.io.UnsupportedEncodingException;

/**
 * Holds details on Apple ICNS icons
 */
public class ICNSType {
    private final int type;
    private final int width;
    private final int height;
    private final int bitsPerPixel;
    private final boolean hasMask;
    private final boolean hasRetinaDisplay;

    public int getType() {
        return type;
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public int getBitsPerPixel() {
        return bitsPerPixel;
    }

    public boolean hasMask() {
        return hasMask;
    }

    public boolean hasRetinaDisplay() {
        return hasRetinaDisplay;
    }

    public static int converttoInt(byte[] bytes) {
        if (bytes.length != 4) {
            throw new IllegalArgumentException("Cannot convert to integer");
        }
        return ((0xff & bytes[0]) << 24)
                | ((0xff & bytes[1]) << 16)
                | ((0xff & bytes[2]) << 8)
                | (0xff & bytes[3]);
    }

    private ICNSType(String type, int width, int height, int bitsPerPixel, boolean hasMask, boolean hasRetinaDisplay) {
        byte[] bytes = null;
        try {
            bytes = type.getBytes("US-ASCII");
        } catch (UnsupportedEncodingException cannotHappen) {
        }
        this.type = converttoInt(bytes);
        this.width = width;
        this.height = height;
        this.bitsPerPixel = bitsPerPixel;
        this.hasMask = hasMask;
        this.hasRetinaDisplay = hasRetinaDisplay;

    }
    public static final ICNSType ICNS_32x32_1BIT_IMAGE
            = new ICNSType("ICON", 32, 32, 1, false, false);
    public static final ICNSType ICNS_16x12_1BIT_IMAGE_AND_MASK
            = new ICNSType("icm#", 16, 12, 1, true, false);
    public static final ICNSType ICNS_16x12_4BIT_IMAGE
            = new ICNSType("icm4", 16, 12, 4, false, false);
    public static final ICNSType ICNS_16x12_8BIT_IMAGE
            = new ICNSType("icm8", 16, 12, 8, false, false);

    public static final ICNSType ICNS_16x16_8BIT_MASK
            = new ICNSType("s8mk", 16, 16, 8, true, false);
    public static final ICNSType ICNS_16x16_1BIT_IMAGE_AND_MASK
            = new ICNSType("ics#", 16, 16, 1, true, false);
    public static final ICNSType ICNS_16x16_4BIT_IMAGE
            = new ICNSType("ics4", 16, 16, 4, false, false);
    public static final ICNSType ICNS_16x16_8BIT_IMAGE
            = new ICNSType("ics8", 16, 16, 8, false, false);
    public static final ICNSType ICNS_16x16_24BIT_IMAGE
            = new ICNSType("is32", 16, 16, 24, false, false);

    public static final ICNSType ICNS_32x32_8BIT_MASK
            = new ICNSType("l8mk", 32, 32, 8, true, false);
    public static final ICNSType ICNS_32x32_1BIT_IMAGE_AND_MASK
            = new ICNSType("ICN#", 32, 32, 1, true, false);
    public static final ICNSType ICNS_32x32_4BIT_IMAGE
            = new ICNSType("icl4", 32, 32, 4, false, false);
    public static final ICNSType ICNS_32x32_8BIT_IMAGE
            = new ICNSType("icl8", 32, 32, 8, false, false);
    public static final ICNSType ICNS_32x32_24BIT_IMAGE
            = new ICNSType("il32", 32, 32, 24, false, false);

    public static final ICNSType ICNS_48x48_8BIT_MASK
            = new ICNSType("h8mk", 48, 48, 8, true, false);
    public static final ICNSType ICNS_48x48_1BIT_IMAGE_AND_MASK
            = new ICNSType("ich#", 48, 48, 1, true, false);
    public static final ICNSType ICNS_48x48_4BIT_IMAGE
            = new ICNSType("ich4", 48, 48, 4, false, false);
    public static final ICNSType ICNS_48x48_8BIT_IMAGE
            = new ICNSType("ich8", 48, 48, 8, false, false);
    public static final ICNSType ICNS_48x48_24BIT_IMAGE
            = new ICNSType("ih32", 48, 48, 24, false, false);
    public static final ICNSType ICNS_128x128_8BIT_MASK
            = new ICNSType("t8mk", 128, 128, 8, true, false);
    public static final ICNSType ICNS_128x128_24BIT_IMAGE
            = new ICNSType("it32", 128, 128, 24, false, false);

    public static final ICNSType ICNS_16x16_JPEG_PNG_IMAGE
            = new ICNSType("icp4", 16, 16, 0, false, false);
    public static final ICNSType ICNS_32x32_JPEG_PNG_IMAGE
            = new ICNSType("icp5", 32, 32, 0, false, false);
    public static final ICNSType ICNS_64x64_JPEG_PNG_IMAGE
            = new ICNSType("icp6", 64, 64, 0, false, false);
    public static final ICNSType ICNS_128x128_JPEG_PNG_IMAGE
            = new ICNSType("icp7", 128, 128, 0, false, false);
    public static final ICNSType ICNS_256x256_JPEG_PNG_IMAGE
            = new ICNSType("ic08", 256, 256, 0, false, false);
    public static final ICNSType ICNS_512x512_JPEG_PNG_IMAGE
            = new ICNSType("ic09", 512, 512, 0, false, false);
    public static final ICNSType ICNS_1024x1024_2X_JPEG_PNG_IMAGE
            = new ICNSType("ic10", 1024, 1024, 0, false, true);
    public static final ICNSType ICNS_16x16_2X_JPEG_PNG_IMAGE
            = new ICNSType("ic11", 16, 16, 0, false, true);
    public static final ICNSType ICNS_32x32_2X_JPEG_PNG_IMAGE
            = new ICNSType("ic12", 32, 32, 0, false, true);
    public static final ICNSType ICNS_128x128_2X_JPEG_PNG_IMAGE
            = new ICNSType("ic13", 128, 128, 0, false, true);
    public static final ICNSType ICNS_256x256_2X_JPEG_PNG_IMAGE
            = new ICNSType("ic14", 256, 256, 0, false, true);

    private static final ICNSType[] allImageTypes
            = {
                ICNS_32x32_1BIT_IMAGE, ICNS_16x12_1BIT_IMAGE_AND_MASK, ICNS_16x12_4BIT_IMAGE, ICNS_16x12_8BIT_IMAGE,
                ICNS_16x16_1BIT_IMAGE_AND_MASK, ICNS_16x16_4BIT_IMAGE, ICNS_16x16_8BIT_IMAGE, ICNS_16x16_24BIT_IMAGE,
                ICNS_32x32_1BIT_IMAGE_AND_MASK, ICNS_32x32_4BIT_IMAGE, ICNS_32x32_8BIT_IMAGE, ICNS_32x32_24BIT_IMAGE,
                ICNS_48x48_1BIT_IMAGE_AND_MASK, ICNS_48x48_4BIT_IMAGE, ICNS_48x48_8BIT_IMAGE, ICNS_48x48_24BIT_IMAGE,
                ICNS_128x128_24BIT_IMAGE, ICNS_16x16_8BIT_MASK,
                ICNS_32x32_8BIT_MASK, ICNS_48x48_8BIT_MASK, ICNS_128x128_8BIT_MASK,
                ICNS_16x16_JPEG_PNG_IMAGE, ICNS_32x32_JPEG_PNG_IMAGE, ICNS_64x64_JPEG_PNG_IMAGE, ICNS_128x128_JPEG_PNG_IMAGE, ICNS_256x256_JPEG_PNG_IMAGE,
                ICNS_512x512_JPEG_PNG_IMAGE, ICNS_1024x1024_2X_JPEG_PNG_IMAGE, ICNS_16x16_2X_JPEG_PNG_IMAGE, ICNS_32x32_2X_JPEG_PNG_IMAGE,
                ICNS_128x128_2X_JPEG_PNG_IMAGE, ICNS_256x256_2X_JPEG_PNG_IMAGE
            };

    public static ICNSType findIconType(byte[] bytes) {
        int type = converttoInt(bytes);
        for (ICNSType allImageType : allImageTypes) {
            if (allImageType.getType() == type) {
                return allImageType;
            }
        }
        return null;
    }
}
