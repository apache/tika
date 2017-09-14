package org.apache.tika;

import org.apache.tika.exception.TikaException;
import org.apache.tika.io.IOUtils;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.AbstractParser;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.parser.microsoft.OfficeParserConfig;
import org.apache.tika.sax.TaggedContentHandler;
import org.apache.tika.sax.TextContentHandler;
import org.apache.tika.sax.ToHTMLContentHandler;
import org.junit.Test;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Enumeration;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import static org.junit.Assert.fail;

public class TestXXEInXML extends TikaTest {
    //TODO: figure out how to test XFA and xmp in PDFs

    private static final String EVIL = "<!DOCTYPE roottag PUBLIC \"-//OXML/XXE/EN\" \"file:///couldnt_possibly_exist\">";

    @Test
    public void testConfirmVulnerable() throws Exception {
        try {
            parse("testXXE.xml", getResourceAsStream("/test-documents/testXXE.xml"), new VulnerableXMLParser());
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
        byte[] injected = injectXML(bytes);
        try {
            parse("injected", new ByteArrayInputStream(injected), new VulnerableXMLParser());
            fail("injected should have triggered xxe");
        } catch (FileNotFoundException e) {

        }
    }

    @Test
    public void test2003_2006xml() throws Exception {
        InputStream is = getResourceAsStream("/test-documents/testWORD_2003ml.xml");
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        IOUtils.copy(is, bos);
        byte[] injected = injectXML(bos.toByteArray());
        parse("testWORD_2003ml.xml", new ByteArrayInputStream(injected), new AutoDetectParser());
        is.close();

        is = getResourceAsStream("/test-documents/testWORD_2006ml.xml");
        bos = new ByteArrayOutputStream();
        IOUtils.copy(is, bos);
        injected = injectXML(bos.toByteArray());
        parse("testWORD_2006ml.xml", new ByteArrayInputStream(injected), new AutoDetectParser());
    }

    @Test
    public void testOOXML() throws Exception {
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
                "testPPT_embeddedPDF.pptx",
                "testPPT_macros.pptm"
        }) {
            try {
                _testOOXML(fileName);
            } catch (Exception e) {
                e.printStackTrace();
                fail("problem with: "+fileName + ": "+ e.getMessage());
            }
        }
    }

    private void _testOOXML(String fileName) throws Exception {
        Path originalOOXML = getResourceAsFile("/test-documents/"+fileName).toPath();
        Path injected = injectOOXML(originalOOXML);
        Parser p = new AutoDetectParser();
        ContentHandler xhtml = new ToHTMLContentHandler();
        ParseContext parseContext = new ParseContext();
        OfficeParserConfig officeParserConfig = new OfficeParserConfig();
        parseContext.set(OfficeParserConfig.class, officeParserConfig);
        //if the SafeContentHandler is turned off, this will throw an FNFE
        Metadata metadata = new Metadata();
        try {
            p.parse(Files.newInputStream(injected), xhtml, metadata, parseContext);

            metadata = new Metadata();
            officeParserConfig.setUseSAXDocxExtractor(true);
            officeParserConfig.setUseSAXPptxExtractor(true);

            p.parse(Files.newInputStream(injected), xhtml, metadata, parseContext);

        } finally {
            Files.delete(injected);
        }

    }

    //use this to confirm that this works
    //by manually turning off the SafeContentHandler in SXWPFWordExtractorDecorator's
    //handlePart
    public void testDocxWithIncorrectSAXConfiguration() throws Exception {
        Path originalDocx = getResourceAsFile("/test-documents/testWORD_macros.docm").toPath();
        Path injected = injectOOXML(originalDocx);
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

    private Path injectOOXML(Path original) throws IOException {
        ZipFile input = new ZipFile(original.toFile());
        File output = Files.createTempFile("tika-xxe-", ".zip").toFile();
        ZipOutputStream outZip = new ZipOutputStream(new FileOutputStream(output));
        Enumeration<? extends ZipEntry> zipEntryEnumeration = input.entries();
        while (zipEntryEnumeration.hasMoreElements()) {
            ZipEntry entry = zipEntryEnumeration.nextElement();
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            IOUtils.copy(input.getInputStream(entry), bos);
            byte[] bytes = bos.toByteArray();
            if (entry.getName().endsWith(".xml") &&
                    //don't inject the slides because you'll get a bean exception
                    //Unexpected node
                    ! entry.getName().contains("slides/slide")) {
                bytes = injectXML(bytes);
            }
            ZipEntry outEntry = new ZipEntry(entry.getName());
            outZip.putNextEntry(outEntry);
            outZip.write(bytes);
            outZip.closeEntry();
        }
        outZip.flush();
        outZip.close();

        return output.toPath();
    }

    private byte[] injectXML(byte[] input) throws IOException {

        int startXML = -1;
        int endXML = -1;
        for (int i = 0; i < input.length; i++) {
            if (input[i] == '<' && i+1 < input.length && input[i+1] == '?') {
                    startXML = i;
            }
            if (input[i] == '?' && i+1 < input.length && input[i+1] == '>') {
                endXML = i+1;
                break;
            }
        }
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        if (startXML > -1 && endXML > -1) {
            bos.write(input, startXML, endXML-startXML+1);
        }
        bos.write(EVIL.getBytes(StandardCharsets.UTF_8));
        bos.write(input, endXML+1, (input.length-endXML-1));
        return bos.toByteArray();
    }

    private void parse(String testFile, InputStream is, Parser parser) throws Exception {
        parser.parse(is, new DefaultHandler(), new Metadata(), new ParseContext());
    }


    private static class VulnerableXMLParser extends AbstractParser {

        @Override
        public Set<MediaType> getSupportedTypes(ParseContext context) {
            return Collections.singleton(MediaType.APPLICATION_XML);
        }

        @Override
        public void parse(InputStream stream, ContentHandler handler, Metadata metadata, ParseContext context) throws IOException, SAXException, TikaException {

            TaggedContentHandler tagged = new TaggedContentHandler(handler);
            try {
                SAXParserFactory saxParserFactory = SAXParserFactory.newInstance();
                SAXParser parser = saxParserFactory.newSAXParser();
                parser.parse( stream,
                        new TextContentHandler(handler,
                        true));
            } catch (SAXException e) {
                //there will be one...ignore it
            } catch (ParserConfigurationException e) {
                throw new TikaException("parser config ex", e);
            }

        }
    }

}
