package org.apache.tika.parser.microsoft;

import org.apache.tika.TikaTest;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.RecursiveParserWrapper;
import org.junit.Test;

import java.util.List;

public class ExcelParserTest extends TikaTest {
    @Test
    public void testEmbeddedPDF() throws Exception {
        List<Metadata> metadataList = getRecursiveMetadata("testExcel_embeddedPDF.xls");
        assertContains("Hello World!", metadataList.get(2).get(RecursiveParserWrapper.TIKA_CONTENT));
    }
}
