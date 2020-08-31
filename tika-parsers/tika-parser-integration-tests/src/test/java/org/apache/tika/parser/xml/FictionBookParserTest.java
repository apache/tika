package org.apache.tika.parser.xml;

import org.apache.tika.TikaTest;
import org.apache.tika.extractor.ContainerExtractor;
import org.apache.tika.extractor.ParserContainerExtractor;
import org.apache.tika.io.TikaInputStream;
import org.junit.Test;

import java.io.InputStream;

import static org.junit.Assert.assertEquals;

public class FictionBookParserTest {

    //not sure why this isn't passing
    @Test
    public void testEmbedded() throws Exception {
        try (InputStream input = FictionBookParserTest.class.getResourceAsStream("/test-documents/test.fb2")) {
            ContainerExtractor extractor = new ParserContainerExtractor();
            TikaInputStream stream = TikaInputStream.get(input);

            assertEquals(true, extractor.isSupported(stream));

            // Process it
            TikaTest.TrackingHandler handler = new TikaTest.TrackingHandler();
            extractor.extract(stream, null, handler);

            assertEquals(2, handler.filenames.size());
        }
    }
}
