package org.apache.tika.langdetect.opennlp;

import org.apache.tika.io.IOUtils;
import org.apache.tika.langdetect.LanguageDetectorTest;
import org.apache.tika.language.detect.LanguageResult;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;

public class OpenNLPDetectorTest {

    static Map<String, String> OPTIMAIZE_TO_OPENNLP = new HashMap<>();

    @BeforeClass
    public static void setUp() {
        OPTIMAIZE_TO_OPENNLP.put("da", "dan");
        OPTIMAIZE_TO_OPENNLP.put("de", "deu");
        OPTIMAIZE_TO_OPENNLP.put("el", "ell");
        OPTIMAIZE_TO_OPENNLP.put("en", "eng");
        OPTIMAIZE_TO_OPENNLP.put("es", "spa");
        OPTIMAIZE_TO_OPENNLP.put("et", "est");
        OPTIMAIZE_TO_OPENNLP.put("fi", "fin");
        OPTIMAIZE_TO_OPENNLP.put("fr", "fra");
        OPTIMAIZE_TO_OPENNLP.put("it", "ita");
        OPTIMAIZE_TO_OPENNLP.put("ja", "jpn");
        OPTIMAIZE_TO_OPENNLP.put("lt", "lit");
        OPTIMAIZE_TO_OPENNLP.put("nl", "nld");
        OPTIMAIZE_TO_OPENNLP.put("pt", "por");
        OPTIMAIZE_TO_OPENNLP.put("sv", "swe");
        OPTIMAIZE_TO_OPENNLP.put("th", "tha");
        OPTIMAIZE_TO_OPENNLP.put("zh", "cmn");
    }

    @Test
    public void languageTests() throws Exception {
        OpenNLPDetector detector = new OpenNLPDetector();
        for (String lang : OPTIMAIZE_TO_OPENNLP.keySet()) {
            String openNLPLang = OPTIMAIZE_TO_OPENNLP.get(lang);
            detector.addText(getLangText(lang));
            List<LanguageResult> results = detector.detectAll();
            assertEquals(openNLPLang, results.get(0).getLanguage());
            detector.reset();
        }
    }

    private CharSequence getLangText(String lang) throws IOException {
        try (Reader reader = new InputStreamReader(
                LanguageDetectorTest.class.getResourceAsStream("language-tests/"+lang+".test")
                , StandardCharsets.UTF_8)) {
            return IOUtils.toString(reader);
        }
    }

}
