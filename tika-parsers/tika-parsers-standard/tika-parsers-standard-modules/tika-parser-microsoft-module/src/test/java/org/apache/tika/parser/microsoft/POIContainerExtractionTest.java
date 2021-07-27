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
package org.apache.tika.parser.microsoft;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import org.apache.tika.detect.microsoft.POIFSContainerDetector;
import org.apache.tika.extractor.ContainerExtractor;
import org.apache.tika.extractor.ParserContainerExtractor;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.mime.MediaType;

/**
 * Tests that the various POI powered parsers are
 * able to extract their embedded contents.
 */
public class POIContainerExtractionTest extends AbstractPOIContainerExtractionTest {

    /**
     * For office files which don't have anything embedded in them
     */
    @Test
    public void testWithoutEmbedded() throws Exception {
        ContainerExtractor extractor = new ParserContainerExtractor();

        String[] files =
                new String[]{"testEXCEL.xls", "testWORD.doc", "testPPT.ppt", "testVISIO.vsd",
                        "test-outlook.msg"};
        for (String file : files) {
            // Process it without recursing
            TrackingHandler handler = process(file, extractor, false);

            // Won't have fired
            assertEquals(0, handler.filenames.size());
            assertEquals(0, handler.mediaTypes.size());

            // Ditto with recursing
            handler = process(file, extractor, true);
            assertEquals(0, handler.filenames.size());
            assertEquals(0, handler.mediaTypes.size());
        }
    }

    /**
     * Office files with embedded images, but no other
     * office files in them
     */
    @Test
    public void testEmbeddedImages() throws Exception {
        ContainerExtractor extractor = new ParserContainerExtractor();
        TrackingHandler handler;

        // Excel with 1 image
        handler = process("testEXCEL_1img.xls", extractor, false);
        assertEquals(1, handler.filenames.size());
        assertEquals(1, handler.mediaTypes.size());

        assertEquals(null, handler.filenames.get(0));
        assertEquals(TYPE_PNG, handler.mediaTypes.get(0));


        // PowerPoint with 2 images + sound
        // TODO


        // Word with 1 image
        handler = process("testWORD_1img.doc", extractor, false);
        assertEquals(1, handler.filenames.size());
        assertEquals(1, handler.mediaTypes.size());

        assertEquals("image1.png", handler.filenames.get(0));
        assertEquals(TYPE_PNG, handler.mediaTypes.get(0));


        // Word with 3 images
        handler = process("testWORD_3imgs.doc", extractor, false);
        assertEquals(3, handler.filenames.size());
        assertEquals(3, handler.mediaTypes.size());

        assertEquals("image1.png", handler.filenames.get(0));
        assertEquals("image2.jpg", handler.filenames.get(1));
        assertEquals("image3.png", handler.filenames.get(2));
        assertEquals(TYPE_PNG, handler.mediaTypes.get(0));
        assertEquals(TYPE_JPG, handler.mediaTypes.get(1));
        assertEquals(TYPE_PNG, handler.mediaTypes.get(2));
    }


    @Test
    public void testEmbeddedOfficeFilesXML() throws Exception {
        ContainerExtractor extractor = new ParserContainerExtractor();
        TrackingHandler handler;

        handler = process("EmbeddedDocument.docx", extractor, false);
        assertTrue(handler.filenames.contains("Microsoft_Office_Excel_97-2003_Worksheet1.bin"));
        assertEquals(2, handler.filenames.size());
    }

    @Test
    public void testPowerpointImages() throws Exception {
        ContainerExtractor extractor = new ParserContainerExtractor();
        TrackingHandler handler;

        handler = process("pictures.ppt", extractor, false);
        assertTrue(handler.mediaTypes.contains(new MediaType("image", "jpeg")));
        assertTrue(handler.mediaTypes.contains(new MediaType("image", "png")));
    }

    @Test
    public void testEmbeddedStorageId() throws Exception {

        List<Metadata> list = getRecursiveMetadata("testWORD_embeded.doc");
        //.docx
        assertEquals("{F4754C9B-64F5-4B40-8AF4-679732AC0607}",
                list.get(10).get(TikaCoreProperties.EMBEDDED_STORAGE_CLASS_ID));
        //_1345471035.ppt
        assertEquals("{64818D10-4F9B-11CF-86EA-00AA00B929E8}",
                list.get(14).get(TikaCoreProperties.EMBEDDED_STORAGE_CLASS_ID));
        //_1345470949.xls
        assertEquals("{00020820-0000-0000-C000-000000000046}",
                list.get(16).get(TikaCoreProperties.EMBEDDED_STORAGE_CLASS_ID));

    }

    @Test
    public void testEmbeddedGraphChart() throws Exception {
        //doc converts a chart to a actual xls file
        //so we only need to look in ppt and xls
        for (String suffix : new String[]{"ppt", "xls"}) {
            List<Metadata> list = getRecursiveMetadata("testMSChart-govdocs-428996." + suffix);
            boolean found = false;
            for (Metadata m : list) {
                if (m.get(Metadata.CONTENT_TYPE)
                        .equals(POIFSContainerDetector.MS_GRAPH_CHART.toString())) {
                    found = true;
                }
                assertNull(m.get(TikaCoreProperties.EMBEDDED_EXCEPTION));
            }
            assertTrue(found, "didn't find chart in " + suffix);
        }
    }

    @Test
    public void testEmbeddedEquation() throws Exception {
        //file derives from govdocs1 863534.doc
        List<Metadata> metadataList = getRecursiveMetadata("testMSEquation-govdocs-863534.doc");
        assertEquals(3, metadataList.size());
        assertEquals("application/vnd.ms-equation", metadataList.get(2).get(Metadata.CONTENT_TYPE));
    }
}
