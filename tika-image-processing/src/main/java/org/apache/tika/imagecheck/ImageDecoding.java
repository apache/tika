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
package org.apache.tika.imagecheck;

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

    /** Gray levels 0..255 of a decoded copy, row by row. */
    record Gray(byte[] levels, int width, int height) {
    }

    /**
     * A gray copy of the image of at most {@link #MAX_SIDE} pixels on a side, or null when it
     * cannot be read or is too large to decode.
     */
    static Gray gray(ImageProbe image) throws IOException {
        return image.memo("imagecheck:gray", () -> {
            long[] size = size(image);
            if (size[0] <= 0 || size[1] <= 0 || size[0] * size[1] > MAX_DECODE_PIXELS) {
                return null;
            }
            return decode(image.bytes());
        });
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
        byte[] levels = new byte[width * height];
        if (image.getColorModel() instanceof IndexColorModel) {
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    int argb = image.getRGB(x, y);
                    levels[y * width + x] = (byte) ((((argb >> 16) & 255) * 299
                            + ((argb >> 8) & 255) * 587 + (argb & 255) * 114) / 1000);
                }
            }
            return new Gray(levels, width, height);
        }
        Raster raster = image.getRaster();
        int bands = raster.getNumBands();
        boolean sixteenBit = raster.getSampleModel().getSampleSize(0) > 8;
        int[] sample = new int[bands];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                raster.getPixel(x, y, sample);
                int gray = bands >= 3
                        ? (sample[0] * 299 + sample[1] * 587 + sample[2] * 114) / 1000
                        : sample[0];
                levels[y * width + x] = (byte) (sixteenBit ? gray >> 8 : gray);
            }
        }
        return new Gray(levels, width, height);
    }
}
