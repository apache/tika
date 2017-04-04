package org.apache.tika.parser.crypto;

import static org.junit.Assert.assertNotNull;

import java.io.InputStream;

import org.apache.tika.TikaTest;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.BodyContentHandler;
import org.junit.Test;
import org.xml.sax.ContentHandler;

public class TSDParserTest extends TikaTest {

    @Test
    public void testDetachedSignature() throws Exception {

        try (InputStream inputXml = 
                         TSDParserTest.class.getResourceAsStream("/test-documents/MANIFEST.XML.TSD");
             InputStream inputTxt1 = 
                         TSDParserTest.class.getResourceAsStream("/test-documents/Test1.txt.tsd");
             InputStream inputTxt2 = 
                         TSDParserTest.class.getResourceAsStream("/test-documents/Test2.txt.tsd");
             InputStream inputDocx = 
                         TSDParserTest.class.getResourceAsStream("/test-documents/Test3.docx.tsd");
             InputStream inputPdf = 
                         TSDParserTest.class.getResourceAsStream("/test-documents/Test4.pdf.tsd");
             InputStream inputPng = 
                         TSDParserTest.class.getResourceAsStream("/test-documents/Test5.PNG.tsd");) {

            TSDParser tsdParser = new TSDParser();

            ContentHandler handler = new BodyContentHandler();
            Metadata metadata = new Metadata();
            tsdParser.parse(inputXml, handler, metadata, new ParseContext());

            assertNotNull(handler);
            assertNotNull(metadata);
            assertContains("Description=Time Stamped Data Envelope", metadata.toString());
            assertContains("Content-Type=application/timestamped-data", metadata.toString());
            assertContains("File-Parsed=true", metadata.toString());
            
            handler = new BodyContentHandler();
            metadata = new Metadata();
            tsdParser.parse(inputTxt1, handler, metadata, new ParseContext());

            assertNotNull(handler);
            assertNotNull(metadata);
            assertContains("Description=Time Stamped Data Envelope", metadata.toString());
            assertContains("Content-Type=application/timestamped-data", metadata.toString());
            assertContains("File-Parsed=true", metadata.toString());
            
            handler = new BodyContentHandler();
            metadata = new Metadata();
            tsdParser.parse(inputTxt2, handler, metadata, new ParseContext());

            assertNotNull(handler);
            assertNotNull(metadata);
            assertContains("Description=Time Stamped Data Envelope", metadata.toString());
            assertContains("Content-Type=application/timestamped-data", metadata.toString());
            assertContains("File-Parsed=true", metadata.toString());
            
            handler = new BodyContentHandler();
            metadata = new Metadata();           
            tsdParser.parse(inputDocx, handler, metadata, new ParseContext());

            assertNotNull(handler);
            assertNotNull(metadata);
            assertContains("Description=Time Stamped Data Envelope", metadata.toString());
            assertContains("Content-Type=application/timestamped-data", metadata.toString());
            assertContains("File-Parsed=true", metadata.toString());
            
            handler = new BodyContentHandler();
            metadata = new Metadata();
            tsdParser.parse(inputPdf, handler, metadata, new ParseContext());

            assertNotNull(handler);
            assertNotNull(metadata);
            assertContains("Description=Time Stamped Data Envelope", metadata.toString());
            assertContains("Content-Type=application/timestamped-data", metadata.toString());
            assertContains("File-Parsed=true", metadata.toString());
            
            handler = new BodyContentHandler();
            metadata = new Metadata();
            tsdParser.parse(inputPng, handler, metadata, new ParseContext());

            assertNotNull(handler);
            assertNotNull(metadata);
            assertContains("Description=Time Stamped Data Envelope", metadata.toString());
            assertContains("Content-Type=application/timestamped-data", metadata.toString());
            assertContains("File-Parsed=true", metadata.toString());
            
        } 
    }
}
