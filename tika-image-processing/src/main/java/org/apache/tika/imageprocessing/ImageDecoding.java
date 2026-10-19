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
package org.apache.tika.imageprocessing;

import java.awt.image.BufferedImage;
import java.awt.image.IndexColorModel;
import java.awt.image.Raster;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Iterator;
import javax.imageio.ImageIO;
import javax.imageio.ImageReadParam;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;

import org.apache.tika.parser.enricher.ImageProbe;

/** What the checks read from an image, each computed once per image through the probe. */
final class ImageDecoding {

    /** The decoded copy is at most this many pixels on a side. */
    static final int MAX_SIDE = 1000;

    /** Images with more pixels than this are not decoded: a check cannot judge them. */
    static final long MAX_DECODE_PIXELS = 100_000_000L;

    /**
     * ImageIO buffers a progressive JPEG whole, about 3 bytes of native memory a pixel,
     * whatever the subsampling: a blank one of 587 KB at 10000 x 10000 took 290 MB.
     */
    static final long MAX_PROGRESSIVE_JPEG_PIXELS = 16_000_000L;

    private static final long[] UNKNOWN = {-1, -1};

    private ImageDecoding() {
    }

    /** {width, height} from the image header, or {-1, -1}. */
    static long[] size(ImageProbe image) throws IOException {
        return image.memo("imagecheck:size", () -> {
            // the start of the file holds the dimensions of nearly every format; read the whole
            // image only when it does not (a TIFF whose directory is at the end)
            long[] size = tryReadSize(image.header());
            if (size[0] <= 0 && !image.headerIsComplete()) {
                size = tryReadSize(image.bytes());
            }
            return size;
        });
    }

    /**
     * Gray levels 0..255 of a decoded copy, row by row. An image with transparency has two
     * copies, over white and over black: transparent pixels hide dark text in one and light
     * text in the other. {@code overBlack} is null for an opaque image.
     */
    record Gray(byte[] levels, byte[] overBlack, int width, int height) {
    }

    /**
     * A gray copy of the image of at most {@link #MAX_SIDE} pixels on a side, or null when it
     * cannot be read or is too large to decode.
     */
    static Gray gray(ImageProbe image) throws IOException {
        return image.memo("imagecheck:gray", () -> {
            long[] size = size(image);
            if (size[0] <= 0 || size[1] <= 0) {
                return null;
            }
            long pixels = size[0] * size[1];
            if (pixels > MAX_DECODE_PIXELS || (pixels > MAX_PROGRESSIVE_JPEG_PIXELS
                    && mayBeProgressiveJpeg(image.header()))) {
                return null;
            }
            return decode(image.bytes());
        });
    }

    /** A JPEG whose frame is progressive, or whose frame header is not in {@code bytes}. */
    static boolean mayBeProgressiveJpeg(byte[] bytes) {
        if (bytes.length < 2 || (bytes[0] & 255) != 0xFF || (bytes[1] & 255) != 0xD8) {
            return false;
        }
        int i = 2;
        while (i + 1 < bytes.length) {
            if ((bytes[i] & 255) != 0xFF) {
                return true;
            }
            int marker = bytes[i + 1] & 255;
            if (marker == 0xFF) {
                i++;
                continue;
            }
            if (marker == 0xC2 || marker == 0xC6 || marker == 0xCA || marker == 0xCE) {
                return true;
            }
            if (marker >= 0xC0 && marker <= 0xCF && marker != 0xC4 && marker != 0xC8
                    && marker != 0xCC) {
                return false;
            }
            if (marker == 0x01 || (marker >= 0xD0 && marker <= 0xD7)) {
                i += 2;
                continue;
            }
            if (marker == 0xDA || marker == 0xD9 || i + 3 >= bytes.length) {
                return true;
            }
            int length = ((bytes[i + 2] & 255) << 8) | (bytes[i + 3] & 255);
            if (length < 2) {
                return true;
            }
            i += 2 + length;
        }
        return true;
    }

    private static long[] tryReadSize(byte[] bytes) throws IOException {
        try {
            return readSize(bytes);
        } catch (IOException | RuntimeException e) {
            return UNKNOWN;
        }
    }

    private static long[] readSize(byte[] bytes) throws IOException {
        if (bytes == null) {
            return UNKNOWN;
        }
        try (ImageInputStream iis = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            ImageReader reader = reader(iis);
            if (reader == null) {
                return UNKNOWN;
            }
            try {
                reader.setInput(iis, true, true);
                return new long[] {reader.getWidth(0), reader.getHeight(0)};
            } finally {
                reader.dispose();
            }
        }
    }

    private static Gray decode(byte[] bytes) throws IOException {
        try (ImageInputStream iis = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            ImageReader reader = reader(iis);
            if (reader == null) {
                return null;
            }
            try {
                reader.setInput(iis, true, true);
                int step = (Math.max(reader.getWidth(0), reader.getHeight(0)) + MAX_SIDE - 1)
                        / MAX_SIDE;
                ImageReadParam param = reader.getDefaultReadParam();
                param.setSourceSubsampling(Math.max(1, step), Math.max(1, step), 0, 0);
                return toGray(reader.read(0, param));
            } finally {
                reader.dispose();
            }
        }
    }

    private static ImageReader reader(ImageInputStream iis) {
        if (iis == null) {
            return null;
        }
        Iterator<ImageReader> readers = ImageIO.getImageReaders(iis);
        return readers.hasNext() ? readers.next() : null;
    }

    static Gray toGray(BufferedImage image) {
        int width = image.getWidth();
        int height = image.getHeight();
        boolean alpha = image.getColorModel().hasAlpha();
        byte[] levels = new byte[width * height];
        byte[] overBlack = alpha ? new byte[width * height] : null;
        if (image.getColorModel() instanceof IndexColorModel) {
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    int argb = image.getRGB(x, y);
                    set(levels, overBlack, y * width + x, luma((argb >> 16) & 255,
                            (argb >> 8) & 255, argb & 255), (argb >>> 24), false);
                }
            }
            return new Gray(levels, overBlack, width, height);
        }
        Raster raster = image.getRaster();
        int bands = raster.getNumBands();
        int colorBands = alpha ? bands - 1 : bands;
        int colorShift = Math.max(0, raster.getSampleModel().getSampleSize(0) - 8);
        int alphaShift = alpha
                ? Math.max(0, raster.getSampleModel().getSampleSize(bands - 1) - 8) : 0;
        boolean premultiplied = image.isAlphaPremultiplied();
        int[] sample = new int[bands];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                raster.getPixel(x, y, sample);
                int gray = colorBands >= 3 ? luma(sample[0], sample[1], sample[2]) : sample[0];
                set(levels, overBlack, y * width + x, gray >> colorShift,
                        alpha ? sample[bands - 1] >> alphaShift : 255, premultiplied);
            }
        }
        return new Gray(levels, overBlack, width, height);
    }

    private static int luma(int r, int g, int b) {
        return (r * 299 + g * 587 + b * 114) / 1000;
    }

    private static void set(byte[] levels, byte[] overBlack, int i, int gray, int alpha,
                            boolean premultiplied) {
        int black = premultiplied ? gray : gray * alpha / 255;
        if (overBlack == null) {
            levels[i] = (byte) gray;
            return;
        }
        levels[i] = (byte) Math.min(255, black + 255 - alpha);
        overBlack[i] = (byte) black;
    }
}
