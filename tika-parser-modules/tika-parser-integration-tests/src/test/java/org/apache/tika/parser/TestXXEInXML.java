/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.tika.parser;

import org.apache.poi.openxml4j.exceptions.InvalidFormatException;
import org.apache.tika.config.TikaConfig;
import org.apache.tika.exception.TikaException;
import org.apache.tika.io.IOUtils;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.microsoft.OfficeParserConfig;
import org.apache.tika.sax.ToHTMLContentHandler;
import org.apache.tika.utils.XMLReaderUtils;
import org.junit.Ignore;
import org.junit.Test;
import org.xml.sax.ContentHandler;

import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.fail;

/**
 * This tests for XXE in basically xml type files, straight xml and zipped xmls, e.g. ebook and ooxml.
 * It does not test for XXE prevention in files that may contain xml
 * files, such as PDFs and other XMP-containing files.
 */
public class TestXXEInXML extends XMLTestBase {
    //TODO: figure out how to test XFA and xmp in PDFs

    private static final byte[] XXE =
            "<!DOCTYPE roottag PUBLIC \"-//OXML/XXE/EN\" \"file:///couldnt_possibly_exist\">".getBytes(StandardCharsets.UTF_8);

    @Test
    @Ignore("ignore vulnerable tests")
    public void testConfirmVulnerable() throws Exception {
        try {
            parse("testXXE.xml",
                    getResourceAsStream("/test-documents/testXXE.xml"),
                    new VulnerableSAXParser(), new ParseContext());
            fail("should have failed!!!");
        } catch (FileNotFoundException e) {

        }
    }

    @Test
    public void testXML() throws Exception {
        parse("testXXE.xml", getResourceAsStream("/test-documents/testXXE.xml"),
                AUTO_DETECT_PARSER, new ParseContext());
    }

    @Test
    public void testInjectedXML() throws Exception {
        byte[] bytes = "<?xml version=\"1.0\" encoding=\"UTF-8\"?><document>blah</document>".getBytes(StandardCharsets.UTF_8);
        byte[] injected = injectXML(bytes, XXE);
        try {
            parse("injected",
                    new ByteArrayInputStream(injected),
                    new VulnerableSAXParser(), new ParseContext());
            fail("injected should have triggered xxe");
        } catch (FileNotFoundException e) {

        }
    }

    @Test
    public void test2003_2006xml() throws Exception {
        InputStream is = getResourceAsStream("/test-documents/testWORD_2003ml.xml");
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        IOUtils.copy(is, bos);
        byte[] injected = injectXML(bos.toByteArray(), XXE);
        parse("testWORD_2003ml.xml",
                new ByteArrayInputStream(injected), AUTO_DETECT_PARSER, new ParseContext());
        is.close();

        is = getResourceAsStream("/test-documents/testWORD_2006ml.xml");
        bos = new ByteArrayOutputStream();
        IOUtils.copy(is, bos);
        injected = injectXML(bos.toByteArray(), XXE);
        parse("testWORD_2006ml.xml", new ByteArrayInputStream(injected),
                AUTO_DETECT_PARSER, new ParseContext());
    }


    @Test
    public void testPOIOOXMLs() throws Exception {
        for (String fileName : new String[]{
                "testWORD.docx",
                "testWORD_1img.docx",
                "testWORD_2006ml.docx",
                "testWORD_embedded_pics.docx",
                "testWORD_macros.docm",
                "testEXCEL_textbox.xlsx",
                "testEXCEL_macro.xlsm",
                "testEXCEL_phonetic.xlsx",
                "testEXCEL_embeddedPDF_windows.xlsx",
                "testPPT_2imgs.pptx",
                "testPPT_comment.pptx",
                "testPPT_EmbeddedPDF.pptx",
                "testPPT_macros.pptm"
        }) {
            _testPOIOOXMLs(fileName);
        }
    }

    private void _testPOIOOXMLs(String fileName) throws Exception {
        Path injected = null;
        try (TikaInputStream tis =
                     TikaInputStream.get(getResourceAsStream("/test-documents/"+fileName))) {
            Path originalOOXML = tis.getPath();
            injected = injectZippedXMLs(originalOOXML, XXE, false);


            ContentHandler xhtml = new ToHTMLContentHandler();
            ParseContext parseContext = new ParseContext();
            //if the SafeContentHandler is turned off, this will throw an FNFE
            Metadata metadata = new Metadata();
            try {
                AUTO_DETECT_PARSER.parse(Files.newInputStream(injected), xhtml, metadata, parseContext);
            } catch (TikaException e) {
                Throwable cause = e.getCause();
                if (!(cause instanceof InvalidFormatException)) {
                    //as of POI 4.1.x
                    fail("POI should have thrown an IFE complaining about " +
                            "not being able to read content types part !");
                }
            } finally {
                Files.delete(injected);
            }

            try {
                metadata = new Metadata();
                xhtml = new ToHTMLContentHandler();

                OfficeParserConfig officeParserConfig = new OfficeParserConfig();
                parseContext.set(OfficeParserConfig.class, officeParserConfig);
                officeParserConfig.setUseSAXDocxExtractor(true);
                officeParserConfig.setUseSAXPptxExtractor(true);
                injected = injectZippedXMLs(originalOOXML, XXE, true);

                AUTO_DETECT_PARSER.parse(Files.newInputStream(injected), xhtml, metadata, parseContext);
            } catch (FileNotFoundException e) {
                e.printStackTrace();
                fail("problem with SAX-based: " + fileName + ": " + e.getMessage());
            } finally {
                Files.delete(injected);
            }
        }
    }

    @Test
    public void testXMLInZips() throws Exception {
        for (String fileName : new String[]{
                "testEPUB.epub"
        }) {
            _testXMLInZips(fileName);
        }
    }

    private void _testXMLInZips(String fileName) throws Exception {
        Path injected = null;
        try (TikaInputStream tis =
                     TikaInputStream.get(getResourceAsStream("/test-documents/"+fileName))) {
            injected = injectZippedXMLs(tis.getPath(), XXE, false);
        }
        Parser p = AUTO_DETECT_PARSER;
        ContentHandler xhtml = new ToHTMLContentHandler();
        ParseContext parseContext = new ParseContext();
        //if the SafeContentHandler is turned off, this will throw an FNFE
        Metadata metadata = new Metadata();
        try {
            p.parse(Files.newInputStream(injected), xhtml, metadata, parseContext);
        } finally {
            Files.delete(injected);
        }

    }


    @Test
    public void testDOM() throws Exception {
        byte[] bytes = "<?xml version=\"1.0\" encoding=\"UTF-8\"?><document>blah</document>".getBytes(StandardCharsets.UTF_8);
        byte[] injected = injectXML(bytes, XXE);
        for (int i = 0; i < XMLReaderUtils.getPoolSize()*2; i++) {
            //this shouldn't throw an exception
            XMLReaderUtils.buildDOM(new ByteArrayInputStream(injected), new ParseContext());
        }
    }
    //use this to confirm that this works
    //by manually turning off the SafeContentHandler in SXWPFWordExtractorDecorator's
    //handlePart
    public void testDocxWithIncorrectSAXConfiguration() throws Exception {
        Path injected = null;

        try (TikaInputStream tis = TikaInputStream.get(
                getResourceAsStream("/test-documents/testWORD_macros.docm"))) {
            injected = injectZippedXMLs(tis.getPath(), XXE, true);
        }

        ContentHandler xhtml = new ToHTMLContentHandler();
        ParseContext parseContext = new ParseContext();
        OfficeParserConfig officeParserConfig = new OfficeParserConfig();
        officeParserConfig.setUseSAXDocxExtractor(true);
        parseContext.set(OfficeParserConfig.class, officeParserConfig);
        parseContext.set(SAXParser.class, SAXParserFactory.newInstance().newSAXParser());
        //if the SafeContentHandler is turned off, this will throw an FNFE
        try {
            AUTO_DETECT_PARSER.parse(Files.newInputStream(injected), xhtml, new Metadata(), parseContext);
        } finally {
            //Files.delete(injected);
        }
    }

    @Test
    public void testDOMTikaConfig() throws Exception {
        //tests the DOM reader in TikaConfig
        //if the safeguards aren't in place, this throws a FNFE
        try (InputStream is =
                getResourceAsStream("/org/apache/tika/config/TIKA-1558-exclude.xml") ) {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            IOUtils.copy(is, bos);
            byte[] injected = injectXML(bos.toByteArray(), XXE);
            TikaConfig tikaConfig = new TikaConfig(new ByteArrayInputStream(injected));
        }
    }
}
