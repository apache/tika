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
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.junit.jupiter.api.Test;

import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;

public class XPIDetectionTest {

    private static String detect(String... names) throws Exception {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(bos)) {
            for (String name : names) {
                zos.putNextEntry(new ZipEntry(name));
                zos.write("x".getBytes(StandardCharsets.US_ASCII));
                zos.closeEntry();
            }
        }
        try (TikaInputStream tis = TikaInputStream.get(bos.toByteArray())) {
            return new DefaultZipContainerDetector().detect(tis, new Metadata(), new ParseContext())
                    .toString();
        }
    }

    @Test
    public void testMozillaAddOns() throws Exception {
        assertEquals("application/x-xpinstall", detect("install.rdf", "chrome.manifest"));
        assertEquals("application/x-xpinstall",
                detect("manifest.json", "META-INF/manifest.mf", "META-INF/mozilla.rsa"));
        // unsigned WebExtensions are shared with Chromium
        assertEquals("application/zip", detect("manifest.json", "background.js"));
        assertEquals("application/zip", detect("addon/install.rdf"));
    }
}
