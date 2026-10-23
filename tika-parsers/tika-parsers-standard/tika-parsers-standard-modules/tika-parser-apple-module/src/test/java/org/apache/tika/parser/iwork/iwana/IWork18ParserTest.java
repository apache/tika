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
package org.apache.tika.parser.iwork.iwana;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;
import javax.imageio.ImageIO;

import org.junit.jupiter.api.Test;

import org.apache.tika.TikaTest;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.HttpHeaders;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.parser.ParseContext;

public class IWork18ParserTest extends TikaTest {

    /**
     * The package's preview.jpg is the document's THUMBNAIL embedded
     * document. The test fixture was saved without one, so a preview is
     * added to a copy of it.
     */
    @Test
    public void testPreviewIsTheThumbnail() throws Exception {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (InputStream is = getResourceAsStream("/test-documents/testKeynote2018.key");
             ZipInputStream in = new ZipInputStream(is);
             ZipOutputStream out = new ZipOutputStream(bos)) {
            ZipEntry entry;
            while ((entry = in.getNextEntry()) != null) {
                out.putNextEntry(new ZipEntry(entry.getName()));
                in.transferTo(out);
                out.closeEntry();
            }
            out.putNextEntry(new ZipEntry("Presentation.key/preview.jpg"));
            out.write(jpeg());
            out.closeEntry();
        }
        List<Metadata> metadataList;
        try (TikaInputStream tis = TikaInputStream.get(bos.toByteArray())) {
            metadataList = getRecursiveMetadata(tis, new IWork18PackageParser(), new Metadata(),
                    new ParseContext(), false);
        }
        assertEquals(2, metadataList.size());
        assertEquals("application/vnd.apple.keynote.18",
                metadataList.get(0).get(HttpHeaders.CONTENT_TYPE));
        Metadata thumbnail = metadataList.get(1);
        assertEquals("Presentation.key/preview.jpg",
                thumbnail.get(TikaCoreProperties.RESOURCE_NAME_KEY));
        assertEquals(TikaCoreProperties.EmbeddedResourceType.THUMBNAIL.toString(),
                thumbnail.get(TikaCoreProperties.EMBEDDED_RESOURCE_TYPE));
    }

    /**
     * A small but well-formed JPEG, so the preview parses like a real one.
     */
    private static byte[] jpeg() throws Exception {
        BufferedImage image = new BufferedImage(8, 8, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        assertTrue(ImageIO.write(image, "jpeg", bos), "no JPEG writer available");
        return bos.toByteArray();
    }

    /**
     * Without a preview the parser still detects the type and emits no
     * embedded document.
     */
    @Test
    public void testNoPreview() throws Exception {
        List<Metadata> metadataList;
        try (TikaInputStream tis = TikaInputStream.get(
                getResourceAsStream("/test-documents/testKeynote2018.key"))) {
            metadataList = getRecursiveMetadata(tis, new IWork18PackageParser(), new Metadata(),
                    new ParseContext(), false);
        }
        assertEquals(1, metadataList.size());
        assertEquals("application/vnd.apple.keynote.18",
                metadataList.get(0).get(HttpHeaders.CONTENT_TYPE));
    }
}
