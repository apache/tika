package org.apache.tika.parser.apple;

import org.apache.tika.TikaTest;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.sax.AbstractRecursiveParserWrapperHandler;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;

public class PListParserTest extends TikaTest {
    @Test
    public void testWebArchive() throws Exception {
        List<Metadata> metadataList = getRecursiveMetadata("testWEBARCHIVE.webarchive");
        assertEquals(12, metadataList.size());
        Metadata m0 = metadataList.get(0);
        assertEquals("application/x-bplist-webarchive", m0.get(Metadata.CONTENT_TYPE));
        Metadata m1 = metadataList.get(1);
        String content = m1.get(AbstractRecursiveParserWrapperHandler.TIKA_CONTENT);
        assertContains("December 2008: Apache Tika Release", content);
    }

}
