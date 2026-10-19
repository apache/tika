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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;

import org.junit.jupiter.api.Test;

public class ImageDecodingTest {

    private static byte[] jpeg(int width, int height, boolean progressive) throws Exception {
        BufferedImage image = ImageFixtures.image(BufferedImage.TYPE_3BYTE_BGR, width, height,
                0xFFFFFFFF, 0xFF000000, 0.1);
        ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
        ImageWriteParam param = writer.getDefaultWriteParam();
        if (progressive) {
            param.setProgressiveMode(ImageWriteParam.MODE_DEFAULT);
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ImageOutputStream ios = ImageIO.createImageOutputStream(out)) {
            writer.setOutput(ios);
            writer.write(null, new IIOImage(image, null, null), param);
        } finally {
            writer.dispose();
        }
        return out.toByteArray();
    }

    /** The jpeg with its frame header claiming {@code width} x {@code height}. */
    private static byte[] claimSize(byte[] jpeg, int width, int height) {
        byte[] patched = jpeg.clone();
        for (int i = 2; i + 8 < patched.length; i++) {
            int marker = patched[i + 1] & 255;
            if ((patched[i] & 255) == 0xFF && (marker == 0xC0 || marker == 0xC2)) {
                patched[i + 5] = (byte) (height >> 8);
                patched[i + 6] = (byte) height;
                patched[i + 7] = (byte) (width >> 8);
                patched[i + 8] = (byte) width;
                return patched;
            }
        }
        throw new IllegalStateException("no frame header");
    }

    @Test
    public void testProgressiveJpegsAreRecognized() throws Exception {
        assertTrue(ImageDecoding.mayBeProgressiveJpeg(jpeg(64, 64, true)));
        assertFalse(ImageDecoding.mayBeProgressiveJpeg(jpeg(64, 64, false)));
        assertFalse(ImageDecoding.mayBeProgressiveJpeg(ImageFixtures.png(64, 64, 0.1)));
        // the frame header is past what was read: assume the worst
        assertTrue(ImageDecoding.mayBeProgressiveJpeg(new byte[] {(byte) 0xFF, (byte) 0xD8,
                (byte) 0xFF, (byte) 0xE1, 0x10, 0x00, 0, 0}));
    }

    @Test
    public void testALargeProgressiveJpegIsNotDecoded() throws Exception {
        byte[] large = claimSize(jpeg(64, 64, true), 5000, 5000);
        assertArrayEquals(new long[] {5000, 5000}, ImageDecoding.size(ImageFixtures.probe(large)));
        assertNull(ImageDecoding.gray(ImageFixtures.probe(large)));
        assertNotNull(ImageDecoding.gray(ImageFixtures.probe(jpeg(64, 64, true))));
    }
}
