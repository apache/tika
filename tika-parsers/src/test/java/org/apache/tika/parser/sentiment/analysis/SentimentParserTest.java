package org.apache.tika.parser.sentiment.analysis;

import org.apache.tika.Tika;
import org.apache.tika.config.TikaConfig;
import org.apache.tika.metadata.Metadata;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.Charset;

import static org.junit.Assert.*;

/**
 * Test case for {@link SentimentParser}
 */
public class SentimentParserTest {

    @Test
    public void endToEndTest() throws Exception {

        Tika tika;
        try (InputStream confStream = getClass().getResourceAsStream("tika-config-sentiment-opennlp.xml")) {
            assert confStream != null;
            TikaConfig config = new TikaConfig(confStream);
            tika = new Tika(config);
        }
        String text = "What a wonderful thought it is that" +
                " some of the best days of our lives haven't happened yet.";
        ByteArrayInputStream stream = new ByteArrayInputStream(text.getBytes(Charset.defaultCharset()));
        Metadata md = new Metadata();
        tika.parse(stream, md);
        String sentiment = md.get("Sentiment");
        assertNotNull(sentiment);
        assertEquals(sentiment, "positive");

    }
}