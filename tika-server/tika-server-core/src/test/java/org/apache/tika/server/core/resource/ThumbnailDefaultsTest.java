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
package org.apache.tika.server.core.resource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import org.apache.tika.config.loader.TikaJsonConfig;
import org.apache.tika.parser.ParseContext;

public class ThumbnailDefaultsTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    public void testBuiltIn() throws Exception {
        ParseContext context = new ParseContext();
        ThumbnailDefaults.builtIn().applyTo(context);
        JsonNode pdf = json(context, "pdf-parser");
        assertEquals("RENDER_PAGES_AT_PAGE_END", pdf.get("imageStrategy").asText());
        assertEquals(1, pdf.get("maxRenderedPages").asInt());
        assertEquals(96, pdf.get("ocr").get("dpi").asInt());
        //a thumbnail is in colour; the renderer defaults to the grayscale OCR wants
        assertEquals("RGB", pdf.get("ocr").get("imageType").asText());
        //no OCR setting: the indexing request keeps whatever the server does
        assertNull(pdf.get("ocr").get("strategy"));
        assertEquals("THUMBNAIL",
                json(context, "emf-parser").get("renderOnlyEmbeddedResourceTypes").get(0).asText());
        assertEquals(true, json(context, "wmf-parser").get("renderImage").asBoolean());
    }

    @Test
    public void testServerConfigReplacesAComponent() throws Exception {
        TikaJsonConfig config = config("{\"thumbnail-defaults\": {\"pdf-parser\": "
                + "{\"imageStrategy\": \"RENDER_PAGES_AT_PAGE_END\", \"maxRenderedPages\": 1, "
                + "\"ocr\": {\"dpi\": 150}}}}");
        ParseContext context = new ParseContext();
        ThumbnailDefaults.fromConfig(config).applyTo(context);
        assertEquals(150, json(context, "pdf-parser").get("ocr").get("dpi").asInt());
        //the components the config does not mention keep the built-in defaults
        assertEquals(true, json(context, "emf-parser").get("renderImage").asBoolean());
    }

    @Test
    public void testNoConfigBlockMeansBuiltIn() throws Exception {
        ParseContext context = new ParseContext();
        ThumbnailDefaults.fromConfig(config("{\"parsers\": []}")).applyTo(context);
        assertEquals(96, json(context, "pdf-parser").get("ocr").get("dpi").asInt());
    }

    @Test
    public void testRequestConfigWins() throws Exception {
        ParseContext context = new ParseContext();
        context.setJsonConfig("pdf-parser", "{\"imageStrategy\": \"NONE\"}");
        ThumbnailDefaults.builtIn().applyTo(context);
        assertEquals("NONE", json(context, "pdf-parser").get("imageStrategy").asText());
        assertEquals(true, json(context, "emf-parser").get("renderImage").asBoolean());
    }

    @Test
    public void testWithMergesFieldByField() throws Exception {
        ParseContext context = new ParseContext();
        ThumbnailDefaults.builtIn()
                .with("{\"pdf-parser\": {\"ocr\": {\"strategy\": \"NO_OCR\"}}, "
                        + "\"tesseract-ocr-parser\": {\"skipOcr\": true}}")
                .applyTo(context);
        JsonNode pdf = json(context, "pdf-parser");
        assertEquals("NO_OCR", pdf.get("ocr").get("strategy").asText());
        assertEquals(96, pdf.get("ocr").get("dpi").asInt());
        assertEquals(1, pdf.get("maxRenderedPages").asInt());
        assertEquals(true, json(context, "tesseract-ocr-parser").get("skipOcr").asBoolean());
        //the original is untouched
        assertNull(MAPPER.readTree(ThumbnailDefaults.builtIn().get("pdf-parser"))
                .get("ocr").get("strategy"));
    }

    @Test
    public void testMalformedBlockIsRejected() throws Exception {
        TikaJsonConfig config = config("{\"thumbnail-defaults\": {\"pdf-parser\": \"yes\"}}");
        assertThrows(IllegalArgumentException.class, () -> ThumbnailDefaults.fromConfig(config));
    }

    private static TikaJsonConfig config(String json) throws Exception {
        return TikaJsonConfig.load(new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)));
    }

    private static JsonNode json(ParseContext context, String component) throws Exception {
        return MAPPER.readTree(context.getJsonConfig(component).json());
    }
}
