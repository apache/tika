package org.apache.tika.parser.tests.microsoft;

import org.apache.tika.TikaTest;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.RecursiveParserWrapper;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;

public class EMFParserTest extends TikaTest {

    @Test
    public void testTextExtractionWindows() throws Exception {
        List<Metadata> metadataList = getRecursiveMetadata("testEXCEL_embeddedPDF_windows.xls");
        Metadata emfMetadata = metadataList.get(1);
        assertEquals("image/emf", emfMetadata.get(Metadata.CONTENT_TYPE));
        assertContains("<p>testPDF.pdf</p>", emfMetadata.get(RecursiveParserWrapper.TIKA_CONTENT));

        //this is just the usual embedded pdf
        Metadata pdfMetadata = metadataList.get(2);
        assertEquals("application/pdf", pdfMetadata.get(Metadata.CONTENT_TYPE));
        assertContains("is a toolkit for detecting", pdfMetadata.get(RecursiveParserWrapper.TIKA_CONTENT));
    }

    @Test
    public void testPDFExtraction() throws Exception {
        List<Metadata> metadataList = getRecursiveMetadata("testEXCEL_embeddedPDF_mac.xls");
        //this pdf has to be extracted from within the EMF
        //it does not exist as a standalone pdf file inside the _mac.xls file.
        Metadata pdfMetadata = metadataList.get(1);
        assertEquals("application/pdf", pdfMetadata.get(Metadata.CONTENT_TYPE));
        assertContains("is a toolkit for detecting", pdfMetadata.get(RecursiveParserWrapper.TIKA_CONTENT));
    }
}
