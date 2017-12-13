package org.apache.tika.parser.microsoft.ooxml.xps;

import org.apache.tika.Tika;
import org.apache.tika.TikaTest;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.parser.RecursiveParserWrapper;
import org.junit.Test;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.Assert.assertEquals;

public class XPSParserTest extends TikaTest {

    @Test
    public void testBasic() throws Exception {
        List<Metadata> metadataList = getRecursiveMetadata("testPPT.xps");
        assertEquals(2, metadataList.size());

        //metadata
        assertEquals("Rajiv", metadataList.get(0).get(TikaCoreProperties.CREATOR));
        assertEquals("2010-06-29T12:06:31Z", metadataList.get(0).get(TikaCoreProperties.CREATED));
        assertEquals("2010-06-29T12:06:31Z", metadataList.get(0).get(TikaCoreProperties.MODIFIED));
        assertEquals("Attachment Test", metadataList.get(0).get(TikaCoreProperties.TITLE));

        String content = metadataList.get(0).get(RecursiveParserWrapper.TIKA_CONTENT);
        assertContains("<p>Attachment Test</p>", content);
        assertContains("<div class=\"canvas\"><p>Different", content);

        //I'd want this to be "tika content", but copy+paste in Windows yields tikacontent
        assertContains("tikacontent", content);


        assertEquals("image/jpeg", metadataList.get(1).get(Metadata.CONTENT_TYPE));
    }

    @Test
    public void testVarious() throws Exception {
        List<Metadata> metadataList = getRecursiveMetadata("testXPS_various.xps");
        //confirm embedded images and thumbnails were extracted
        assertEquals(4, metadataList.size());

        //now check for content in the right order
        String quickBrownFox = "\u0644\u062B\u0639\u0644\u0628\u0020" +
                "\u0627\u0644\u0628\u0646\u064A\u0020" +
                "\u0627\u0644\u0633\u0631\u064A\u0639";

        String content = metadataList.get(0).get(RecursiveParserWrapper.TIKA_CONTENT);
        assertContains(quickBrownFox, content);

        assertContains("The \u0627\u0644\u0628\u0646\u064A fox", content);

        assertContains("\u0644\u062B\u0639\u0644\u0628 brown \u0627\u0644\u0633\u0631\u064A\u0639",
                content);

        //make sure the urls come through
        assertContains("<a href=\"http://tika.apache.org/\">http://tika.apache.org/</a>",
                content);

        Metadata metadata = metadataList.get(0);
        assertEquals("Allison, Timothy B.", metadata.get(TikaCoreProperties.CREATOR));
        assertEquals("2017-12-12T11:15:38Z", metadata.get(TikaCoreProperties.CREATED));
        assertEquals("2017-12-12T11:15:38Z", metadata.get(TikaCoreProperties.MODIFIED));


        assertEquals("image/png", metadataList.get(1).get(Metadata.CONTENT_TYPE));

        Metadata inlineJpeg = metadataList.get(2);
        assertEquals("image/jpeg", inlineJpeg.get(Metadata.CONTENT_TYPE));
        assertContains("INetCache", inlineJpeg.get(TikaCoreProperties.ORIGINAL_RESOURCE_NAME));
        assertEquals(TikaCoreProperties.EmbeddedResourceType.INLINE.toString(),
                inlineJpeg.get(TikaCoreProperties.EMBEDDED_RESOURCE_TYPE));

        assertEquals("image/jpeg", metadataList.get(3).get(Metadata.CONTENT_TYPE));
//        assertEquals(TikaCoreProperties.EmbeddedResourceType.THUMBNAIL.toString(),
  //              inlineJpeg.get(TikaCoreProperties.EMBEDDED_RESOURCE_TYPE));


    }

}
