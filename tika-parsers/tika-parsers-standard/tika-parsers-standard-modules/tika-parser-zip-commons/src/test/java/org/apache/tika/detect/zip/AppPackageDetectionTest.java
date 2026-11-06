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
package org.apache.tika.detect.zip;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.junit.jupiter.api.Test;

import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;

public class AppPackageDetectionTest {

    private static final String SCORE = "<?xml version=\"1.0\"?><score-partwise version=\"4.0\">";

    // name=content entries; a leading "!" stores the entry uncompressed
    private static String detect(String... names) throws Exception {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(bos)) {
            for (String spec : names) {
                boolean stored = spec.startsWith("!");
                String[] kv = (stored ? spec.substring(1) : spec).split("=", 2);
                byte[] content = (kv.length > 1 ? kv[1] : "x").getBytes(StandardCharsets.UTF_8);
                ZipEntry entry = new ZipEntry(kv[0]);
                if (stored) {
                    CRC32 crc = new CRC32();
                    crc.update(content);
                    entry.setMethod(ZipEntry.STORED);
                    entry.setSize(content.length);
                    entry.setCrc(crc.getValue());
                }
                zos.putNextEntry(entry);
                zos.write(content);
                zos.closeEntry();
            }
        }
        try (TikaInputStream tis = TikaInputStream.get(bos.toByteArray())) {
            return new DefaultZipContainerDetector().detect(tis, new Metadata(), new ParseContext())
                    .toString();
        }
    }

    @Test
    public void testAppPackages() throws Exception {
        assertEquals("model/vnd.usdz+zip", detect("!model.usdc", "0/baseColor.jpg"));
        assertEquals("application/x-musescore",
                detect("META-INF/container.xml", "Song.mscx", "Thumbnails/thumbnail.png"));
        assertEquals("application/vnd.recordare.musicxml",
                detect("META-INF/container.xml", "score.xml=" + SCORE));
        assertEquals("application/x-vnd.corel.draw.document+zip",
                detect("content/riffData.cdr", "metadata/metadata.xml"));
        assertEquals("application/x-powerbi",
                detect("Version", "[Content_Types].xml", "DiagramLayout", "Report/Layout"));
        assertEquals("application/x-extension-fcstd",
                detect("Document.xml", "PartShape.brp", "GuiDocument.xml"));
        assertEquals("application/x-smarttech-notebook",
                detect("annotationmetadata/metadata.xml", "settings.xml", "imsmanifest.xml",
                        "metadata.xml", "page0.svg"));
        assertEquals("application/x-anki-package", detect("collection.anki2", "media"));
        assertEquals("application/x-fritzing-bundle",
                detect("part.x_1.fzp", "sketch.fz", "svg.icon.x_icon.svg"));
        assertEquals("application/x-scratch-project", detect("project.json={\"objName\": \"Stage\"}", "0.png", "1.wav"));
        assertEquals("application/x-scratch-project",
                detect("project.json={\"targets\": []}", "83a9787d4cb6f3b7632b4ddfebf74367.wav"));
        assertEquals("application/x-lego-lxf", detect("IMAGE100.PNG", "IMAGE100.LXFML"));
        assertEquals("application/x-appinventor-project",
                detect("src/appinventor/ai_x/App/Screen1.scm", "youngandroidproject/project.properties"));
    }

    @Test
    public void testLookalikesStayGeneric() throws Exception {
        // EPUB without a mimetype entry: container.xml points at an OPF
        assertEquals("application/zip",
                detect("META-INF/container.xml", "content.opf", "toc.xml"));
        assertEquals("application/zip", detect("project.json", "README.md", "src/index.js"));
        assertEquals("application/zip", detect("project.json"));
        assertEquals("application/zip", detect("Document.xml", "notes.txt"));
        assertEquals("application/zip", detect("imsmanifest.xml", "settings.xml", "metadata.xml"));
        assertEquals("application/zip", detect("!docs/model.usdc"));
        // USDZ forbids compression
        assertEquals("application/zip", detect("model.usdc", "0/baseColor.jpg"));
        // InDesign IDML without its mimetype entry
        assertEquals("application/zip",
                detect("META-INF/container.xml", "designmap.xml=<?xml version=\"1.0\"?><Document/>"));
        assertEquals("application/zip", detect("project.json={\"name\": \"x\"}", "0.png"));
    }
}
