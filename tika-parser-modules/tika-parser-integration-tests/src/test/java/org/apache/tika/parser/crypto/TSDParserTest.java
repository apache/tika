package org.apache.tika.parser.crypto;

import org.apache.tika.TikaTest;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.utils.ParserUtils;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class TSDParserTest extends TikaTest {
    @Test
    public void testBrokenPdf() throws Exception {
        //make sure that embedded file appears in list
        //and make sure embedded exception is recorded
        List<Metadata> list = getRecursiveMetadata("testTSD_broken_pdf.tsd");
        assertEquals(2, list.size());
        assertEquals("application/pdf", list.get(1).get(Metadata.CONTENT_TYPE));
        assertNotNull(list.get(1).get(ParserUtils.EMBEDDED_EXCEPTION));
        assertContains("org.apache.pdfbox.pdmodel.PDDocument.load", list.get(1).get(ParserUtils.EMBEDDED_EXCEPTION));
    }

    @Test
    public void testToXML() throws Exception {
        String xml = getXML("Test4.pdf.tsd").xml;
        assertContains("Empty doc",
                xml);
    }
}
