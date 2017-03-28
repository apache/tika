package org.apache.tika.parser.crypto;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.InputStream;

import org.apache.tika.TikaTest;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.BodyContentHandler;
import org.xml.sax.ContentHandler;

public class TSDParserTest extends TikaTest {
    public void testDetachedSignature() throws Exception {
        try (InputStream input = TSDParserTest.class.getResourceAsStream(
                "/test-documents/MANIFEST.XML.TSD")) {
            ContentHandler handler = new BodyContentHandler();
            Metadata metadata = new Metadata();
            new TSDParser().parse(input, handler, metadata, new ParseContext());
        } catch (NullPointerException npe) {
            fail("should not get NPE");
        } catch (TikaException te) {
            assertTrue(te.toString().contains("cannot parse detached TSD file"));
        }
    }
}
