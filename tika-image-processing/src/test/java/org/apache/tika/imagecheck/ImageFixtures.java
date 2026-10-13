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

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import javax.imageio.ImageIO;

import org.apache.tika.io.TikaInputStream;
import org.apache.tika.parser.enricher.ImageProbe;

final class ImageFixtures {

    private ImageFixtures() {
    }

    /** A white image, with a black block over {@code inkFraction} of its width when positive. */
    static byte[] png(int width, int height, double inkFraction) throws IOException {
        return png(width, height, inkFraction, Color.WHITE);
    }

    static byte[] png(int width, int height, double inkFraction, Color background)
            throws IOException {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setColor(background);
        g.fillRect(0, 0, width, height);
        if (inkFraction > 0) {
            g.setColor(Color.BLACK);
            g.fillRect(0, 0, (int) Math.max(1, Math.round(width * inkFraction)), height);
        }
        g.dispose();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return out.toByteArray();
    }

    static ImageProbe probe(byte[] bytes) {
        return new ImageProbe(TikaInputStream.get(bytes), new org.apache.tika.metadata.Metadata());
    }
}
