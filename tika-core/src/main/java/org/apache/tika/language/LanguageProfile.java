/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.tika.language;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Language profile based on ngram counts.
 *
 * @since Apache Tika 0.5
 */
public class LanguageProfile {

    public static final int DEFAULT_NGRAM_LENGTH = 3;

    private final int length;

    /**
     * The ngrams that make up this profile.
     */
    private final Map<String, Counter> ngrams =
        new HashMap<String, Counter>();

    /**
     * The sum of all ngram counts in this profile.
     * Used to calculate relative ngram frequency.
     */
    private long count = 0;

    private class Counter {
        private long count = 0;
        public String toString() {
            return Long.toString(count);
        }
    }

    public LanguageProfile(int length) {
        this.length = length;
    }

    public LanguageProfile() {
        this(DEFAULT_NGRAM_LENGTH);
    }

    public LanguageProfile(String content, int length) {
        this(length);

        ProfilingWriter writer = new ProfilingWriter(this);
        char[] ch = content.toCharArray();
        writer.write(ch, 0, ch.length);
    }

    public LanguageProfile(String content) {
        this(content, DEFAULT_NGRAM_LENGTH);
    }

    public long getCount() {
        return count;
    }

    public long getCount(String ngram) {
        Counter counter = ngrams.get(ngram);
        if (counter != null) {
            return counter.count;
        } else {
            return 0;
        }
    }

    /**
     * Adds a single occurrence of the given ngram to this profile.
     *
     * @param ngram the ngram
     */
    public void add(String ngram) {
        add(ngram, 1);
    }

    /**
     * Adds multiple occurrences of the given ngram to this profile.
     *
     * @param ngram the ngram
     * @param count number of occurrences to add
     */
    public void add(String ngram, long count) {
        if (length != ngram.length()) {
            throw new IllegalArgumentException(
                    "Unable to add an ngram of incorrect length: "
                    + ngram.length() + " != " + length);
        }

        Counter counter = ngrams.get(ngram);
        if (counter == null) {
            counter = new Counter();
            ngrams.put(ngram, counter);
        }
        counter.count += count;
        this.count += count;
    }

    /**
     * Calculates the geometric distance between this and the given
     * other language profile.
     *
     * @param that the other language profile
     * @return distance between the profiles
     */
    public double distance(LanguageProfile that) {
        if (length != that.length) {
            throw new IllegalArgumentException(
                    "Unable to calculage distance of language profiles"
                    + " with different ngram lengths: "
                    + that.length + " != " + length);
        }

        double sumOfSquares = 0.0;
        double thisCount = Math.max(this.count, 1.0);
        double thatCount = Math.max(that.count, 1.0);

        Set<String> ngrams = new HashSet<String>();
        ngrams.addAll(this.ngrams.keySet());
        ngrams.addAll(that.ngrams.keySet());
        for (String ngram : ngrams) {
            double thisFrequency = this.getCount(ngram) / thisCount;
            double thatFrequency = that.getCount(ngram) / thatCount;
            double difference = thisFrequency - thatFrequency;
            sumOfSquares += difference * difference;
        }

        return Math.sqrt(sumOfSquares);
    }

    @Override
    public String toString() {
        return ngrams.toString();
    }

}
