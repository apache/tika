package org.apache.tika.language.translate;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertTrue;

public class MosesTranslatorTest {
    MosesTranslator translator;
    @Before
    public void setUp() {
        translator = new MosesTranslator();
    }

    @Test
    public void testSimpleTranslate() throws Exception {
        String source = "hola";
        String expected = "hello";
        String translated = translator.translate(source, "sp", "en");
        if (translator.isAvailable()) assertTrue("Translate " + source + " to " + expected + " (was " + translated + ")",
                expected.equalsIgnoreCase(translated));
    }
}
