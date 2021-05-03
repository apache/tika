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
package org.apache.tika.parser.microsoft.rtf;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.io.FilenameUtils;
import org.junit.Assert;
import org.junit.Test;

import org.apache.tika.TikaTest;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.RTFMetadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.RecursiveParserWrapper;
import org.apache.tika.sax.BasicContentHandlerFactory;
import org.apache.tika.sax.RecursiveParserWrapperHandler;

public class RTFParserTest extends TikaTest {
    // TIKA-1010
    @Test
    public void testEmbeddedMonster() throws Exception {

        Map<Integer, Pair> expected = new HashMap<>();
        expected.put(3, new Pair("Hw.txt", "text/plain; charset=ISO-8859-1"));
        expected.put(4, new Pair("file_0.doc", "application/msword"));
        expected.put(7, new Pair("file_1.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
        expected.put(10, new Pair("text.html", "text/html; charset=windows-1252"));
        expected.put(11, new Pair("html-within-zip.zip", "application/zip"));
        expected.put(12,
                new Pair("test-zip-of-zip_\u666E\u6797\u65AF\u987F.zip", "application/zip"));
        expected.put(15, new Pair("testHTML_utf8_\u666E\u6797\u65AF\u987F.html",
                "text/html; charset=UTF-8"));
        expected.put(18, new Pair("testJPEG_\u666E\u6797\u65AF\u987F.jpg", "image/jpeg"));
        expected.put(21, new Pair("file_2.xls", "application/vnd.ms-excel"));
        expected.put(24,
                new Pair("testMSG_\u666E\u6797\u65AF\u987F.msg", "application/vnd.ms-outlook"));
        expected.put(27, new Pair("file_3.pdf", "application/pdf"));
        expected.put(30, new Pair("file_4.ppt", "application/vnd.ms-powerpoint"));
        expected.put(34, new Pair("file_5.pptx",
                "application/vnd.openxmlformats-officedocument.presentationml.presentation"));
        expected.put(33, new Pair("thumbnail.jpeg", "image/jpeg"));
        expected.put(37, new Pair("file_6.doc", "application/msword"));
        expected.put(40, new Pair("file_7.doc", "application/msword"));
        expected.put(43, new Pair("file_8.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document"));
        expected.put(46, new Pair("testJPEG_\u666E\u6797\u65AF\u987F.jpg", "image/jpeg"));


        List<Metadata> metadataList = getRecursiveMetadata("testRTFEmbeddedFiles.rtf");
        assertEquals(49, metadataList.size());
        for (Map.Entry<Integer, Pair> e : expected.entrySet()) {
            Metadata metadata = metadataList.get(e.getKey());
            Pair p = e.getValue();
            assertNotNull(metadata.get(TikaCoreProperties.RESOURCE_NAME_KEY));
            //necessary to getName() because MSOffice extractor includes
            //directory: _1457338524/HW.txt
            Assert.assertEquals("filename equals ", p.fileName,
                    FilenameUtils.getName(metadata.get(TikaCoreProperties.EMBEDDED_RESOURCE_PATH)));

            assertEquals(p.mimeType, metadata.get(Metadata.CONTENT_TYPE));
        }
        assertEquals("C:\\Users\\tallison\\AppData\\Local\\Temp\\testJPEG_普林斯顿.jpg",
                metadataList.get(46).get(TikaCoreProperties.ORIGINAL_RESOURCE_NAME));
    }

    //TIKA-1010 test regular (not "embedded") images/picts
    @Test
    public void testRegularImages() throws Exception {
        ParseContext ctx = new ParseContext();
        RecursiveParserWrapper parser = new RecursiveParserWrapper(AUTO_DETECT_PARSER);
        RecursiveParserWrapperHandler handler = new RecursiveParserWrapperHandler(
                new BasicContentHandlerFactory(BasicContentHandlerFactory.HANDLER_TYPE.IGNORE, -1),
                -1);
        Metadata rootMetadata = new Metadata();
        rootMetadata.add(TikaCoreProperties.RESOURCE_NAME_KEY, "testRTFRegularImages.rtf");
        try (TikaInputStream tis = TikaInputStream
                .get(getResourceAsStream("/test-documents/testRTFRegularImages.rtf"))) {
            parser.parse(tis, handler, rootMetadata, ctx);
        }
        List<Metadata> metadatas = handler.getMetadataList();

        Metadata meta_jpg_exif = metadatas.get(1);//("testJPEG_EXIF_\u666E\u6797\u65AF\u987F.jpg");
        Metadata meta_jpg = metadatas.get(3);//("testJPEG_\u666E\u6797\u65AF\u987F.jpg");

        assertTrue(meta_jpg_exif != null);
        assertTrue(meta_jpg != null);
        assertTrue(Arrays.asList(meta_jpg_exif.getValues(TikaCoreProperties.SUBJECT))
                .contains("serbor"));
        assertTrue(meta_jpg.get(TikaCoreProperties.COMMENTS).contains("Licensed to the Apache"));
        //make sure old metadata doesn't linger between objects
        assertFalse(
                Arrays.asList(meta_jpg.getValues(TikaCoreProperties.SUBJECT)).contains("serbor"));
        assertEquals("false", meta_jpg.get(RTFMetadata.THUMBNAIL));
        assertEquals("false", meta_jpg_exif.get(RTFMetadata.THUMBNAIL));

        //need flexibility for if tesseract is installed or not
        assertTrue(meta_jpg.names().length >= 50 && meta_jpg.names().length <= 51);
        assertTrue(meta_jpg_exif.names().length >= 109 && meta_jpg_exif.names().length <= 110);
    }

    private static class Pair {
        final String fileName;
        final String mimeType;

        Pair(String fileName, String mimeType) {
            this.fileName = fileName;
            this.mimeType = mimeType;
        }
    }

}
