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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.xml.sax.helpers.DefaultHandler;

import org.apache.tika.config.loader.TikaLoader;
import org.apache.tika.exception.TikaConfigException;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.parser.enricher.CompositeContentEnricher;
import org.apache.tika.parser.enricher.GatedEnricher;
import org.apache.tika.parser.mock.MockEnricher;

/** The checks as a deployment lists them: by name under a "text-recognizers" entry. */
public class ImageChecksConfigTest {

    @TempDir
    Path tmp;

    private Parser engine(String entry) throws Exception {
        Path config = tmp.resolve("tika-config.json");
        Files.writeString(config, "{ \"engines\": { \"png\": { \"mock-enricher\": {} } },"
                + " \"text-recognizers\": [ " + entry + " ] }");
        List<Parser> matched = TikaLoader.load(config).get(CompositeContentEnricher.class)
                .getEnrichers(MediaType.image("png"));
        assertEquals(1, matched.size());
        return matched.get(0);
    }

    private static Metadata read(Parser engine, byte[] image) throws Exception {
        Metadata metadata = new Metadata();
        try (TikaInputStream tis = TikaInputStream.get(image)) {
            engine.parse(tis, new DefaultHandler(), metadata, new ParseContext());
        }
        return metadata;
    }

    @Test
    public void testNamedChecksWrapTheEngineOnce() throws Exception {
        Parser engine = engine("{ \"engine\": \"png\", \"_min-width\": 8, \"checks\": ["
                + " {\"min-image-size\": {}}, {\"blank-image-check\": {\"maxInk\": 0.001}} ] }");
        GatedEnricher gate = (GatedEnricher) engine;
        // the recorded-size minimum, then the two named checks, in one decorator
        assertEquals(3, gate.getChecks().size());
        assertTrue(gate.getChecks().get(1) instanceof MinImageSizeCheck);
        assertTrue(gate.getChecks().get(2) instanceof BlankImageCheck);
    }

    @Test
    public void testABlankImageNeverReachesTheEngine() throws Exception {
        Parser engine = engine("{ \"engine\": \"png\", \"checks\": [ \"blank-image-check\" ] }");
        Metadata m = read(engine, ImageFixtures.png(200, 200, 0));
        assertNull(m.get(MockEnricher.MARKER_KEY));
        assertEquals("skipped:blank", m.get(TikaCoreProperties.OCR_WARNINGS));
    }

    @Test
    public void testAnImageWithInkReachesTheEngine() throws Exception {
        Parser engine = engine("{ \"engine\": \"png\", \"checks\": ["
                + " {\"min-image-size\": {}}, {\"blank-image-check\": {}} ] }");
        Metadata m = read(engine, ImageFixtures.png(200, 200, 0.2));
        assertEquals("ENRICHED", m.get(MockEnricher.MARKER_KEY));
        assertNull(m.get(TikaCoreProperties.OCR_WARNINGS));
    }

    @Test
    public void testChecksRunInTheOrderListedAndTheFirstRefusalWins() throws Exception {
        // a blank 20x20 image is too small and blank; the order decides the reason
        byte[] tiny = ImageFixtures.png(20, 20, 0);
        Parser sizeFirst = engine("{ \"engine\": \"png\", \"checks\": ["
                + " \"min-image-size\", \"blank-image-check\" ] }");
        assertEquals("skipped:image-too-small",
                read(sizeFirst, tiny).get(TikaCoreProperties.OCR_WARNINGS));
        Parser blankFirst = engine("{ \"engine\": \"png\", \"checks\": ["
                + " \"blank-image-check\", \"min-image-size\" ] }");
        assertEquals("skipped:blank",
                read(blankFirst, tiny).get(TikaCoreProperties.OCR_WARNINGS));
    }

    @Test
    public void testTheInlineFormTakesChecksToo() throws Exception {
        Parser engine = engine("{ \"mock-enricher\": { \"checks\": [ \"blank-image-check\" ] } }");
        assertEquals("skipped:blank", read(engine, ImageFixtures.png(200, 200, 0))
                .get(TikaCoreProperties.OCR_WARNINGS));
    }

    @Test
    public void testSettingsAreBoundToTheCheck() throws Exception {
        Parser engine = engine("{ \"engine\": \"png\", \"checks\": ["
                + " {\"min-image-size\": {\"minWidthPixels\": 300, \"minHeightPixels\": 0, \"minTotalPixels\": 0}} ] }");
        assertEquals("skipped:image-too-small", read(engine, ImageFixtures.png(200, 200, 0.5))
                .get(TikaCoreProperties.OCR_WARNINGS));
        assertEquals("ENRICHED", read(engine, ImageFixtures.png(400, 50, 0.5))
                .get(MockEnricher.MARKER_KEY));
    }

    @Test
    public void testByteSizeCheckByName() throws Exception {
        Parser engine = engine("{ \"engine\": \"png\", \"checks\": ["
                + " {\"byte-size\": {\"minBytes\": 1000000}} ] }");
        assertEquals("skipped:file-too-small", read(engine, ImageFixtures.png(200, 200, 0.5))
                .get(TikaCoreProperties.OCR_WARNINGS));
    }

    @Test
    public void testAnUnknownCheckFailsLoad() {
        assertThrows(TikaConfigException.class, () -> engine("{ \"engine\": \"png\","
                + " \"checks\": [ \"no-such-check\" ] }"));
    }

    @Test
    public void testChecksMustBeAnArray() {
        TikaConfigException e = assertThrows(TikaConfigException.class, () -> engine(
                "{ \"engine\": \"png\", \"checks\": \"blank-image-check\" }"));
        assertTrue(e.getMessage().contains("must be an array"), e.getMessage());
    }
}
