package org.apache.tika.parser.hwp;

import static org.junit.Assert.assertEquals;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

import org.apache.tika.TikaTest;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.BodyContentHandler;
import org.junit.Test;
import org.xml.sax.ContentHandler;

import junit.framework.TestCase;

public class HwpV5ParserTest extends TikaTest {

	@Test
    public void testHwpV5Parser() throws Exception {

        try (InputStream input = HwpV5ParserTest.class.getResourceAsStream(
                "/test-documents/test-documents-v5.hwp")) {
            ContentHandler handler = new BodyContentHandler();
            Metadata metadata = new Metadata();
            new HwpV5Parser().parse(input, handler, metadata, new ParseContext());

            assertEquals(
                    "application/x-hwp-v5",
                    metadata.get(Metadata.CONTENT_TYPE));
            assertEquals("Apache Tika", metadata.get(TikaCoreProperties.TITLE));
            assertEquals("SooMyung Lee", metadata.get(TikaCoreProperties.CREATOR));
            
            assertContains("Apache Tika", handler.toString());
        }
    }
	
}
