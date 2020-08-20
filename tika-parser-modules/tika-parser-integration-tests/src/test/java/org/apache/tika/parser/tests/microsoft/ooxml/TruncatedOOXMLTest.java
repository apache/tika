package org.apache.tika.parser.tests.microsoft.ooxml;

import org.apache.tika.TikaTest;
import org.apache.tika.metadata.Metadata;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;

public class TruncatedOOXMLTest extends TikaTest {

    @Test
    public void testWordTrunc13138() throws Exception {
        //this truncates the content_types.xml
        //this tests that there's a backoff to the pkg parser
        List<Metadata> metadataList = getRecursiveMetadata(truncate(
                "testWORD_various.docx", 13138), true);
        assertEquals(19, metadataList.size());
        Metadata m = metadataList.get(0);
        assertEquals("application/x-tika-ooxml", m.get(Metadata.CONTENT_TYPE));
    }

    @Test
    public void testWordTrunc774() throws Exception {
        //this is really truncated
        List<Metadata> metadataList = getRecursiveMetadata(truncate(
                "testWORD_various.docx", 774), true);
        assertEquals(4, metadataList.size());
        Metadata m = metadataList.get(0);
        assertEquals("application/x-tika-ooxml", m.get(Metadata.CONTENT_TYPE));
    }
}
