/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.tika.langdetect.opennlp;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Map;

import opennlp.tools.langdetect.LanguageDetector;
import opennlp.tools.langdetect.LanguageDetectorModel;
import opennlp.tools.util.normalizer.AggregateCharSequenceNormalizer;
import opennlp.tools.util.normalizer.CharSequenceNormalizer;

/**
 * Implements learnable Language Detector.
 * <p>
 * Starts at the beginning of the charsequence and runs language
 * detection on chunks of text.  If the end of the
 * string is reached or there are {@link #minConsecImprovements}
 * consecutive predictions for the best language and the confidence
 * increases over those last predictions and if the difference
 * in confidence between the highest confidence language
 * and the second highest confidence language is greater than {@link #minDiff},
 * the language detector will stop and report the results.
 * </p>
 * <p>
 * The authors wish to thank Ken Krugler and
 * <a href="https://github.com/kkrugler/yalder">Yalder</a>}
 * for the inspiration for many of the design
 * components of this detector.
 * </p>
 */
class ProbingLanguageDetector implements LanguageDetector {

    /**
     * Default chunk size (in codepoints) to take from the
     * initial String
     */
    public static final int DEFAULT_CHUNK_SIZE = 300;

    /**
     * Default minimum consecutive improvements in confidence.
     * If the best language is the same over this many consecutive
     * probes, and if the confidence did not go down over those probes,
     * the detector stops early.
     */
    public static final int DEFAULT_MIN_CONSEC_IMPROVEMENTS = 2;

    /**
     * Default minimum difference in confidence between the language with
     * the highest confidence and the language with the second highest confidence.
     */
    public static final double DEFAULT_MIN_DIFF = 0.20;

    /**
     * Default absolute maximum length of the String (in codepoints) to process
     */
    public static final int DEFAULT_MAX_LENGTH = 10000;

    private static final String SPACE = " ";

    //size at which to break strings for detection (in codepoints)
    private int chunkSize = DEFAULT_CHUNK_SIZE;

    //require that the "best" language be the same
    //and that the confidence in that language increase over
    //this number of probes.
    private int minConsecImprovements = DEFAULT_MIN_CONSEC_IMPROVEMENTS;

    //Minimum difference in confidence between the best candidate
    //and the second best candidate
    private double minDiff = DEFAULT_MIN_DIFF;

    /**
     * Absolute maximum length (in codepoints) that will processed
     */
    private int maxLength = DEFAULT_MAX_LENGTH;

    private CharSequenceNormalizer normalizer;

    private LanguageDetectorModel model;

    /**
     * Initializes the current instance with a language detector model. Default feature
     * generation is used.
     *
     * @param model the language detector model
     */
    public ProbingLanguageDetector(LanguageDetectorModel model,
                                   CharSequenceNormalizer... normalizers) {
        this.model = model;
        this.normalizer = new AggregateCharSequenceNormalizer(normalizers);
    }

    @Override
    public opennlp.tools.langdetect.Language predictLanguage(CharSequence content) {
        return predictLanguages(content)[0];
    }

    @Override
    public opennlp.tools.langdetect.Language[] predictLanguages(CharSequence content) {
        //list of the languages that received the highest
        //confidence over the last n chunk detections
        LinkedList<opennlp.tools.langdetect.Language[]> predictions = new LinkedList();
        int start = 0;//where to start the next chunk in codepoints
        opennlp.tools.langdetect.Language[] currPredictions = null;
        //cache ngram counts across chunks
        Map<String, MutableInt> ngramCounts = new HashMap<>();
        CharIntNGrammer ngrammer = new CharIntNGrammer(1, 3);
        int nGrams = 0;
        while (true) {
            int actualChunkSize = (start + chunkSize > maxLength) ? maxLength - start : chunkSize;

            CSAndLength csAndLength = chunk(content, start, actualChunkSize);
            int[] chunk = csAndLength.normed.codePoints().toArray();
            if (csAndLength.originalLength == 0) {
                if (currPredictions == null) {
                    return predict(ngramCounts);
                } else {
                    return currPredictions;
                }
            }
            start += csAndLength.originalLength;
            ngrammer.reset(chunk);

            while (ngrammer.hasNext()) {
                String nGram = ngrammer.next();
                if (nGram.equals(SPACE)) {
                    continue;
                }
                MutableInt cnt = ngramCounts.get(nGram);
                if (cnt == null) {
                    ngramCounts.put(nGram, new MutableInt(1));
                } else {
                    cnt.increment();
                }
                if (++nGrams % 110 == 0) {
                    currPredictions = predict(ngramCounts);
                    if (seenEnough(predictions, currPredictions, ngramCounts)) {
                        return currPredictions;
                    }
                }
            }
        }
    }

    private opennlp.tools.langdetect.Language[] predict(Map<String, MutableInt> ngramCounts) {
        String[] allGrams = new String[ngramCounts.size()];
        float[] counts = new float[ngramCounts.size()];
        int i = 0;
        for (Map.Entry<String, MutableInt> e : ngramCounts.entrySet()) {
            allGrams[i] = e.getKey();
            // TODO -- once OPENNLP-1261 is fixed,
            // change this to e.getValue().getValue().
            counts[i] = 1;
            i++;
        }
        double[] eval = model.getMaxentModel().eval(allGrams, counts);
        opennlp.tools.langdetect.Language[] arr =
                new opennlp.tools.langdetect.Language[eval.length];
        for (int j = 0; j < eval.length; j++) {
            arr[j] = new opennlp.tools.langdetect.Language(model.getMaxentModel().getOutcome(j),
                    eval[j]);
        }

        Arrays.sort(arr, (o1, o2) -> Double.compare(o2.getConfidence(), o1.getConfidence()));
        return arr;
    }

    /**
     * Size in codepoints at which to chunk the
     * text for detection.
     *
     * @return the chunk size in codepoints
     */
    public int getChunkSize() {
        return chunkSize;
    }

    /**
     * Size in codepoints at which to chunk the
     * text for detection.
     *
     * @param chunkSize
     */
    public void setChunkSize(int chunkSize) {
        this.chunkSize = chunkSize;
    }

    /**
     * Number of consecutive improvements in the
     * confidence of the most likely language required
     * for this language detector to stop.
     *
     * @return the minimum consecutive improvements
     */
    public int getMinConsecImprovements() {
        return minConsecImprovements;
    }

    /**
     * Number of consecutive improvements in the
     * confidence of the most likely language required
     * for this language detector to stop.
     *
     * @param minConsecImprovements minimum consecutive improvements
     */
    public void setMinConsecImprovements(int minConsecImprovements) {
        this.minConsecImprovements = minConsecImprovements;
    }

    /**
     * The minimum difference between the highest confidence and the
     * second highest confidence required to stop.
     *
     * @return the minimum difference required
     */
    public double getMinDiff() {
        return minDiff;
    }

    /**
     * The minimum difference between the highest confidence and the
     * second highest confidence required to stop.
     * <p>
     * Throws {@link IllegalArgumentException} if &lt; 0.0
     *
     * @param minDiff
     */
    public void setMinDiff(double minDiff) {
        if (minDiff < 0.0) {
            throw new IllegalArgumentException("minDiff must be >= 0.0");
        }
        this.minDiff = minDiff;
    }

    /**
     * The absolute maximum length of the string (in codepoints)
     * to be processed.
     *
     * @return the absolute maximum length of the string (in codepoints)
     * to be processed.
     */
    public int getMaxLength() {
        return maxLength;
    }

    /**
     * The absolute maximum length of the string (in codepoints)
     * to be processed.
     *
     * @param maxLength
     */
    public void setMaxLength(int maxLength) {
        this.maxLength = maxLength;
    }

    /**
     * Set the normalizer used on each chunk
     *
     * @param normalizer
     */
    public void setNormalizer(CharSequenceNormalizer normalizer) {
        this.normalizer = normalizer;
    }

    @Override
    public String[] getSupportedLanguages() {
        int numberLanguages = model.getMaxentModel().getNumOutcomes();
        String[] languages = new String[numberLanguages];
        for (int i = 0; i < numberLanguages; i++) {
            languages[i] = model.getMaxentModel().getOutcome(i);
        }
        return languages;
    }

    /**
     * Override this for different behavior to determine if there is enough
     * confidence in the predictions to stop.
     *
     * @param predictionsQueue
     * @param newPredictions
     * @param ngramCounts
     * @return
     */
    boolean seenEnough(LinkedList<opennlp.tools.langdetect.Language[]> predictionsQueue,
                       opennlp.tools.langdetect.Language[] newPredictions,
                       Map<String, MutableInt> ngramCounts) {

        if (predictionsQueue.size() < minConsecImprovements) {
            predictionsQueue.add(newPredictions);
            return false;
        } else if (predictionsQueue.size() > minConsecImprovements) {
            predictionsQueue.removeFirst();
        }
        predictionsQueue.add(newPredictions);
        if (minDiff > 0.0 &&
                newPredictions[0].getConfidence() - newPredictions[1].getConfidence() < minDiff) {
            return false;
        }
        String lastLang = null;
        double lastConf = -1.0;
        //iterate through the last predictions
        //and check that the lang with the highest confidence
        //hasn't changed, and that the confidence in it
        //hasn't decreased
        for (opennlp.tools.langdetect.Language[] predictions : predictionsQueue) {
            if (lastLang == null) {
                lastLang = predictions[0].getLang();
                lastConf = predictions[0].getConfidence();
                continue;
            } else {
                if (!lastLang.equals(predictions[0].getLang())) {
                    return false;
                }
                if (lastConf > predictions[0].getConfidence()) {
                    return false;
                }
            }
            lastLang = predictions[0].getLang();
            lastConf = predictions[0].getConfidence();
        }
        return true;
    }

    private CSAndLength chunk(CharSequence content, int start, int chunkSize) {
        if (start == 0 && chunkSize > content.length()) {
            int length = content.codePoints().toArray().length;
            return new CSAndLength(normalizer.normalize(content), length);
        }
        int[] codepoints = content.codePoints().skip(start).limit(chunkSize).toArray();
        String chunk = new String(codepoints, 0, codepoints.length);
        return new CSAndLength(normalizer.normalize(chunk), codepoints.length);
    }

    private static class CSAndLength {
        private final CharSequence normed;
        private final int originalLength;

        public CSAndLength(CharSequence normed, int originalLength) {
            this.normed = normed;
            this.originalLength = originalLength;
        }
    }

    private static class CharIntNGrammer implements Iterator<String> {
        private final int minGram;
        private final int maxGram;
        private String next;
        private int pos = 0;
        private int[] buffer;
        private int currGram;

        CharIntNGrammer(int minGram, int maxGram) {
            this.minGram = minGram;
            this.maxGram = maxGram;
            this.currGram = minGram;
        }

        @Override
        public boolean hasNext() {
            return next != null;
        }

        @Override
        public String next() {
            String ret = next;
            currGram++;
            if (currGram > maxGram) {
                currGram = minGram;
                pos++;
                if (pos + maxGram < buffer.length) {
                    //lowercase the last character; we've already
                    //lowercased all previous chars
                    buffer[pos + maxGram] = Character.toLowerCase(buffer[pos + maxGram]);
                }
            }
            if (pos + currGram > buffer.length) {
                currGram = minGram;
                pos++;
            }
            if (pos >= buffer.length - 1) {
                next = null;
                return ret;
            } else {
                next = new String(buffer, pos, currGram);
                return ret;
            }
        }

        /**
         * @param chunk this is the chunk that will be ngrammed.  Note:
         *              The ngrammer will lowercase the codepoints in place!
         *              If you don't want the original data transformed,
         *              copy it before calling this!
         */
        void reset(int[] chunk) {
            next = null;
            pos = 0;
            currGram = minGram;
            buffer = chunk;
            if (buffer.length < minGram) {
                return;
            }
            int end = Math.min(buffer.length, maxGram);

            for (int i = 0; i < end; i++) {
                buffer[i] = Character.toLowerCase(buffer[i]);
            }
            if (buffer.length >= minGram) {
                next = new String(buffer, 0, minGram);
            }
        }
    }

    private static class MutableInt {
        private int i;

        MutableInt() {
            this(0);
        }

        MutableInt(int i) {
            this.i = i;
        }

        void increment() {
            i++;
        }
    }
}
