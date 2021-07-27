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

package org.apache.tika.parser.pkg;


import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import org.apache.tika.TikaTest;
import org.apache.tika.config.TikaConfig;
import org.apache.tika.detect.Detector;
import org.apache.tika.detect.zip.DefaultZipContainerDetector;
import org.apache.tika.detect.zip.DeprecatedStreamingZipContainerDetector;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;
import org.apache.tika.mime.MediaTypeRegistry;

public class CompositeZipContainerDetectorTest extends TikaTest {
    private static MediaType ODT_TEXT = MediaType.application("vnd.oasis.opendocument.text");
    private static MediaType TIFF = MediaType.image("tiff");
    DefaultZipContainerDetector compositeZipContainerDetector = new DefaultZipContainerDetector();
    DeprecatedStreamingZipContainerDetector streamingZipDetector =
            new DeprecatedStreamingZipContainerDetector();

    @Test
    public void testTiffWorkaround() throws Exception {
        //TIKA-2591
        Metadata metadata = new Metadata();
        try (InputStream is = TikaInputStream
                .get(getResourceAsStream("/test-documents/testTIFF.tif"))) {
            MediaType mt = compositeZipContainerDetector.detect(is, metadata);
            assertEquals(TIFF, mt);
        }
        metadata = new Metadata();
        try (InputStream is = TikaInputStream
                .get(getResourceAsStream("/test-documents/testTIFF_multipage.tif"))) {
            MediaType mt = compositeZipContainerDetector.detect(is, metadata);
            assertEquals(TIFF, mt);
        }
    }

    /* TODO these tests!

        @Test
        public void testODT() throws Exception {
            try (InputStream input = ODFParserTest.class.getResourceAsStream(
                    "/test-documents/testODFwithOOo3.odt")) {
                Metadata metadata = new Metadata();
                MediaType mt = zipContainerDetector.detect(input, metadata);
                assertEquals(ODT_TEXT, mt);
            }
        }

        @Test
        public void testIWorks() throws Exception {
            //have to have marklimit in ZipContainerDetector > 100000 for this to work
            try (InputStream input = ODFParserTest.class.getResourceAsStream(
                    "/test-documents/testPages.pages")) {
                Metadata metadata = new Metadata();
                MediaType mt = zipContainerDetector.detect(input, metadata);
                assertEquals("application/vnd.apple.pages", mt.toString());
            }

            InputStream is =
            getClass().getResourceAsStream("/org/apache/tika/parser/pkg/tika-config.xml");
            assertNotNull(is);
            TikaConfig tikaConfig = new TikaConfig(is);
            try (InputStream input = ODFParserTest.class.getResourceAsStream(
                    "/test-documents/testPages.pages")) {
                Metadata metadata = new Metadata();
                MediaType mt = tikaConfig.getDetector().detect(input, metadata);
                assertEquals("application/zip", mt.toString());
            }
        }

        @Test
        public void testXPS() throws Exception {
            for (String file : new String[]{"testXPS_various.xps", "testPPT.xps"}) {
                long start = System.currentTimeMillis();
                try (InputStream input = ODFParserTest.class.getResourceAsStream(
                        "/test-documents/" + file)) {
                    MediaType mediaType = streamingZipDetector.detect(input, new Metadata());
                    assertEquals(ZipContainerDetectorBase.XPS, mediaType);
                }
                try (TikaInputStream input = TikaInputStream.get(
                Paths.get(ODFParserTest.class.getResource(
                        "/test-documents/" + file).toURI()))) {
                    MediaType mediaType = zipContainerDetector.detect(input, new Metadata());
                    assertEquals(ZipContainerDetectorBase.XPS, mediaType);
                }
            }
        }
    */

    @Disabled("for offline testing")
    @Test
    public void timeDetection() throws Exception {
        TikaConfig config = TikaConfig.getDefaultConfig();
        Detector detector = config.getDetector();
        MediaTypeRegistry registry = config.getMediaTypeRegistry();
        List<File> zips = getTestZipBasedFiles(detector, registry);

        Set<MediaType> mediaTypeSet = new HashSet<>();
        long nonTikaStream = 0;
        long tikaStream = 0;
        long tikaStreamWFile = 0;
        for (int i = 0; i < 20; i++) {
            for (File z : zips) {
                long start = System.currentTimeMillis();
                try (InputStream is = new BufferedInputStream(new FileInputStream(z))) {
                    MediaType mt = detector.detect(is, new Metadata());
                    mediaTypeSet.add(mt);
                }
                nonTikaStream += System.currentTimeMillis() - start;

                start = System.currentTimeMillis();
                try (InputStream is = TikaInputStream
                        .get(new BufferedInputStream(new FileInputStream(z)))) {
                    MediaType mt = detector.detect(is, new Metadata());
                    mediaTypeSet.add(mt);
                }
                tikaStream += System.currentTimeMillis() - start;

                start = System.currentTimeMillis();
                try (InputStream is = TikaInputStream.get(z)) {
                    MediaType mt = detector.detect(is, new Metadata());
                    mediaTypeSet.add(mt);
                }
                tikaStreamWFile += System.currentTimeMillis() - start;
            }
        }
        System.out.println(
                "tika stream: " + tikaStream + "\ntika stream w file: " + tikaStreamWFile +
                        "\nnon tika stream:" + nonTikaStream);
    }

    @Test
    @Disabled("to be used for offline timing tests")
    public void timeParsing() throws Exception {
        TikaConfig config = TikaConfig.getDefaultConfig();
        Detector detector = config.getDetector();
        MediaTypeRegistry registry = config.getMediaTypeRegistry();

        List<File> zips = getTestZipBasedFiles(detector, registry);
        System.out.println("zips size: " + zips.size());
        Set<MediaType> mediaTypeSet = new HashSet<>();
        long nonTikaStream = 0;
        long tikaStream = 0;
        long tikaStreamWFile = 0;
        for (int i = 0; i < 10; i++) {
            for (File z : zips) {
                long start = System.currentTimeMillis();
                try (InputStream is = new BufferedInputStream(new FileInputStream(z))) {
                    getRecursiveMetadata(is, true);
                }
                nonTikaStream += System.currentTimeMillis() - start;
                start = System.currentTimeMillis();
                try (InputStream is = TikaInputStream
                        .get(new BufferedInputStream(new FileInputStream(z)))) {
                    getRecursiveMetadata(is, true);
                }
                tikaStream += System.currentTimeMillis() - start;
                start = System.currentTimeMillis();
                try (InputStream is = TikaInputStream.get(z)) {
                    getRecursiveMetadata(is, true);
                }
                tikaStreamWFile += System.currentTimeMillis() - start;

            }
        }
        System.out.println(
                "tika stream: " + tikaStream + "\ntika stream w file: " + tikaStreamWFile +
                        "\nnon tika stream:" + nonTikaStream);
    }

    //TODO -- we need to find a dwg+xps file for testing

    private List<File> getTestZipBasedFiles(Detector detector, MediaTypeRegistry registry)
            throws Exception {
        List<File> zips = new ArrayList<>();
        for (File f : Paths.get(getResourceAsUri("/test-documents")).toFile().listFiles()) {
            try (InputStream is = TikaInputStream.get(f)) {
                MediaType mt = detector.detect(is, new Metadata());
                if (registry.isSpecializationOf(mt, MediaType.APPLICATION_ZIP)) {
                    zips.add(f);
                }
            } catch (Exception e) {
                //swallow
            }
        }
        return zips;
    }
}
