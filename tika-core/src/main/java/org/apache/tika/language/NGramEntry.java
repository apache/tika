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

/**
 * NGram entry.
 */
class NGramEntry implements Comparable<NGramEntry> {

    /**
     * The ngram profile this entry is related to
     */
    private final NGramProfile profile;

    /**
     * The sequence of characters of the ngram
     */
    private CharSequence seq = null;

    /**
     * The number of occurrences of this ngram in its profile
     */
    private int count = 0;

    /**
     * The frequency of this ngram in its profile. Calculated by the
     * {@link #calculateFrequency(int)} method.
     */
    private float frequency = 0.0F;

    /** 
     * Constructs a new NGramEntry
     * @param seq is the sequence of characters of the ngram
     * @param nGramProfile TODO
     */
    public NGramEntry(NGramProfile nGramProfile, CharSequence seq) {
        this.profile = nGramProfile;
        this.seq = seq;
    }

    /** 
     * Constructs a new NGramEntry
     * @param seq is the sequence of characters of the ngram
     * @param count is the number of occurrences of this ngram
     * @param nGramProfile TODO
     */
    public NGramEntry(NGramProfile nGramProfile, String seq, int count) {
        this.profile = nGramProfile;
        this.seq = new StringBuffer(seq).subSequence(0, seq.length());
        this.count = count;
    }

    /**
     * Returns the number of occurrences of this ngram in its profile
     * @return the number of occurrences of this ngram in its profile
     */
    public int getCount() {
        return count;
    }

    /**
     * Returns the frequency of this ngram in its profile
     * @return the frequency of this ngram in its profile
     */
    public float getFrequency() {
        return frequency;
    }

    public void calculateFrequency(int totalCount) {
        frequency = (float) count / (float) totalCount;
    }

    /**
     * Returns the sequence of characters of this ngram
     * @return the sequence of characters of this ngram
     */
    public CharSequence getSeq() {
        return seq;
    }

    /**
     * Returns the size of this ngram
     * @return the size of this ngram
     */
    public int size() {
        return seq.length();
    }

    // Inherited JavaDoc
    public int compareTo(NGramEntry ngram) {
        int diff = Float.compare(ngram.getFrequency(), frequency);
        if (diff != 0) {
            return diff;
        } else {
            return (toString().compareTo(ngram.toString()));
        }
    }

    /**
     * Increments the number of occurrences of this ngram.
     */
    public void inc() {
        count++;
    }

    /**
     * Returns the profile associated to this ngram
     * @return the profile associated to this ngram
     */
    public NGramProfile getProfile() {
        return profile;
    }

    public String toString() {
        return "ngram(" + seq + "," + count + "," + frequency + ")";
    }

    // Inherited JavaDoc
    public int hashCode() {
        return seq.hashCode();
    }

    // Inherited JavaDoc
    public boolean equals(Object obj) {
        NGramEntry ngram = null;
        try {
            ngram = (NGramEntry) obj;
            return ngram.seq.equals(seq);
        } catch (Exception e) {
            return false;
        }
    }

}