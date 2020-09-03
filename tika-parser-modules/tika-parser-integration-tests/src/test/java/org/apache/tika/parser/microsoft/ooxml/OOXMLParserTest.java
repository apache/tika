package org.apache.tika.parser.microsoft.ooxml;

import org.apache.tika.TikaTest;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.RecursiveParserWrapper;
import org.apache.tika.parser.microsoft.OfficeParserConfig;
import org.junit.Ignore;
import org.junit.Test;

import java.util.List;

public class OOXMLParserTest extends TikaTest {

    @Test
    public void testEmbeddedPDFInPPTX() throws Exception {
        List<Metadata> metadataList = getRecursiveMetadata("testPPT_EmbeddedPDF.pptx");
        Metadata pdfMetadata1 = metadataList.get(4);
        assertContains("Apache Tika", pdfMetadata1.get(RecursiveParserWrapper.TIKA_CONTENT));
        Metadata pdfMetadata2 = metadataList.get(5);
        assertContains("Hello World", pdfMetadata2.get(RecursiveParserWrapper.TIKA_CONTENT));
    }

    @Test
    public void testEmbeddedPDFInXLSX() throws Exception {
        List<Metadata> metadataList = getRecursiveMetadata("testExcel_embeddedPDF.xlsx");
        Metadata pdfMetadata = metadataList.get(1);
        assertContains("Hello World", pdfMetadata.get(RecursiveParserWrapper.TIKA_CONTENT));
    }

    @Test
    public void testEmbeddedPDFInStreamingPPTX() throws Exception {
        ParseContext parseContext = new ParseContext();
        OfficeParserConfig officeParserConfig = new OfficeParserConfig();
        officeParserConfig.setUseSAXPptxExtractor(true);
        parseContext.set(OfficeParserConfig.class, officeParserConfig);

        List<Metadata> metadataList = getRecursiveMetadata("testPPT_EmbeddedPDF.pptx", parseContext);
        Metadata pdfMetadata1 = metadataList.get(4);
        assertContains("Apache Tika", pdfMetadata1.get(RecursiveParserWrapper.TIKA_CONTENT));
        Metadata pdfMetadata2 = metadataList.get(5);
        assertContains("Hello World", pdfMetadata2.get(RecursiveParserWrapper.TIKA_CONTENT));
    }

    @Ignore("TODO figure out why this doesn't work")
    @Test(expected = org.apache.tika.exception.TikaException.class)
    public void testCorruptedZip() throws Exception {
        //TIKA_2446
        getRecursiveMetadata("testZIP_corrupted_oom.zip");
    }
}
