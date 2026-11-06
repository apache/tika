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
package org.apache.tika.detect;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.zip.GZIPOutputStream;

import com.github.luben.zstd.Zstd;
import org.junit.jupiter.api.Test;

import org.apache.tika.TikaTest;
import org.apache.tika.detect.blender.BlenderDetector;
import org.apache.tika.detect.gzip.GZipSpecializationDetector;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.mime.MediaTypeRegistry;
import org.apache.tika.parser.ParseContext;

/**
 * Compressed Blender files with every standard detector: gzip ones are recognized by
 * GZipSpecializationDetector, zstd ones by BlenderDetector, whatever order they run in.
 */
public class BlenderDetectionTest extends TikaTest {

    @Test
    public void testDefaultDetector() throws Exception {
        byte[] blend = blend();
        for (byte[] file : new byte[][]{blend, gzip(blend), Zstd.compress(blend)}) {
            assertEquals("application/x-blender", detect(new DefaultDetector(), file));
        }
        byte[] other = "BLENDEX and more".getBytes(StandardCharsets.US_ASCII);
        assertEquals("application/gzip", detect(new DefaultDetector(), gzip(other)));
        assertEquals("application/zstd", detect(new DefaultDetector(), Zstd.compress(other)));
    }

    @Test
    public void testDetectorOrder() throws Exception {
        MediaTypeRegistry registry = MediaTypeRegistry.getDefaultRegistry();
        Detector gzipDetector = new GZipSpecializationDetector();
        Detector blenderDetector = new BlenderDetector();
        byte[] blend = blend();
        for (List<Detector> order : List.of(List.of(gzipDetector, blenderDetector),
                List.of(blenderDetector, gzipDetector))) {
            for (byte[] file : new byte[][]{gzip(blend), Zstd.compress(blend)}) {
                assertEquals("application/x-blender",
                        detect(new CompositeDetector(registry, order), file));
            }
        }
    }

    @Test
    public void testCompressedFilesYieldTheirPreview() throws Exception {
        byte[] blend = blend();
        for (byte[] file : new byte[][]{gzip(blend), Zstd.compress(blend)}) {
            List<Metadata> list;
            try (TikaInputStream tis = TikaInputStream.get(file)) {
                list = getRecursiveMetadata(tis, true);
            }
            assertEquals(2, list.size());
            assertEquals(TikaCoreProperties.EmbeddedResourceType.THUMBNAIL.toString(),
                    list.get(1).get(TikaCoreProperties.EMBEDDED_RESOURCE_TYPE));
        }
    }

    private static String detect(Detector detector, byte[] file) throws IOException {
        try (TikaInputStream tis = TikaInputStream.get(file)) {
            return detector.detect(tis, new Metadata(), new ParseContext()).toString();
        }
    }

    /** A blend file of Blender 4: header, REND, a 2x1 preview in TEST, ENDB. */
    private static byte[] blend() {
        ByteBuffer out = ByteBuffer.allocate(256).order(ByteOrder.LITTLE_ENDIAN);
        out.put("BLENDER-v402".getBytes(StandardCharsets.US_ASCII));
        block(out, "REND", new byte[72]);
        ByteBuffer preview = ByteBuffer.allocate(8 + 2 * 4).order(ByteOrder.LITTLE_ENDIAN);
        preview.putInt(2).putInt(1).putInt(0xff0000ff).putInt(0xff00ff00);
        block(out, "TEST", preview.array());
        block(out, "ENDB", new byte[0]);
        return Arrays.copyOf(out.array(), out.position());
    }

    private static void block(ByteBuffer out, String code, byte[] data) {
        out.put(code.getBytes(StandardCharsets.US_ASCII)).putInt(data.length).putLong(0)
                .putInt(0).putInt(1).put(data);
    }

    private static byte[] gzip(byte[] data) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (GZIPOutputStream gz = new GZIPOutputStream(bytes)) {
            gz.write(data);
        }
        return bytes.toByteArray();
    }
}
