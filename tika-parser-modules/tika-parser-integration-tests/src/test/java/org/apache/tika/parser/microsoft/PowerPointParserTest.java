package org.apache.tika.parser.microsoft;

import org.apache.tika.TikaTest;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.parser.RecursiveParserWrapper;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;

public class PowerPointParserTest extends TikaTest {
    @Test
    public void testEmbeddedPDF() throws Exception {
        List<Metadata> metadataList = getRecursiveMetadata("testPPT_EmbeddedPDF.ppt");
        assertContains("Apache Tika project", metadataList.get(1).get(RecursiveParserWrapper.TIKA_CONTENT));
        assertEquals("3.pdf", metadataList.get(1).get(TikaCoreProperties.RESOURCE_NAME_KEY));
        assertContains("Hello World", metadataList.get(2).get(RecursiveParserWrapper.TIKA_CONTENT));
        assertEquals("4.pdf", metadataList.get(2).get(TikaCoreProperties.RESOURCE_NAME_KEY));
    }
}
