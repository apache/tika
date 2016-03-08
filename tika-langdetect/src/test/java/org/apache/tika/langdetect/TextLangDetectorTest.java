package org.apache.tika.langdetect;

import org.apache.tika.io.IOUtils;
import org.apache.tika.language.detect.LanguageDetector;
import org.apache.tika.language.detect.LanguageResult;
import org.apache.tika.language.detect.LanguageWriter;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

/**
 * Created by trevorlewis on 3/7/16.
 */
public class TextLangDetectorTest {

    @Test
    public void test() throws Exception {
        LanguageDetector detector = new TextLangDetector();

        LanguageWriter writer = new LanguageWriter(detector);

        List<String> lines = IOUtils.readLines(TextLangDetectorTest.class.getResourceAsStream("text-test.tsv"));

        for (String line : lines) {
            String[] data = line.split("\t");
            if (data.length != 2) {
                continue;
            }

            writer.reset();
            writer.append(data[1]);

            LanguageResult result = detector.detect();
            assertNotNull(result);

            /*if (!data[0].equals(result.getLanguage())) {
                System.out.println(result.getLanguage() + " : " + data[0] + " - " + data[1]);
            }*/
            assertEquals(data[0], result.getLanguage());
        }

        writer.close();
    }
}
