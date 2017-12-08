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
package org.apache.tika;

import org.apache.tika.config.TikaConfig;
import org.apache.tika.io.IOUtils;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.parser.microsoft.OfficeParserConfig;
import org.apache.tika.sax.ToHTMLContentHandler;
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
@Ignore
public class TestXXEInXML extends XMLTestBase {
    //TODO: figure out how to test XFA and xmp in PDFs

    private static final byte[] XXE =
            "<!DOCTYPE roottag PUBLIC \"-//OXML/XXE/EN\" \"file:///couldnt_possibly_exist\">".getBytes(StandardCharsets.UTF_8);

    @Test
    public void testConfirmVulnerable() throws Exception {
        try {
            parse("testXXE.xml", getResourceAsStream("/test-documents/testXXE.xml"), new VulnerableSAXParser());
            fail("should have failed!!!");
        } catch (FileNotFoundException e) {

        }
    }

    @Test
    public void testXML() throws Exception {
        parse("testXXE.xml", getResourceAsStream("/test-documents/testXXE.xml"), new AutoDetectParser());
    }

    @Test
    public void testInjectedXML() throws Exception {
        byte[] bytes = "<?xml version=\"1.0\" encoding=\"UTF-8\"?><document>blah</document>".getBytes(StandardCharsets.UTF_8);
        byte[] injected = injectXML(bytes, XXE);
        try {
            parse("injected", new ByteArrayInputStream(injected), new VulnerableSAXParser());
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
        parse("testWORD_2003ml.xml", new ByteArrayInputStream(injected), new AutoDetectParser());
        is.close();

        is = getResourceAsStream("/test-documents/testWORD_2006ml.xml");
        bos = new ByteArrayOutputStream();
        IOUtils.copy(is, bos);
        injected = injectXML(bos.toByteArray(), XXE);
        parse("testWORD_2006ml.xml", new ByteArrayInputStream(injected), new AutoDetectParser());
    }

    @Test
    public void testXMLInZips() throws Exception {
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
                "testPPT_macros.pptm",
                "testEPUB.epub"
        }) {
            _testOOXML(fileName);
        }
    }

    private void _testOOXML(String fileName) throws Exception {

        Path originalOOXML = getResourceAsFile("/test-documents/"+fileName).toPath();
        Path injected = injectZippedXMLs(originalOOXML, XXE, false);

        Parser p = new AutoDetectParser();
        ContentHandler xhtml = new ToHTMLContentHandler();
        ParseContext parseContext = new ParseContext();
        //if the SafeContentHandler is turned off, this will throw an FNFE
        Metadata metadata = new Metadata();
        try {
            p.parse(Files.newInputStream(injected), xhtml, metadata, parseContext);

        } catch (FileNotFoundException e) {
            e.printStackTrace();
            fail("problem with: "+fileName + ": "+ e.getMessage());
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

            p.parse(Files.newInputStream(injected), xhtml, metadata, parseContext);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
            fail("problem with SAX-based: "+fileName + ": "+ e.getMessage());
        } finally {
            Files.delete(injected);
        }
    }

    //use this to confirm that this works
    //by manually turning off the SafeContentHandler in SXWPFWordExtractorDecorator's
    //handlePart
    public void testDocxWithIncorrectSAXConfiguration() throws Exception {
        Path originalDocx = getResourceAsFile("/test-documents/testWORD_macros.docm").toPath();
        Path injected = injectZippedXMLs(originalDocx, XXE,true);
        Parser p = new AutoDetectParser();
        ContentHandler xhtml = new ToHTMLContentHandler();
        ParseContext parseContext = new ParseContext();
        OfficeParserConfig officeParserConfig = new OfficeParserConfig();
        officeParserConfig.setUseSAXDocxExtractor(true);
        parseContext.set(OfficeParserConfig.class, officeParserConfig);
        parseContext.set(SAXParser.class, SAXParserFactory.newInstance().newSAXParser());
        //if the SafeContentHandler is turned off, this will throw an FNFE
        try {
            p.parse(Files.newInputStream(injected), xhtml, new Metadata(), parseContext);
        } finally {
            //Files.delete(injected);
        }
    }

    @Test
    public void testDOMTikaConfig() throws Exception {
        //tests the DOM reader in TikaConfig
        //if the safeguards aren't in place, this throws a FNFE
        try (InputStream is =
                getResourceAsStream("/org/apache/tika/config/TIKA-1558-blacklist.xml") ) {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            IOUtils.copy(is, bos);
            byte[] injected = injectXML(bos.toByteArray(), XXE);
            TikaConfig tikaConfig = new TikaConfig(new ByteArrayInputStream(injected));
        }
    }





}
