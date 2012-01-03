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

// JDK imports
import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import org.apache.tika.exception.TikaException;

/**
 * This class runs a ngram analysis over submitted text, results might be used
 * for automatic language identification.
 * 
 * The similarity calculation is at experimental level. You have been warned.
 * 
 * Methods are provided to build new NGramProfiles profiles.
 * 
 * @author Sami Siren
 * @author Jerome Charron - http://frutch.free.fr/
 */
public class LanguageProfilerBuilder {

    // public static final Log LOG =
    // LogFactory.getLog(LanguageProfilerBuilder.class);

    /** The minimum length allowed for a ngram. */
    final static int ABSOLUTE_MIN_NGRAM_LENGTH = 3; /* was 1 */

    /** The maximum length allowed for a ngram. */
    final static int ABSOLUTE_MAX_NGRAM_LENGTH = 3; /* was 4 */

    /** The default min length of ngram */
    final static int DEFAULT_MIN_NGRAM_LENGTH = 3;

    /** The default max length of ngram */
    final static int DEFAULT_MAX_NGRAM_LENGTH = 3;

    /** The ngram profile file extension */
    final static String FILE_EXTENSION = "ngp";

    /** The profile max size (number of ngrams of the same size) */
    final static int MAX_SIZE = 1000;

    /** separator char */
    final static char SEPARATOR = '_';
    /** The String form of the separator char */
    private final static String SEP_CHARSEQ = new String(
            new char[] { SEPARATOR });

    /** The profile's name */
    private String name = null;

    /** The NGrams of this profile sorted on the number of occurrences */
    private List<NGramEntry> sorted = null;

    /** The min length of ngram */
    private int minLength = DEFAULT_MIN_NGRAM_LENGTH;

    /** The max length of ngram */
    private int maxLength = DEFAULT_MAX_NGRAM_LENGTH;

    /** The total number of ngrams occurences */
    private int[] ngramcounts = null;

    /** An index of the ngrams of the profile */
    private Map<CharSequence, NGramEntry> ngrams = null;

    /** A StringBuffer used during analysis */
    private QuickStringBuffer word = new QuickStringBuffer();

    /**
     * Constructs a new ngram profile
     * 
     * @param name is the name of the profile
     * @param minlen is the min length of ngram sequences
     * @param maxlen is the max length of ngram sequences
     */
    public LanguageProfilerBuilder(String name, int minlen, int maxlen) {
        // TODO: Compute the initial capacity using minlen and maxlen.
        this.ngrams = new HashMap<CharSequence, NGramEntry>(4000);
        this.minLength = minlen;
        this.maxLength = maxlen;
        this.name = name;
    }
  
    /**
     * Constructs a new ngram profile where minlen=3, maxlen=3
     * 
     * @param name is a name of profile, usually two length string
     * @since Tika 1.0
     */
    public LanguageProfilerBuilder(String name) {
        this.ngrams = new HashMap<CharSequence, NGramEntry>(4000);
        this.minLength = ABSOLUTE_MIN_NGRAM_LENGTH;
        this.maxLength = ABSOLUTE_MAX_NGRAM_LENGTH;
        this.name = name;
    }

    /**
     * @return Returns the name.
     */
    public String getName() {
        return name;
    }
  
    // This method was commented because it depends on org.apache.lucene.analysis.Token
    // that is not a part of the Tika
    // /**
    // * Adds ngrams from a token to this profile
    // *
    // * @param t is the Token to be added
    // */
    // public void add(Token t) {
    // add(new StringBuffer().append(SEPARATOR)
    // .append(t.term())
    // .append(SEPARATOR));
    // }

    /**
     * Adds ngrams from a single word to this profile
     * 
     * @param word is the word to add
     */
    public void add(StringBuffer word) {
        for (int i = minLength; (i <= maxLength) && (i < word.length()); i++) {
            add(word, i);
        }
    }

    /**
     * Adds the last NGrams from the specified word.
     */
    private void add(QuickStringBuffer word) {
        int wlen = word.length();
        if (wlen >= minLength) {
            int max = Math.min(maxLength, wlen);
            for (int i = minLength; i <= max; i++) {
                add(word.subSequence(wlen - i, wlen));
            }
        }
    }

    /**
     * Adds ngrams from a single word in this profile
     * 
     * @param word is the word to add
     * @param n is the ngram size
     */
    private void add(CharSequence cs) {

        if (cs.equals(SEP_CHARSEQ)) {
            return;
        }
        NGramEntry nge = ngrams.get(cs);
        if (nge == null) {
            nge = new NGramEntry(cs);
            ngrams.put(cs, nge);
        }
        nge.inc();
    }

    /**
     * Analyzes a piece of text
     * 
     * @param text
     *            the text to be analyzed
     */
    public void analyze(StringBuilder text) {

        if (ngrams != null) {
            ngrams.clear();
            sorted = null;
            ngramcounts = null;
        }

        word.clear().append(SEPARATOR);
        for (int i = 0; i < text.length(); i++) {
            char c = Character.toLowerCase(text.charAt(i));

            if (Character.isLetter(c)) {
                add(word.append(c));
            } else {
                // found word boundary
                if (word.length() > 1) {
                    // we have a word!
                    add(word.append(SEPARATOR));
                    word.clear().append(SEPARATOR);
                }
            }
        }

        if (word.length() > 1) {
            // we have a word!
            add(word.append(SEPARATOR));
        }
        normalize();
    }

    /**
     * @param word
     * @param n sequence length
     */
    private void add(StringBuffer word, int n) {
        for (int i = 0; i <= word.length() - n; i++) {
            add(word.subSequence(i, i + n));
        }
    }
    
    /**
     * Normalizes the profile (calculates the ngrams frequencies)
     */
    protected void normalize() {
        NGramEntry e = null;
        Iterator<NGramEntry> i = ngrams.values().iterator();

        // Calculates ngram count if not already done
        if (ngramcounts == null) {
            ngramcounts = new int[maxLength + 1];
            while (i.hasNext()) {
                e = i.next();
                ngramcounts[e.size()] += e.count;
            }
        }

        i = ngrams.values().iterator();
        while (i.hasNext()) {
            e = i.next();
            e.frequency = (float) e.count / (float) ngramcounts[e.size()];
        }
    }

    /**
     * Returns a sorted list of ngrams (sort done by 1. frequency 2. sequence)
     * 
     * @return sorted vector of ngrams
     */
    public List<NGramEntry> getSorted() {
        // make sure sorting is done only once
        if (sorted == null) {
            sorted = new ArrayList<NGramEntry>(ngrams.values());
            Collections.sort(sorted);

            // trim at NGRAM_LENGTH entries
            if (sorted.size() > MAX_SIZE) {
                sorted = sorted.subList(0, MAX_SIZE);
            }
        }
        return sorted;
    }

    // Inherited JavaDoc
    public String toString() {

        StringBuffer s = new StringBuffer().append("NGramProfile: ")
                                           .append(name).append("\n");

        Iterator<NGramEntry> i = getSorted().iterator();

        while (i.hasNext()) {
            NGramEntry entry = i.next();
            s.append("[").append(entry.seq).append("/").append(entry.count)
                         .append("/").append(entry.frequency).append("]\n");
        }
        return s.toString();
    }

    /**
     * Calculates a score how well NGramProfiles match each other
     * 
     * @param another
     *            ngram profile to compare against
     * @return similarity 0=exact match
     * @throws TikaException
     *             if could not calculate a score
     */
    public float getSimilarity(LanguageProfilerBuilder another)
            throws TikaException {

        float sum = 0;

        try {
            Iterator<NGramEntry> i = another.getSorted().iterator();
            while (i.hasNext()) {
                NGramEntry other = i.next();
                if (ngrams.containsKey(other.seq)) {
                    sum += Math.abs((other.frequency - ngrams.get(other.seq).frequency)) / 2;
                } else {
                    sum += other.frequency;
                }
            }
            i = getSorted().iterator();
            while (i.hasNext()) {
                NGramEntry other = i.next();
                if (another.ngrams.containsKey(other.seq)) {
                    sum += Math.abs((other.frequency - another.ngrams
                            .get(other.seq).frequency)) / 2;
                } else {
                    sum += other.frequency;
                }
            }
        } catch (Exception e) {
            throw new TikaException("Could not calculate a score how well NGramProfiles match each other");
        }
        return sum;
    }

    /**
     * Loads a ngram profile from an InputStream (assumes UTF-8 encoded content)
     * 
     * @param is the InputStream to read
     */
    public void load(InputStream is) throws IOException {

        ngrams.clear();
        ngramcounts = new int[maxLength + 1];
        BufferedReader reader = new BufferedReader(new InputStreamReader(is, "UTF-8"));
        String line = null;

        while ((line = reader.readLine()) != null) {

            // # starts a comment line
            if (line.charAt(0) != '#') {
                int spacepos = line.indexOf(' ');
                String ngramsequence = line.substring(0, spacepos).trim();
                int len = ngramsequence.length();
                if ((len >= minLength) && (len <= maxLength)) {
                    int ngramcount = Integer.parseInt(line.substring(spacepos + 1));
                    NGramEntry en = new NGramEntry(ngramsequence, ngramcount);
                    ngrams.put(en.getSeq(), en);
                    ngramcounts[len] += ngramcount;
                }
            }
        }
        normalize();
    }
    
    /**
     * Creates a new Language profile from (preferably quite large - 5-10k of
     * lines) text file
     * 
     * @param name to be given for the profile
     * @param is a stream to be read
     * @param encoding is the encoding of stream
     * 
     * @throws TikaException if could not create a language profile
     *  
     */
    public static LanguageProfilerBuilder create(String name, InputStream is, String encoding) throws TikaException {

        LanguageProfilerBuilder newProfile = new LanguageProfilerBuilder(name,
                ABSOLUTE_MIN_NGRAM_LENGTH, ABSOLUTE_MAX_NGRAM_LENGTH);
        BufferedInputStream bis = new BufferedInputStream(is);

        byte buffer[] = new byte[4096];
        StringBuilder text = new StringBuilder();
        int len;

        try {
            while ((len = bis.read(buffer)) != -1) {
                text.append(new String(buffer, 0, len, encoding));
            }
        } catch (IOException e) {
            throw new TikaException("Could not create profile, " + e.getMessage());
        }

        newProfile.analyze(text);
        return newProfile;
    }

    /**
     * Writes NGramProfile content into OutputStream, content is outputted with
     * UTF-8 encoding
     * 
     * @param os the Stream to output to
     * 
     * @throws IOException
     */
    public void save(OutputStream os) throws IOException {
        os.write(("# NgramProfile generated at " + new Date() + 
                  " for Apache Tika Language Identification\n").getBytes());

        // And then each ngram

        // First dispatch ngrams in many lists depending on their size
        // (one list for each size, in order to store MAX_SIZE ngrams for each
        // size of ngram)
        List<NGramEntry> list = new ArrayList<NGramEntry>();
        List<NGramEntry> sublist = new ArrayList<NGramEntry>();
        NGramEntry[] entries = ngrams.values().toArray(
                new NGramEntry[ngrams.size()]);
        for (int i = minLength; i <= maxLength; i++) {
            for (int j = 0; j < entries.length; j++) {
                if (entries[j].getSeq().length() == i) {
                    sublist.add(entries[j]);
                }
            }
            Collections.sort(sublist);
            if (sublist.size() > MAX_SIZE) {
                sublist = sublist.subList(0, MAX_SIZE);
            }
            list.addAll(sublist);
            sublist.clear();
        }
        for (int i = 0; i < list.size(); i++) {
            NGramEntry e = list.get(i);
            String line = e.toString() + " " + e.getCount() + "\n";
            os.write(line.getBytes("UTF-8"));
        }
        os.flush();
    }

    /**
     * main method used for testing only
     * 
     * @param args
     */
    public static void main(String args[]) {

        // -create he sample_he.txt utf-8

        String usage = "Usage: NGramProfile "
                + "[-create profilename filename encoding] "
                + "[-similarity file1 file2] "
                + "[-score profile-name filename encoding]";
        int command = 0;

        final int CREATE = 1;
        final int SIMILARITY = 2;
        final int SCORE = 3;

        String profilename = "";
        String filename = "";
        String filename2 = "";
        String encoding = "";

        if (args.length == 0) {
            System.err.println(usage);
            System.exit(-1);
        }

        for (int i = 0; i < args.length; i++) { // parse command line
            if (args[i].equals("-create")) { // found -create option
                command = CREATE;
                profilename = args[++i];
                filename = args[++i];
                encoding = args[++i];
            }

            if (args[i].equals("-similarity")) { // found -similarity option
                command = SIMILARITY;
                filename = args[++i];
                filename2 = args[++i];
                encoding = args[++i];
            }

            if (args[i].equals("-score")) { // found -Score option
                command = SCORE;
                profilename = args[++i];
                filename = args[++i];
                encoding = args[++i];
            }
        }

        try {

            switch (command) {

            case CREATE:

                File f = new File(filename);
                FileInputStream fis = new FileInputStream(f);
                LanguageProfilerBuilder newProfile = LanguageProfilerBuilder
                        .create(profilename, fis, encoding);
                fis.close();
                f = new File(profilename + "." + FILE_EXTENSION);
                FileOutputStream fos = new FileOutputStream(f);
                newProfile.save(fos);
                System.out.println("new profile " + profilename + "."
                        + FILE_EXTENSION + " was created.");
                break;

            case SIMILARITY:

                f = new File(filename);
                fis = new FileInputStream(f);
                newProfile = LanguageProfilerBuilder.create(filename, fis,
                        encoding);
                newProfile.normalize();

                f = new File(filename2);
                fis = new FileInputStream(f);
                LanguageProfilerBuilder newProfile2 = LanguageProfilerBuilder
                        .create(filename2, fis, encoding);
                newProfile2.normalize();
                System.out.println("Similarity is "
                        + newProfile.getSimilarity(newProfile2));
                break;

            case SCORE:
                f = new File(filename);
                fis = new FileInputStream(f);
                newProfile = LanguageProfilerBuilder.create(filename, fis,
                        encoding);

                f = new File(profilename + "." + FILE_EXTENSION);
                fis = new FileInputStream(f);
                LanguageProfilerBuilder compare = new LanguageProfilerBuilder(
                        profilename, DEFAULT_MIN_NGRAM_LENGTH,
                        DEFAULT_MAX_NGRAM_LENGTH);
                compare.load(fis);
                System.out.println("Score is "
                        + compare.getSimilarity(newProfile));
                break;

            }

        } catch (Exception e) {
            e.printStackTrace();
            // throw new TikaException("");
        }
    }

  
    /**
     * Inner class that describes a NGram
     */
    static class NGramEntry implements Comparable<NGramEntry> {

        /** The NGRamProfile this NGram is related to */
        private LanguageProfilerBuilder profile = null;

        /** The sequence of characters of the ngram */
        CharSequence seq = null;

        /** The number of occurences of this ngram in its profile */
        private int count = 0;

        /** The frequency of this ngram in its profile */
        private float frequency = 0.0F;

        /**
         * Constructs a new NGramEntry
         * 
         * @param seq is the sequence of characters of the ngram
         */
        public NGramEntry(CharSequence seq) {
            this.seq = seq;
        }

        /**
         * Constructs a new NGramEntry
         * 
         * @param seq is the sequence of characters of the ngram
         * @param count is the number of occurrences of this ngram
         */
        public NGramEntry(String seq, int count) {
            this.seq = new StringBuffer(seq).subSequence(0, seq.length());
            this.count = count;
        }

        /**
         * Returns the number of occurrences of this ngram in its profile
         * 
         * @return the number of occurrences of this ngram in its profile
         */
        public int getCount() {
            return count;
        }

        /**
         * Returns the frequency of this ngram in its profile
         * 
         * @return the frequency of this ngram in its profile
         */
        public float getFrequency() {
            return frequency;
        }

        /**
         * Returns the sequence of characters of this ngram
         * 
         * @return the sequence of characters of this ngram
         */
        public CharSequence getSeq() {
            return seq;
        }

        /**
         * Returns the size of this ngram
         * 
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
         * Associated a profile to this ngram
         * 
         * @param profile
         *            is the profile associated to this ngram
         */
        public void setProfile(LanguageProfilerBuilder profile) {
            this.profile = profile;
        }

        /**
         * Returns the profile associated to this ngram
         * 
         * @return the profile associated to this ngram
         */
        public LanguageProfilerBuilder getProfile() {
            return profile;
        }

        // Inherited JavaDoc
        public String toString() {
            return seq.toString();
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

    private static class QuickStringBuffer implements CharSequence {

        private char value[];

        private int count;

        QuickStringBuffer() {
            this(16);
        }

        QuickStringBuffer(char[] value) {
            this.value = value;
            count = value.length;
        }

        QuickStringBuffer(int length) {
            value = new char[length];
        }

        QuickStringBuffer(String str) {
            this(str.length() + 16);
            append(str);
        }

        public int length() {
            return count;
        }

        private void expandCapacity(int minimumCapacity) {
            int newCapacity = (value.length + 1) * 2;
            if (newCapacity < 0) {
                newCapacity = Integer.MAX_VALUE;
            } else if (minimumCapacity > newCapacity) {
                newCapacity = minimumCapacity;
            }

            char newValue[] = new char[newCapacity];
            System.arraycopy(value, 0, newValue, 0, count);
            value = newValue;
        }

        QuickStringBuffer clear() {
            count = 0;
            return this;
        }

        public char charAt(int index) {
            return value[index];
        }

        QuickStringBuffer append(String str) {
            if (str == null) {
                str = String.valueOf(str);
            }

            int len = str.length();
            int newcount = count + len;
            if (newcount > value.length) {
                expandCapacity(newcount);
            }
            str.getChars(0, len, value, count);
            count = newcount;
            return this;
        }

        QuickStringBuffer append(char c) {
            int newcount = count + 1;
            if (newcount > value.length) {
                expandCapacity(newcount);
            }
            value[count++] = c;
            return this;
        }

        public CharSequence subSequence(int start, int end) {
            return new String(value, start, end - start);
        }

        public String toString() {
            return new String(this.value);
        }
    }
}
