package org.apache.tika.parser.pkg;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.InputStream;

import org.apache.tika.TikaTest;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.sax.BodyContentHandler;
import org.junit.Before;
import org.junit.Test;
import org.xml.sax.ContentHandler;

public class PackageTest extends TikaTest {

    private static final MediaType TYPE_7ZIP = MediaType.application("x-7z-compressed");
    
    private ParseContext recursingContext;
    private Parser autoDetectParser;
    
    @Before
    public void setUp() throws Exception {
       
       autoDetectParser = new AutoDetectParser();
       recursingContext = new ParseContext();
       recursingContext.set(Parser.class, autoDetectParser);
    }
    
    @Test
    public void testZlibParsing() throws Exception {
        Parser parser = new AutoDetectParser(); // Should auto-detect!
        ContentHandler handler = new BodyContentHandler();
        Metadata metadata = new Metadata();

        try (InputStream stream = PackageTest.class.getResourceAsStream(
                "/test-documents/testTXT.zlib")) {
            parser.parse(stream, handler, metadata, recursingContext);
        }

        assertEquals("application/zlib", metadata.get(Metadata.CONTENT_TYPE));
        String content = handler.toString();
        assertContains("Test d'indexation de Txt", content);
        assertContains("http://www.apache.org", content);
    }
    
    
    @Test
    public void testArParsing() throws Exception {
        Parser parser = new AutoDetectParser();

        ContentHandler handler = new BodyContentHandler();
        Metadata metadata = new Metadata();

        try (InputStream stream = PackageTest.class.getResourceAsStream(
                "/test-documents/testARofText.ar")) {
            parser.parse(stream, handler, metadata, recursingContext);
        }

        assertEquals("application/x-archive",
                metadata.get(Metadata.CONTENT_TYPE));
        String content = handler.toString();
        assertContains("testTXT.txt", content);
        assertContains("Test d'indexation de Txt", content);
        assertContains("http://www.apache.org", content);

        try (InputStream stream = PackageTest.class.getResourceAsStream(
                "/test-documents/testARofSND.ar")) {
            parser.parse(stream, handler, metadata, recursingContext);
        }

        assertEquals("application/x-archive",
                     metadata.get(Metadata.CONTENT_TYPE));
        content = handler.toString();
        assertContains("testAU.au", content);
    }
    
    @Test
    public void testBzip2Parsing() throws Exception {
        Parser parser = new AutoDetectParser(); // Should auto-detect!
        ContentHandler handler = new BodyContentHandler();
        Metadata metadata = new Metadata();

        try (InputStream stream = PackageTest.class.getResourceAsStream(
                "/test-documents/test-documents.tbz2")) {
            parser.parse(stream, handler, metadata, recursingContext);
        }

        assertEquals("application/x-bzip2", metadata.get(Metadata.CONTENT_TYPE));
        String content = handler.toString();
        assertContains("test-documents/testEXCEL.xls", content);
        assertContains("Sample Excel Worksheet", content);
        assertContains("test-documents/testHTML.html", content);
        assertContains("Test Indexation Html", content);
        assertContains("test-documents/testOpenOffice2.odt", content);
        assertContains("This is a sample Open Office document", content);
        assertContains("test-documents/testPDF.pdf", content);
        assertContains("Apache Tika", content);
        assertContains("test-documents/testPPT.ppt", content);
        assertContains("Sample Powerpoint Slide", content);
        assertContains("test-documents/testRTF.rtf", content);
        assertContains("indexation Word", content);
        assertContains("test-documents/testTXT.txt", content);
        assertContains("Test d'indexation de Txt", content);
        assertContains("test-documents/testWORD.doc", content);
        assertContains("This is a sample Microsoft Word Document", content);
        assertContains("test-documents/testXML.xml", content);
        assertContains("Rida Benjelloun", content);
    }
    
    @Test
    public void testCompressParsing() throws Exception {
        Parser parser = new AutoDetectParser(); // Should auto-detect!
        ContentHandler handler = new BodyContentHandler();
        Metadata metadata = new Metadata();

        InputStream stream = PackageTest.class.getResourceAsStream(
                "/test-documents/test-documents.tar.Z");
        try {
            parser.parse(stream, handler, metadata, recursingContext);
        } finally {
            stream.close();
        }

        assertEquals("application/x-compress", metadata.get(Metadata.CONTENT_TYPE));
        String content = handler.toString();
        assertContains("test-documents/testEXCEL.xls", content);
        assertContains("Sample Excel Worksheet", content);
        assertContains("test-documents/testHTML.html", content);
        assertContains("Test Indexation Html", content);
        assertContains("test-documents/testOpenOffice2.odt", content);
        assertContains("This is a sample Open Office document", content);
        assertContains("test-documents/testPDF.pdf", content);
        assertContains("Apache Tika", content);
        assertContains("test-documents/testPPT.ppt", content);
        assertContains("Sample Powerpoint Slide", content);
        assertContains("test-documents/testRTF.rtf", content);
        assertContains("indexation Word", content);
        assertContains("test-documents/testTXT.txt", content);
        assertContains("Test d'indexation de Txt", content);
        assertContains("test-documents/testWORD.doc", content);
        assertContains("This is a sample Microsoft Word Document", content);
        assertContains("test-documents/testXML.xml", content);
        assertContains("Rida Benjelloun", content);
    }
    
    @Test
    public void testGzipParsing() throws Exception {
        Parser parser = new AutoDetectParser(); // Should auto-detect!
        ContentHandler handler = new BodyContentHandler();
        Metadata metadata = new Metadata();

        try (InputStream stream = PackageTest.class.getResourceAsStream(
                "/test-documents/test-documents.tgz")) {
            parser.parse(stream, handler, metadata, recursingContext);
        }

        assertEquals("application/gzip", metadata.get(Metadata.CONTENT_TYPE));
        String content = handler.toString();
        assertContains("test-documents/testEXCEL.xls", content);
        assertContains("Sample Excel Worksheet", content);
        assertContains("test-documents/testHTML.html", content);
        assertContains("Test Indexation Html", content);
        assertContains("test-documents/testOpenOffice2.odt", content);
        assertContains("This is a sample Open Office document", content);
        assertContains("test-documents/testPDF.pdf", content);
        assertContains("Apache Tika", content);
        assertContains("test-documents/testPPT.ppt", content);
        assertContains("Sample Powerpoint Slide", content);
        assertContains("test-documents/testRTF.rtf", content);
        assertContains("indexation Word", content);
        assertContains("test-documents/testTXT.txt", content);
        assertContains("Test d'indexation de Txt", content);
        assertContains("test-documents/testWORD.doc", content);
        assertContains("This is a sample Microsoft Word Document", content);
        assertContains("test-documents/testXML.xml", content);
        assertContains("Rida Benjelloun", content);
    }
    
    @Test
    public void testRarParsing() throws Exception {
        Parser parser = new AutoDetectParser(); // Should auto-detect!
        ContentHandler handler = new BodyContentHandler();
        Metadata metadata = new Metadata();

        try (InputStream stream = PackageTest.class.getResourceAsStream(
                "/test-documents/test-documents.rar")) {
            parser.parse(stream, handler, metadata, recursingContext);
        }

        assertEquals("application/x-rar-compressed", metadata.get(Metadata.CONTENT_TYPE));
        String content = handler.toString();
        assertContains("test-documents/testEXCEL.xls", content);
        assertContains("Sample Excel Worksheet", content);
        assertContains("test-documents/testHTML.html", content);
        assertContains("Test Indexation Html", content);
        assertContains("test-documents/testOpenOffice2.odt", content);
        assertContains("This is a sample Open Office document", content);
        assertContains("test-documents/testPDF.pdf", content);
        assertContains("Apache Tika", content);
        assertContains("test-documents/testPPT.ppt", content);
        assertContains("Sample Powerpoint Slide", content);
        assertContains("test-documents/testRTF.rtf", content);
        assertContains("indexation Word", content);
        assertContains("test-documents/testTXT.txt", content);
        assertContains("Test d'indexation de Txt", content);
        assertContains("test-documents/testWORD.doc", content);
        assertContains("This is a sample Microsoft Word Document", content);
        assertContains("test-documents/testXML.xml", content);
        assertContains("Rida Benjelloun", content);
    }
    
    @Test
    public void test7ZParsing() throws Exception {
        Parser parser = new AutoDetectParser(); // Should auto-detect!
        ContentHandler handler = new BodyContentHandler();
        Metadata metadata = new Metadata();
        
        // Ensure 7zip is a parsable format
        assertTrue("No 7zip parser found", 
                parser.getSupportedTypes(recursingContext).contains(TYPE_7ZIP));
        
        // Parse
        try (InputStream stream = PackageTest.class.getResourceAsStream(
                "/test-documents/test-documents.7z")) {
            parser.parse(stream, handler, metadata, recursingContext);
        }

        assertEquals(TYPE_7ZIP.toString(), metadata.get(Metadata.CONTENT_TYPE));
        String content = handler.toString();
        assertContains("test-documents/testEXCEL.xls", content);
        assertContains("Sample Excel Worksheet", content);
        assertContains("test-documents/testHTML.html", content);
        assertContains("Test Indexation Html", content);
        assertContains("test-documents/testOpenOffice2.odt", content);
        assertContains("This is a sample Open Office document", content);
        assertContains("test-documents/testPDF.pdf", content);
        assertContains("Apache Tika", content);
        assertContains("test-documents/testPPT.ppt", content);
        assertContains("Sample Powerpoint Slide", content);
        assertContains("test-documents/testRTF.rtf", content);
        assertContains("indexation Word", content);
        assertContains("test-documents/testTXT.txt", content);
        assertContains("Test d'indexation de Txt", content);
        assertContains("test-documents/testWORD.doc", content);
        assertContains("This is a sample Microsoft Word Document", content);
        assertContains("test-documents/testXML.xml", content);
        assertContains("Rida Benjelloun", content);
    }
    @Test
    public void testTarParsing() throws Exception {
        Parser parser = new AutoDetectParser(); // Should auto-detect!
        ContentHandler handler = new BodyContentHandler();
        Metadata metadata = new Metadata();

        try (InputStream stream = PackageTest.class.getResourceAsStream(
                "/test-documents/test-documents.tar")) {
            parser.parse(stream, handler, metadata, recursingContext);
        }

        assertEquals("application/x-tar", metadata.get(Metadata.CONTENT_TYPE));
        String content = handler.toString();
        assertContains("test-documents/testEXCEL.xls", content);
        assertContains("Sample Excel Worksheet", content);
        assertContains("test-documents/testHTML.html", content);
        assertContains("Test Indexation Html", content);
        assertContains("test-documents/testOpenOffice2.odt", content);
        assertContains("This is a sample Open Office document", content);
        assertContains("test-documents/testPDF.pdf", content);
        assertContains("Apache Tika", content);
        assertContains("test-documents/testPPT.ppt", content);
        assertContains("Sample Powerpoint Slide", content);
        assertContains("test-documents/testRTF.rtf", content);
        assertContains("indexation Word", content);
        assertContains("test-documents/testTXT.txt", content);
        assertContains("Test d'indexation de Txt", content);
        assertContains("test-documents/testWORD.doc", content);
        assertContains("This is a sample Microsoft Word Document", content);
        assertContains("test-documents/testXML.xml", content);
        assertContains("Rida Benjelloun", content);
    }
    
    @Test
    public void testZipParsing() throws Exception {
        Parser parser = new AutoDetectParser(); // Should auto-detect!
        ContentHandler handler = new BodyContentHandler();
        Metadata metadata = new Metadata();

        try (InputStream stream = PackageTest.class.getResourceAsStream(
                "/test-documents/test-documents.zip")) {
            parser.parse(stream, handler, metadata, recursingContext);
        }

        assertEquals("application/zip", metadata.get(Metadata.CONTENT_TYPE));
        String content = handler.toString();
        assertContains("testEXCEL.xls", content);
        assertContains("Sample Excel Worksheet", content);
        assertContains("testHTML.html", content);
        assertContains("Test Indexation Html", content);
        assertContains("testOpenOffice2.odt", content);
        assertContains("This is a sample Open Office document", content);
        assertContains("testPDF.pdf", content);
        assertContains("Apache Tika", content);
        assertContains("testPPT.ppt", content);
        assertContains("Sample Powerpoint Slide", content);
        assertContains("testRTF.rtf", content);
        assertContains("indexation Word", content);
        assertContains("testTXT.txt", content);
        assertContains("Test d'indexation de Txt", content);
        assertContains("testWORD.doc", content);
        assertContains("This is a sample Microsoft Word Document", content);
        assertContains("testXML.xml", content);
        assertContains("Rida Benjelloun", content);
    }
    
    @Test
    public void testSvgzParsing() throws Exception {
        Parser parser = new AutoDetectParser(); // Should auto-detect!
        ContentHandler handler = new BodyContentHandler();
        Metadata metadata = new Metadata();

        try (InputStream stream = PackageTest.class.getResourceAsStream(
                "/test-documents/testSVG.svgz")) {
            parser.parse(stream, handler, metadata, recursingContext);
        }

        assertEquals("application/gzip", metadata.get(Metadata.CONTENT_TYPE));
        String content = handler.toString();
        assertContains("Test SVG image", content);
    }
}
