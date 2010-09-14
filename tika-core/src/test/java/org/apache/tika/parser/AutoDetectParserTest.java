package org.apache.tika.parser;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashSet;
import java.util.Set;

import junit.framework.Assert;
import junit.framework.TestCase;

import org.apache.tika.detect.Detector;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;
import org.apache.tika.sax.BodyContentHandler;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

public class AutoDetectParserTest extends TestCase {

    private static final MediaType MY_MEDIA_TYPE = new MediaType("application", "x-myparser");
    
    @SuppressWarnings("serial")
    private static class MyDetector implements Detector {

        public MediaType detect(InputStream input, Metadata metadata) throws IOException {
            return MY_MEDIA_TYPE;
        }
    }
    
    @SuppressWarnings("serial")
    private static class MyParser implements Parser {

        public Set<MediaType> getSupportedTypes(ParseContext context) {
            Set<MediaType> supportedTypes = new HashSet<MediaType>();
            supportedTypes.add(MY_MEDIA_TYPE);
            return supportedTypes;
        }

        public void parse(InputStream stream, ContentHandler handler, Metadata metadata, ParseContext context)
                throws IOException, SAXException, TikaException {
            metadata.add("MyParser", "value");
        }

        public void parse(InputStream stream, ContentHandler handler, Metadata metadata) throws IOException,
                SAXException, TikaException {
            parse(stream, handler, metadata, new ParseContext());
        }
    }
    
    
    /**
     * Test case for TIKA-514. Provide constructor for AutoDetectParser that has explicit
     * list of supported parsers.
     * @see <a href="https://issues.apache.org/jira/browse/TIKA-514">TIKA-514</a>
     */
    public void testSpecificParserList() throws Exception {
        AutoDetectParser parser = new AutoDetectParser(new MyDetector(), new MyParser());
        
        InputStream is = new ByteArrayInputStream("test".getBytes());
        Metadata metadata = new Metadata();
        parser.parse(is, new BodyContentHandler(), metadata, new ParseContext());
        
        Assert.assertEquals("value", metadata.get("MyParser"));
    }
}
