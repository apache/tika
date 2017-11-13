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
package org.apache.tika.parser.rtf;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.StringWriter;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.io.FilenameUtils;
import org.apache.tika.Tika;
import org.apache.tika.TikaTest;
import org.apache.tika.config.TikaConfig;
import org.apache.tika.extractor.ContainerExtractor;
import org.apache.tika.extractor.ParserContainerExtractor;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.Office;
import org.apache.tika.metadata.OfficeOpenXMLCore;
import org.apache.tika.metadata.RTFMetadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.parser.RecursiveParserWrapper;
import org.apache.tika.sax.BasicContentHandlerFactory;
import org.apache.tika.sax.BodyContentHandler;
import org.apache.tika.sax.WriteOutContentHandler;
import org.junit.Test;
import org.xml.sax.ContentHandler;

/**
 * Junit test class for the Tika {@link RTFParser}
 */
public class RTFParserTest extends TikaTest {

    private Tika tika = new Tika();

    @Test
    public void testBasicExtraction() throws Exception {
        File file = getResourceAsFile("/test-documents/testRTF.rtf");

        Metadata metadata = new Metadata();
        StringWriter writer = new StringWriter();
        tika.getParser().parse(
                new FileInputStream(file),
                new WriteOutContentHandler(writer),
                metadata,
                new ParseContext());
        String content = writer.toString();

        assertEquals("application/rtf", metadata.get(Metadata.CONTENT_TYPE));
        assertEquals(1, metadata.getValues(Metadata.CONTENT_TYPE).length);
        assertContains("Test", content);
        assertContains("indexation Word", content);
    }

    @Test
    public void testUmlautSpacesExtraction2() throws Exception {
        String content = getText("testRTFUmlautSpaces2.rtf");
        content = content.replaceAll("\\s+", "");
        assertEquals("\u00DCbersicht", content);
    }

    @Test
    public void testUnicodeUCNControlWordCharacterDoublingExtraction() throws Exception {
        String content = getText("testRTFUnicodeUCNControlWordCharacterDoubling.rtf");

        assertContains("\u5E74", content);
        assertContains("\u5ff5", content);
        assertContains("0 ", content);
        assertContains("abc", content);
        assertFalse("Doubled character \u5E74", content.contains("\u5E74\u5E74"));
    }

    @Test
    public void testHexEscapeInsideWord() throws Exception {
        String content = getText("testRTFHexEscapeInsideWord.rtf");
        assertContains("ESP\u00cdRITO", content);
    }

    @Test
    public void testWindowsCodepage1250() throws Exception {
        String content = getText("testRTFWindowsCodepage1250.rtf");
        assertContains("za\u017c\u00f3\u0142\u0107 g\u0119\u015bl\u0105 ja\u017a\u0144", content);
        assertContains("ZA\u017b\u00d3\u0141\u0106 G\u0118\u015aL\u0104 JA\u0179\u0143", content);
    }

    @Test
    public void testTableCellSeparation() throws Exception {
        File file = getResourceAsFile("/test-documents/testRTFTableCellSeparation.rtf");
        String content = tika.parseToString(file);
        content = content.replaceAll("\\s+", " ");
        assertContains("a b c d \u00E4 \u00EB \u00F6 \u00FC", content);
        assertContains("a b c d \u00E4 \u00EB \u00F6 \u00FC", content);
    }

    @Test
    public void testTableCellSeparation2() throws Exception {
        String content = getText("testRTFTableCellSeparation2.rtf");
        // TODO: why do we insert extra whitespace...?
        content = content.replaceAll("\\s+", " ");
        assertContains("Station Fax", content);
    }

    @Test
    public void testWordPadCzechCharactersExtraction() throws Exception {
        File file = getResourceAsFile("/test-documents/testRTFWordPadCzechCharacters.rtf");
        String s1 = tika.parseToString(file);
        assertTrue(s1.contains("\u010Cl\u00E1nek t\u00FDdne"));
        assertTrue(s1.contains("starov\u011Bk\u00E9 \u017Eidovsk\u00E9 n\u00E1bo\u017Eensk\u00E9 texty"));
    }

    @Test
    public void testWord2010CzechCharactersExtraction() throws Exception {
        File file = getResourceAsFile("/test-documents/testRTFWord2010CzechCharacters.rtf");
        String s1 = tika.parseToString(file);
        assertTrue(s1.contains("\u010Cl\u00E1nek t\u00FDdne"));
        assertTrue(s1.contains("starov\u011Bk\u00E9 \u017Eidovsk\u00E9 n\u00E1bo\u017Eensk\u00E9 texty"));
    }

    @Test
    public void testMS932Extraction() throws Exception {
        File file = getResourceAsFile("/test-documents/testRTF-ms932.rtf");
        String s1 = tika.parseToString(file);

        // Hello in Japanese
        assertTrue(s1.contains("\u3053\u3093\u306b\u3061\u306f"));

        // Verify title, since it was also encoded with MS932:
        Result r = getResult("testRTF-ms932.rtf");
        assertEquals("\u30bf\u30a4\u30c8\u30eb", r.metadata.get(TikaCoreProperties.TITLE));
    }

    @Test
    public void testUmlautSpacesExtraction() throws Exception {
        File file = getResourceAsFile("/test-documents/testRTFUmlautSpaces.rtf");
        String s1 = tika.parseToString(file);
        assertTrue(s1.contains("\u00DCbersicht"));
    }

    @Test
    public void testGothic() throws Exception {
        String content = getText("testRTFUnicodeGothic.rtf");
        assertContains("\uD800\uDF32\uD800\uDF3f\uD800\uDF44\uD800\uDF39\uD800\uDF43\uD800\uDF3A", content);
    }

    @Test
    public void testJapaneseText() throws Exception {
        Result r = getResult("testRTFJapanese.rtf");
        String content = r.text;

        // Verify title -- this title uses upr escape inside
        // title info field:
        assertEquals("\u30be\u30eb\u30b2\u3068\u5c3e\u5d0e\u3001\u6de1\u3005\u3068\u6700\u671f\u3000",
                r.metadata.get(TikaCoreProperties.TITLE));
        assertEquals("VMazel", r.metadata.get(TikaCoreProperties.CREATOR));
        assertEquals("VMazel", r.metadata.get(Metadata.AUTHOR));
        assertEquals("StarWriter", r.metadata.get(TikaCoreProperties.COMMENTS));

        // Special version of (GHQ)
        assertContains("\uff08\uff27\uff28\uff31\uff09", content);

        // 6 other characters
        assertContains("\u6771\u4eac\u90fd\u4e09\u9df9\u5e02", content);
    }

    @Test
    public void testMaxLength() throws Exception {
        File file = getResourceAsFile("/test-documents/testRTFJapanese.rtf");
        Metadata metadata = new Metadata();
        InputStream stream = TikaInputStream.get(file, metadata);

        // Test w/ default limit:
        Tika localTika = new Tika();
        String content = localTika.parseToString(stream, metadata);
        // parseToString closes for convenience:
        //stream.close();
        assertTrue(content.length() > 500);

        // Test setting max length on the instance:
        localTika.setMaxStringLength(200);
        stream = TikaInputStream.get(file, metadata);
        content = localTika.parseToString(stream, metadata);

        // parseToString closes for convenience:
        //stream.close();
        assertTrue(content.length() <= 200);

        // Test setting max length per-call:
        stream = TikaInputStream.get(file, metadata);
        content = localTika.parseToString(stream, metadata, 100);
        // parseToString closes for convenience:
        //stream.close();
        assertTrue(content.length() <= 100);
    }

    @Test
    public void testTextWithCurlyBraces() throws Exception {
        String content = getText("testRTFWithCurlyBraces.rtf");
        assertContains("{ some text inside curly brackets }", content);
    }

    @Test
    public void testControls() throws Exception {
        Result r = getResult("testRTFControls.rtf");
        String content = r.text;
        assertContains("Thiswordhasanem\u2014dash", content);
        assertContains("Thiswordhasanen\u2013dash", content);
        assertContains("Thiswordhasanon\u2011breakinghyphen", content);
        assertContains("Thiswordhasanonbreaking\u00a0space", content);
        assertContains("Thiswordhasanoptional\u00adhyphen", content);
        assertContains("\u2018Single quoted text\u2019", content);
        assertContains("\u201cDouble quoted text\u201d", content);
        assertContains("\u201cDouble quoted text again\u201d", content);
    }

    @Test
    public void testInvalidUnicode() throws Exception {
        Result r = getResult("testRTFInvalidUnicode.rtf");
        String content = r.text;
        assertContains("Unpaired hi \ufffd here", content);
        assertContains("Unpaired lo \ufffd here", content);
        assertContains("Mismatched pair \ufffd\ufffd here", content);
    }

    @Test
    public void testVarious() throws Exception {
        Result r = getResult("testRTFVarious.rtf");
        String content = r.text;
        assertContains("Footnote appears here", content);
        assertContains("This is a footnote.", content);
        assertContains("This is the header text.", content);
        assertContains("This is the footer text.", content);
        assertContains("Here is a text box", content);
        assertContains("Bold", content);
        assertContains("italic", content);
        assertContains("underline", content);
        assertContains("superscript", content);
        assertContains("subscript", content);
        assertContains("Here is a citation:", content);
        assertContains("Figure 1 This is a caption for Figure 1", content);
        assertContains("(Kramer)", content);

        // Table
        assertContains("Row 1 Col 1 Row 1 Col 2 Row 1 Col 3 Row 2 Col 1 Row 2 Col 2 Row 2 Col 3", content.replaceAll("\\s+", " "));

        // 2-columns
        assertContains("Row 1 column 1 Row 2 column 1 Row 1 column 2 Row 2 column 2", content.replaceAll("\\s+", " "));
        assertContains("This is a hyperlink", content);
        assertContains("Here is a list:", content);
        for (int row = 1; row <= 3; row++) {
            assertContains("Bullet " + row, content);
        }
        assertContains("Here is a numbered list:", content);
        for (int row = 1; row <= 3; row++) {
            assertContains("Number bullet " + row, content);
        }

        for (int row = 1; row <= 2; row++) {
            for (int col = 1; col <= 3; col++) {
                assertContains("Row " + row + " Col " + col, content);
            }
        }

        assertContains("Keyword1 Keyword2", content);
        assertEquals("Keyword1 Keyword2",
                r.metadata.get(TikaCoreProperties.KEYWORDS));

        assertContains("Subject is here", content);
        assertEquals("Subject is here",
                r.metadata.get(OfficeOpenXMLCore.SUBJECT));
        assertEquals("Subject is here",
                r.metadata.get(Metadata.SUBJECT));

        assertContains("Suddenly some Japanese text:", content);
        // Special version of (GHQ)
        assertContains("\uff08\uff27\uff28\uff31\uff09", content);
        // 6 other characters
        assertContains("\u30be\u30eb\u30b2\u3068\u5c3e\u5d0e\u3001\u6de1\u3005\u3068\u6700\u671f", content);

        assertContains("And then some Gothic text:", content);
        assertContains("\uD800\uDF32\uD800\uDF3f\uD800\uDF44\uD800\uDF39\uD800\uDF43\uD800\uDF3A", content);
    }

    @Test
    public void testVariousStyle() throws Exception {
        String content = getXML("testRTFVarious.rtf").xml;
        assertContains("<b>Bold</b>", content);
        assertContains("<i>italic</i>", content);
    }

    @Test
    public void testBoldItalic() throws Exception {
        String content = getXML("testRTFBoldItalic.rtf").xml;
        assertContains("<b>bold</b>", content);
        assertContains("<b>bold </b><b><i>italic</i></b>", content);
        assertContains("<b><i>italic </i></b><b>bold</b>", content);
        assertContains("<i>italic</i>", content);
        assertContains("<b>bold then </b><b><i>italic then</i></b><i> not bold</i>", content);
        assertContains("<i>italic then </i><b><i>bold then</i></b><b> not italic</b>", content);
    }

    @Test
    public void testHyperlink() throws Exception {
        String content = getXML("testRTFHyperlink.rtf").xml;
        assertContains("our most <a href=\"http://r.office.microsoft.com/r/rlidwelcomeFAQ?clid=1033\">frequently asked questions</a>", content);
        assertEquals(-1, content.indexOf("<p>\t\t</p>"));
    }

    @Test
    public void testIgnoredControlWord() throws Exception {
        assertContains("<p>The quick brown fox jumps over the lazy dog</p>", getXML("testRTFIgnoredControlWord.rtf").xml);
    }

    @Test
    public void testFontAfterBufferedText() throws Exception {
        assertContains("\u0423\u0432\u0430\u0436\u0430\u0435\u043c\u044b\u0439 \u043a\u043b\u0438\u0435\u043d\u0442!",
                getXML("testFontAfterBufferedText.rtf").xml);
    }

    @Test
    public void testListMicrosoftWord() throws Exception {
        String content = getXML("testRTFListMicrosoftWord.rtf").xml;
        assertContains("<ol>\t<li>one</li>", content);
        assertContains("</ol>", content);
        assertContains("<ul>\t<li>first</li>", content);
        assertContains("</ul>", content);
    }

    @Test
    public void testListLibreOffice() throws Exception {
        String content = getXML("testRTFListLibreOffice.rtf").xml;
        assertContains("<ol>\t<li>one</li>", content);
        assertContains("</ol>", content);
        assertContains("<ul>\t<li>first</li>", content);
        assertContains("</ul>", content);
    }

    // TIKA-782
    @Test
    public void testBinControlWord() throws Exception {
        ByteCopyingHandler embHandler = new ByteCopyingHandler();
        try (TikaInputStream tis = TikaInputStream.get(getResourceAsStream("/test-documents/testBinControlWord.rtf"))) {
            ContainerExtractor ex = new ParserContainerExtractor();
            assertEquals(true, ex.isSupported(tis));
            ex.extract(tis, ex, embHandler);
        }
        assertEquals(1, embHandler.bytes.size());

        byte[] bytes = embHandler.bytes.get(0);
        assertEquals(10, bytes.length);
        //}
        assertEquals(125, (int) bytes[4]);
        //make sure that at least the last value is correct
        assertEquals(-1, (int) bytes[9]);
    }

    // TIKA-999
    @Test
    public void testMetaDataCounts() throws Exception {
        XMLResult xml = getXML("testRTFWord2010CzechCharacters.rtf");
        assertEquals("1", xml.metadata.get(Office.PAGE_COUNT));
        assertEquals("70", xml.metadata.get(Office.WORD_COUNT));
        assertEquals("401", xml.metadata.get(Office.CHARACTER_COUNT));
        assertTrue(xml.metadata.get(Office.CREATION_DATE).startsWith("2010-10-13T"));
    }

    // TIKA-1192
    @Test
    public void testListOverride() throws Exception {
        Result r = getResult("testRTFListOverride.rtf");
        String content = r.text;
        assertContains("Body", content);
    }

    // TIKA-1305
    @Test
    public void testCorruptListOverride() throws Exception {
        Result r = getResult("testRTFCorruptListOverride.rtf");
        String content = r.text;
        assertContains("apple", content);
    }

    // TIKA-1010
    @Test
    public void testEmbeddedMonster() throws Exception {

        Map<Integer, Pair> expected = new HashMap<>();
        expected.put(3, new Pair("Hw.txt","text/plain; charset=ISO-8859-1"));
        expected.put(4, new Pair("file_0.doc", "application/msword"));
        expected.put(7, new Pair("file_1.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
        expected.put(10, new Pair("text.html", "text/html; charset=windows-1252"));
        expected.put(11, new Pair("html-within-zip.zip", "application/zip"));
        expected.put(12, new Pair("test-zip-of-zip_\u666E\u6797\u65AF\u987F.zip", "application/zip"));
        expected.put(15, new Pair("testHTML_utf8_\u666E\u6797\u65AF\u987F.html", "text/html; charset=UTF-8"));
        expected.put(18, new Pair("testJPEG_\u666E\u6797\u65AF\u987F.jpg", "image/jpeg"));
        expected.put(21, new Pair("file_2.xls", "application/vnd.ms-excel"));
        expected.put(24, new Pair("testMSG_\u666E\u6797\u65AF\u987F.msg", "application/vnd.ms-outlook"));
        expected.put(27, new Pair("file_3.pdf", "application/pdf"));
        expected.put(30, new Pair("file_4.ppt", "application/vnd.ms-powerpoint"));
        expected.put(34, new Pair("file_5.pptx", "application/vnd.openxmlformats-officedocument.presentationml.presentation"));
        expected.put(33, new Pair("thumbnail.jpeg", "image/jpeg"));
        expected.put(37, new Pair("file_6.doc", "application/msword"));
        expected.put(40, new Pair("file_7.doc", "application/msword"));
        expected.put(43, new Pair("file_8.docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document"));
        expected.put(46, new Pair("testJPEG_\u666E\u6797\u65AF\u987F.jpg", "image/jpeg"));


        List<Metadata> metadataList = getRecursiveMetadata("testRTFEmbeddedFiles.rtf");
        assertEquals(49, metadataList.size());
        for (Map.Entry<Integer, Pair> e : expected.entrySet()) {
            Metadata metadata = metadataList.get(e.getKey());
            Pair p = e.getValue();
            assertNotNull(metadata.get(Metadata.RESOURCE_NAME_KEY));
            //necessary to getName() because MSOffice extractor includes
            //directory: _1457338524/HW.txt
            assertEquals("filename equals ",
                    p.fileName, FilenameUtils.getName(
                            metadata.get(RecursiveParserWrapper.EMBEDDED_RESOURCE_PATH)));

            assertEquals(p.mimeType, metadata.get(Metadata.CONTENT_TYPE));
        }
        assertEquals("C:\\Users\\tallison\\AppData\\Local\\Temp\\testJPEG_普林斯顿.jpg",
                metadataList.get(46).get(TikaCoreProperties.ORIGINAL_RESOURCE_NAME));
    }
    
    //TIKA-1010 test regular (not "embedded") images/picts
    @Test
    public void testRegularImages() throws Exception {
        Parser base = new AutoDetectParser();
        ParseContext ctx = new ParseContext();
        RecursiveParserWrapper parser = new RecursiveParserWrapper(base,
                new BasicContentHandlerFactory(BasicContentHandlerFactory.HANDLER_TYPE.IGNORE, -1));
        ContentHandler handler = new BodyContentHandler();
        Metadata rootMetadata = new Metadata();
        rootMetadata.add(Metadata.RESOURCE_NAME_KEY, "testRTFRegularImages.rtf");
        try (TikaInputStream tis = TikaInputStream.get(getResourceAsStream("/test-documents/testRTFRegularImages.rtf"))) {
            parser.parse(tis, handler, rootMetadata, ctx);
        }
        List<Metadata> metadatas = parser.getMetadata();

        Metadata meta_jpg_exif = metadatas.get(1);//("testJPEG_EXIF_\u666E\u6797\u65AF\u987F.jpg");
        Metadata meta_jpg = metadatas.get(3);//("testJPEG_\u666E\u6797\u65AF\u987F.jpg");

        assertTrue(meta_jpg_exif != null);
        assertTrue(meta_jpg != null);
        assertTrue(Arrays.asList(meta_jpg_exif.getValues("dc:subject")).contains("serbor"));
        assertTrue(meta_jpg.get("Comments").contains("Licensed to the Apache"));
        //make sure old metadata doesn't linger between objects
        assertFalse(Arrays.asList(meta_jpg.getValues("dc:subject")).contains("serbor"));
        assertEquals("false", meta_jpg.get(RTFMetadata.THUMBNAIL));
        assertEquals("false", meta_jpg_exif.get(RTFMetadata.THUMBNAIL));

        assertEquals(50, meta_jpg.names().length);
        assertEquals(114, meta_jpg_exif.names().length);
    }

    @Test
    public void testMultipleNewlines() throws Exception {
        String content = getXML("testRTFNewlines.rtf").xml;
        content = content.replaceAll("[\r\n]+", " ");
        assertContains("<body><p>one</p> " +
                "<p /> " +
                "<p>two</p> " +
                "<p /> " +
                "<p /> " +
                "<p>three</p> " +
                "<p /> " +
                "<p /> " +
                "<p /> " +
                "<p>four</p>", content);
    }

    //TIKA-1010 test linked embedded doc
    @Test
    public void testEmbeddedLinkedDocument() throws Exception {
        Set<MediaType> skipTypes = new HashSet<MediaType>();
        skipTypes.add(MediaType.parse("image/emf"));
        skipTypes.add(MediaType.parse("image/wmf"));

        TrackingHandler tracker = new TrackingHandler(skipTypes);
        try (TikaInputStream tis = TikaInputStream.get(getResourceAsStream("/test-documents/testRTFEmbeddedLink.rtf"))) {
            ContainerExtractor ex = new ParserContainerExtractor();
            assertEquals(true, ex.isSupported(tis));
            ex.extract(tis, ex, tracker);
        }
        //should gracefully skip link and not throw NPE, IOEx, etc
        assertEquals(0, tracker.filenames.size());

        tracker = new TrackingHandler();
        try (TikaInputStream tis = TikaInputStream.get(getResourceAsStream("/test-documents/testRTFEmbeddedLink.rtf"))) {
            ContainerExtractor ex = new ParserContainerExtractor();
            assertEquals(true, ex.isSupported(tis));
            ex.extract(tis, ex, tracker);
        }
        //should gracefully skip link and not throw NPE, IOEx, etc
        assertEquals(2, tracker.filenames.size());
    }

    @Test
    public void testConfig() throws Exception {
        //test that memory allocation of the bin element is limited
        //via the config file.  Unfortunately, this test file's bin embedding contains 10 bytes
        //so we had to set the config to 0.
        InputStream is = getClass().getResourceAsStream("/org/apache/tika/parser/rtf/tika-config.xml");
        assertNotNull(is);
        TikaConfig tikaConfig = new TikaConfig(is);
        Parser p = new AutoDetectParser(tikaConfig);
        List<Metadata> metadataList = getRecursiveMetadata("testBinControlWord.rtf", p);
        assertEquals(1, metadataList.size());
        assertContains("TikaMemoryLimitException", metadataList.get(0).get(TikaCoreProperties.TIKA_META_EXCEPTION_EMBEDDED_STREAM));
    }

    @Test
    public void testBoldPlain() throws Exception {
        //TIKA-2410 -- bold should be turned off by "plain"
        XMLResult r = getXML("testRTFBoldPlain.rtf");
        assertContains("<b>Hank</b>", r.xml);
        assertNotContained("<b>Anna Smith", r.xml);
    }

    private Result getResult(String filename) throws Exception {
        File file = getResourceAsFile("/test-documents/" + filename);

        Metadata metadata = new Metadata();
        StringWriter writer = new StringWriter();
        tika.getParser().parse(
                new FileInputStream(file),
                new WriteOutContentHandler(writer),
                metadata,
                new ParseContext());
        String content = writer.toString();
        return new Result(content, metadata);
    }

    private String getText(String filename) throws Exception {
        return getResult(filename).text;
    }

    private static class Result {
        public final String text;
        public final Metadata metadata;

        public Result(String text, Metadata metadata) {
            this.text = text;
            this.metadata = metadata;
        }
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
