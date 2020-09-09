package org.apache.tika.parser.mail;

import org.apache.tika.TikaTest;
import org.apache.tika.detect.TypeDetector;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.parser.mbox.MboxParser;
import org.apache.tika.sax.BodyContentHandler;
import org.junit.Before;
import org.junit.Test;
import org.xml.sax.ContentHandler;

import java.io.InputStream;

import static org.junit.Assert.assertEquals;

public class MboxParserTest extends TikaTest {

    protected ParseContext recursingContext;
    private Parser autoDetectParser;
    private TypeDetector typeDetector;
    private MboxParser mboxParser;

    @Before
    public void setUp() throws Exception {
        typeDetector = new TypeDetector();
        autoDetectParser = new AutoDetectParser(typeDetector);
        recursingContext = new ParseContext();
        recursingContext.set(Parser.class, autoDetectParser);

        mboxParser = new MboxParser();
        mboxParser.setTracking(true);
    }
    @Test
    public void testOverrideDetector() throws Exception {
        ContentHandler handler = new BodyContentHandler();
        Metadata metadata = new Metadata();
        ParseContext context = new ParseContext();

        try (InputStream stream = getResourceAsStream("/test-documents/single_mail.mbox")) {
            mboxParser.parse(stream, handler, metadata, context);
        }

        Metadata firstMail = mboxParser.getTrackingMetadata().get(0);
        assertEquals("message/rfc822", firstMail.get(Metadata.CONTENT_TYPE));
    }
}
