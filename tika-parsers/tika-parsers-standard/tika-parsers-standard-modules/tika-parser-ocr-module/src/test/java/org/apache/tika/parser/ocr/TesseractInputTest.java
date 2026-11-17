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
package org.apache.tika.parser.ocr;

import static java.nio.charset.StandardCharsets.ISO_8859_1;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Paths;

import org.junit.jupiter.api.Test;

import org.apache.tika.TikaTest;

public class TesseractInputTest extends TikaTest {

    private static final String PAD = "\0\0\0\0\0\0\0\0\0\0\0\0";

    @Test
    public void acceptsImages() throws Exception {
        for (String name : new String[]{"testOCR_spacing.png", "testOCR.jpg", "testTIFF_multipage.tif",
                "testRotated+10.png"}) {
            assertTrue(TesseractInput.isAccepted(
                    Paths.get(getResourceAsUri("/test-documents/" + name))));
        }
    }

    @Test
    public void acceptsSignatures() throws Exception {
        for (String head : new String[]{
                "\u0089PNG\r\n\u001a\n\0\0\0\rIHDR",
                "\u00ff\u00d8\u00ff\u00e0\0\u0010JFIF\0\u0001",
                "II*\0\b\0\0\0\0\0\0\0",
                "MM\0*\0\0\0\b\0\0\0\0",
                "II+\0\b\0\0\0\0\0\0\0",
                "MM\0+\0\b\0\0\0\0\0\0",
                "BM\u0010\u0010\0\0\0\0\0\0\0\0",
                "GIF89a\u0010\0\u0010\0\0\0",
                "P6\n16 16\n255\n",
                "P1 # ok\n1 1\n1\n\n",
                "\0\0\0\fjP  \r\n\u0087\n"}) {
            assertTrue(accepted(head), head);
        }
    }

    @Test
    public void rejects() throws Exception {
        for (String head : new String[]{
                "plain text\n" + PAD,
                "short\n" + PAD,
                "",
                "BM\n",
                "\u00ff\u00d8 plain text\n",
                "GIF88a" + PAD,
                "P6text text\n",
                "P7\n" + PAD,
                "RIFF\0\0\0\0WEBP",
                "%PDF-1.7\n1 0 obj\n",
                "%!PS-Adobe-3.0\n",
                "spix" + PAD}) {
            assertFalse(accepted(head), head);
        }
    }

    private static boolean accepted(String head) {
        byte[] bytes = head.getBytes(ISO_8859_1);
        byte[] buffer = new byte[Math.max(bytes.length, 12)];
        System.arraycopy(bytes, 0, buffer, 0, bytes.length);
        return TesseractInput.format(buffer, bytes.length) != null;
    }
}
