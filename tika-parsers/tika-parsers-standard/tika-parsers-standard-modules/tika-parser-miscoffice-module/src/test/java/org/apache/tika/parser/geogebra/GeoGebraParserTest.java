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
package org.apache.tika.parser.geogebra;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import org.apache.tika.TikaTest;
import org.apache.tika.metadata.HttpHeaders;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.PagedText;
import org.apache.tika.metadata.TikaCoreProperties;

public class GeoGebraParserTest extends TikaTest {

    @Test
    public void testGGB() throws Exception {
        List<Metadata> metadataList = getRecursiveMetadata("testGeoGebra.ggb");
        Metadata metadata = metadataList.get(0);
        assertEquals("application/vnd.geogebra.file", metadata.get(HttpHeaders.CONTENT_TYPE));
        assertEquals("Pythagorean theorem", metadata.get(TikaCoreProperties.TITLE));
        assertEquals("Ada Lovelace", metadata.get(TikaCoreProperties.CREATOR));
        assertEquals("15 January 2026", metadata.get(GeoGebraParser.DATE));
        assertEquals("classic", metadata.get(GeoGebraParser.APP_NAME));
        assertEquals("5.0.815.0", metadata.get(GeoGebraParser.APP_VERSION));
        assertEquals("5.0", metadata.get(GeoGebraParser.FORMAT_VERSION));
        assertEquals("0c34397e-e3e1-4d1c-9cb6-fe6e54b1e88f", metadata.get(GeoGebraParser.ID));

        String content = metadata.get(TikaCoreProperties.TIKA_CONTENT);
        assertContains("In a right triangle a² + b² = c²", content);
        assertContains("Theorem statement", content);
        assertContains("Drag the vertices to explore.", content);

        //the thumbnail is emitted as an embedded document marked THUMBNAIL
        assertEquals(2, metadataList.size());
        Metadata thumbnail = metadataList.get(1);
        assertEquals("image/png", thumbnail.get(HttpHeaders.CONTENT_TYPE));
        assertEquals("geogebra_thumbnail.png", thumbnail.get(TikaCoreProperties.RESOURCE_NAME_KEY));
        assertEquals(TikaCoreProperties.EmbeddedResourceType.THUMBNAIL.toString(),
                thumbnail.get(TikaCoreProperties.EMBEDDED_RESOURCE_TYPE));
    }

    @Test
    public void testGGS() throws Exception {
        List<Metadata> metadataList = getRecursiveMetadata("testGeoGebraSlides.ggs");
        Metadata metadata = metadataList.get(0);
        assertEquals("application/vnd.geogebra.slides", metadata.get(HttpHeaders.CONTENT_TYPE));
        assertEquals("notes", metadata.get(GeoGebraParser.APP_NAME));
        assertEquals(2, (int) metadata.getInt(PagedText.N_PAGES));

        //structure.json orders _slide1 before _slide0
        String content = metadata.get(TikaCoreProperties.TIKA_CONTENT);
        assertContains("First slide text", content);
        assertContains("Second slide text", content);
        assertTrue(content.indexOf("First slide text") < content.indexOf("Second slide text"),
                "slide order should follow structure.json");

        //only the first slide's thumbnail is emitted, marked THUMBNAIL
        assertEquals(2, metadataList.size());
        Metadata thumbnail = metadataList.get(1);
        assertEquals("image/png", thumbnail.get(HttpHeaders.CONTENT_TYPE));
        assertEquals("_slide1/geogebra_thumbnail.png",
                thumbnail.get(TikaCoreProperties.INTERNAL_PATH));
        assertEquals(TikaCoreProperties.EmbeddedResourceType.THUMBNAIL.toString(),
                thumbnail.get(TikaCoreProperties.EMBEDDED_RESOURCE_TYPE));
    }

    @Test
    public void testGGT() throws Exception {
        List<Metadata> metadataList = getRecursiveMetadata("testGeoGebraTool.ggt");
        Metadata metadata = metadataList.get(0);
        assertEquals("application/vnd.geogebra.tool", metadata.get(HttpHeaders.CONTENT_TYPE));
        assertEquals("Midpoint tool", metadata.get(GeoGebraParser.TOOL_NAME));

        String content = metadata.get(TikaCoreProperties.TIKA_CONTENT);
        assertContains("Midpoint tool", content);
        assertContains("Select two points to construct their midpoint", content);

        //no thumbnail in this tool file
        assertEquals(1, metadataList.size());
        assertNull(metadata.get(TikaCoreProperties.TITLE));
    }
}
