package org.apache.tika.dl.text.sentencode;

import static org.junit.Assert.assertTrue;

import org.apache.tika.Tika;
import org.apache.tika.config.TikaConfig;
import org.apache.tika.metadata.Metadata;

import org.junit.Test;

import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.linalg.ops.transforms.Transforms;

import org.tensorflow.SavedModelBundle;
import org.tensorflow.Session;
import org.tensorflow.TensorFlow;
import org.tensorflow.Tensors;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Collectors;

import static java.nio.charset.StandardCharsets.UTF_8;

public class TFUniSentEncoderTest {
    @Test
    public void test2() throws Exception {

        System.out.println(TensorFlow.version());
        try (SavedModelBundle savedModel = SavedModelBundle.load("/home/thejan/Desktop/DL-tutz/keras-tests/tweaks/text-sim/001", "serve")) {

            // create the session from the Bundle
            Session sess = savedModel.session();

            String[] m = {
                    // Smartphones
                    "I like my phone",
                    "My phone is not good.",
                    "Your cellphone looks great.",

                    // Weather
                    "Will it snow tomorrow?",
                    "It is going to rain",
                    "Recently a lot of hurricanes have hit the US",
                    "Global warming is real",

                    // Food and health
                    "An apple a day, keeps the doctors away",
                    "Eating strawberries is healthy",
                    "Is paleo better than keto?",

                    // Asking about age
                    "How old are you?",
                    "what is your age?",
                    "you look young. How old are you?"
            };

            int textCount = m.length;

            byte[][] msgs = new byte[textCount][];

            for (int i = 0; i < textCount; i++) {
                msgs[i] = m[i].getBytes(UTF_8);
            }

            float[][] encodings = sess.runner()
                    .feed("sentences", Tensors.create(msgs))
                    .fetch("encodings:0")
                    .run()
                    .get(0)
                    .copyTo(new float[textCount][512]);

            Map<String, Double> corrs = new HashMap<>();
            for (int i = 0; i < encodings.length - 1; i++) {
                for (int j = i + 1; j < encodings.length; j++) {
                    String indexes = i + "-" + j;
                    double cosineSim = Transforms.cosineSim(Nd4j.create(encodings[i]), Nd4j.create(encodings[j]));
                    corrs.put(indexes, cosineSim);
                }
            }

            Map<String, Double> sortedCorrs = corrs.entrySet().stream()
                    .sorted(Collections.reverseOrder(Map.Entry.comparingByValue()))
                    .collect(Collectors.toMap(Entry::getKey, Entry::getValue, (e1, e2) -> e1, LinkedHashMap::new));
            System.out.println(sortedCorrs);

            for (int i = 0; i < 12; i++) {
                String k = (String) sortedCorrs.keySet().toArray()[i];
                double v = sortedCorrs.get(k);

                int[] two_ks = Arrays.stream(k.split("-")).mapToInt(Integer::parseInt).toArray();
                System.out.println(m[two_ks[0]] + " -- " + m[two_ks[1]] + " : " + v);
            }
        }
    }
}
