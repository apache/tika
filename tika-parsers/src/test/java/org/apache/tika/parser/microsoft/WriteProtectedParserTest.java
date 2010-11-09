package org.apache.tika.parser.microsoft;

import junit.framework.TestCase;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.sax.BodyContentHandler;
import org.xml.sax.ContentHandler;

import java.io.InputStream;

public class WriteProtectedParserTest extends TestCase {
    public void testWriteProtected() throws Exception {
        InputStream input = ExcelParserTest.class.getResourceAsStream(
                "/test-documents/protect.xlsx");

        Metadata metadata = new Metadata();
        ContentHandler handler = new BodyContentHandler();
        new OfficeParser().parse(input, handler, metadata);
        String content = handler.toString();
        assertTrue(content.contains("Office"));
    }
}
