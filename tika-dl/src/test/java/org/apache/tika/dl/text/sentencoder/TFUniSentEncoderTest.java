package org.apache.tika.dl.text.sentencoder;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Collectors;

import org.apache.tika.Tika;
import org.apache.tika.config.TikaConfig;
import org.apache.tika.exception.TikaConfigException;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.junit.Test;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.linalg.ops.transforms.Transforms;
import org.xml.sax.SAXException;

public class TFUniSentEncoderTest {

    @Test
    public void sentenceSimilarityTest() throws Exception {
        Tika tika = getTika("tf-uni-sent-encoder-config.xml");
        if (tika == null) {
            return;
        }

        String[] sentences = {
                // About age
                "How old are you?",
                "What is your age?",
                "How old did you turn?",
                "When is your birthday?",

                // About smart phones
                "The Samsung Galaxy S10 has the potential to be the most exciting phone of 2019",
                "Android beats iOS in smartphone loyalty, study finds",
                "IPhone X includes a 5.8-inch edge-to-edge display which covers the entire front of the phone.",
                "Apple became the world’s first trillion-dollar public company",

                // About weather
                "With roads covered with slippery snow and ice, can challenge even the most experienced driver.",
                "Heavy rain slammed the mid-Atlantic United States on Monday, delaying flights, forming sinkholes",
                "News showed, violent floodwaters surging down main Streets",
                "Recently a lot of hurricanes have hit the US",
                "Multiple lines of scientific evidence show that the climate system is warming",

                // About health
                "An ounce of prevention is worth a pound of cure",
                "Green tea contains bioactive compounds that improve health",
                "Yoga has been shown to help people reduce anxiety",
                "Is paleo better than keto?"
        };

        String text = String.join("\n", sentences);
        ByteArrayInputStream stream = new ByteArrayInputStream(text.getBytes(Charset.defaultCharset()));
        Metadata md = new Metadata();
        tika.parse(stream, md);
        String strEncodings = md.get("UniversalSentenceEncodings");
        float[][] encodings = string2array(strEncodings);
        // System.out.println(Arrays.deepToString(encodings));

        Map<String, Double> correlations = new HashMap<>();
        for (int i = 0; i < encodings.length - 1; i++) {
            for (int j = i + 1; j < encodings.length; j++) {
                String indexes = i + "-" + j;
                double cosineSim = Transforms.cosineSim(Nd4j.create(encodings[i]), Nd4j.create(encodings[j]));
                correlations.put(indexes, cosineSim);
            }
        }

        Map<String, Double> sortedCorrelations = correlations.entrySet().stream()
                .sorted(Collections.reverseOrder(Map.Entry.comparingByValue()))
                .collect(Collectors.toMap(Entry::getKey, Entry::getValue, (e1, e2) -> e1, LinkedHashMap::new));

        int i = 0;
        double corrThreshold = 0.347;
        while (true) {
            String k = (String) sortedCorrelations.keySet().toArray()[i++];
            int[] two_ks = Arrays.stream(k.split("-")).mapToInt(Integer::parseInt).toArray();
            double v = sortedCorrelations.get(k);

            if (v < corrThreshold) {
                break;
            } else {
                System.out.println(sentences[two_ks[0]]);
                System.out.println(sentences[two_ks[1]]);
                System.out.println(v);
                System.out.println("=====================================================================================");
            }
        }
    }

    private Tika getTika(String configXml) throws TikaException, SAXException, IOException {
        try (InputStream confStream = getClass().getResourceAsStream(configXml)) {
            assert confStream != null;
            TikaConfig config = new TikaConfig(confStream);
            return new Tika(config);
        } catch (TikaConfigException e) {
            //if can't connect to pull sentiment model...ignore test
            if (e.getCause() != null
                    && e.getCause() instanceof IOException) {
                return null;
            }
            throw e;
        }
    }

    private float[][] string2array(String array) {
        String[] arrays = array
                .replace("[[", "")
                .replace("]]", "")
                .replace("], [", "#")
                .split("#");
        List<float[]> tempResult = new ArrayList<>();
        for (String a : arrays) {
            String[] strFloatArray = a.split(", ");
            float[] floatArray = new float[strFloatArray.length];
            for (int i = 0; i < strFloatArray.length; i++) {
                floatArray[i] = Float.parseFloat(strFloatArray[i]);
            }
            tempResult.add(floatArray);
        }
        return tempResult.toArray(new float[tempResult.size()][]);
    }
}
