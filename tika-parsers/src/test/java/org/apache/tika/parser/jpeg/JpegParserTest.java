package org.apache.tika.parser.jpeg;

import junit.framework.TestCase;
import org.apache.tika.parser.Parser;
import org.apache.tika.metadata.Metadata;
import org.xml.sax.helpers.DefaultHandler;

import java.io.InputStream;

public class JpegParserTest extends TestCase {
    private final Parser parser = new JpegParser();

    public void testJPEG() throws Exception {
        Metadata metadata = new Metadata();
        metadata.set(Metadata.CONTENT_TYPE, "image/jpeg");
        InputStream stream =
            getClass().getResourceAsStream("/test-documents/testJPEG_EXIF.jpg");
        parser.parse(stream, new DefaultHandler(), metadata);

        assertEquals("Canon EOS 40D", metadata.get("Model"));
    }

}
