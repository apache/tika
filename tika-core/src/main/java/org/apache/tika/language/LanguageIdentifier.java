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

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Properties;
import java.util.Vector;

/**
 * Identify the language of a content, based on statistical analysis.
 *
 * @see <a href="http://www.w3.org/WAI/ER/IG/ert/iso639.htm">ISO 639
 *      Language Codes</a>
 * 
 * @author Sami Siren
 * @author J&eacute;r&ocirc;me Charron
 */
public class LanguageIdentifier {

    private final static int DEFAULT_ANALYSIS_LENGTH = 0;    // 0 means full content

    private ArrayList<NGramProfile> languages = new ArrayList<NGramProfile>();

    private ArrayList<String> supportedLanguages = new ArrayList<String>();

    /** Minimum size of NGrams */
    private int minLength = NGramProfile.DEFAULT_MIN_NGRAM_LENGTH;

    /** Maximum size of NGrams */
    private int maxLength = NGramProfile.DEFAULT_MAX_NGRAM_LENGTH;

    /** The maximum amount of data to analyze */
    private int analyzeLength = DEFAULT_ANALYSIS_LENGTH;

    /** A global index of ngrams of all supported languages */
    private HashMap<CharSequence, NGramEntry[]> ngramsIdx = new HashMap<CharSequence, NGramEntry[]>();

    /** The NGramProfile used for identification */
    private NGramProfile suspect = null;

    /**
     * Constructs a new Language Identifier.
     */
    public LanguageIdentifier(NGramProfile suspect) {
        this.suspect = suspect;

        Properties p = new Properties();
        try {
            p.load(this.getClass().getResourceAsStream("langmappings.properties"));

            Enumeration alllanguages = p.keys();

            StringBuffer list = new StringBuffer("Language identifier plugin supports:");
            HashMap<NGramEntry, List<NGramEntry>> tmpIdx = new HashMap<NGramEntry, List<NGramEntry>>();
            while (alllanguages.hasMoreElements()) {
                String lang = (String) (alllanguages.nextElement());

                InputStream is = this.getClass().getClassLoader().getResourceAsStream(
                        "org/apache/tika/language/" + lang + "." + NGramProfile.FILE_EXTENSION);

                if (is != null) {
                    NGramProfile profile = new NGramProfile(lang, minLength, maxLength);
                    try {
                        profile.load(is);
                        languages.add(profile);
                        supportedLanguages.add(lang);
                        List<NGramEntry> ngrams = profile.getSorted();
                        for (int i=0; i<ngrams.size(); i++) {
                            NGramEntry entry = ngrams.get(i);
                            List<NGramEntry> registered = tmpIdx.get(entry);
                            if (registered == null) {
                                registered = new ArrayList<NGramEntry>();
                                tmpIdx.put(entry, registered);
                            }
                            registered.add(entry);
                        }
                        list.append(" " + lang + "(" + ngrams.size() + ")");
                        is.close();
                    } catch (IOException e1) {
                        // if (LOG.isFatalEnabled()) { LOG.fatal(e1.toString()); }
                    }
                }
            }
            // transform all ngrams lists to arrays for performances
            Iterator<NGramEntry> keys = tmpIdx.keySet().iterator();
            while (keys.hasNext()) {
                NGramEntry entry = keys.next();
                List<NGramEntry> l = tmpIdx.get(entry);
                if (l != null) {
                    NGramEntry[] array = l.toArray(new NGramEntry[l.size()]);
                    ngramsIdx.put(entry.getSeq(), array);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            // if (LOG.isFatalEnabled()) { LOG.fatal(e.toString()); }
        }
    }

    public LanguageIdentifier() {
        this(new NGramProfile(
                "suspect",
                NGramProfile.DEFAULT_MIN_NGRAM_LENGTH,
                NGramProfile.DEFAULT_MAX_NGRAM_LENGTH));
    }

    /**
     * Main method used for command line process.
     * <br/>Usage is:
     * <pre>
     * LanguageIdentifier [-identifyrows filename maxlines]
     *                    [-identifyfile charset filename]
     *                    [-identifyfileset charset files]
     *                    [-identifytext text]
     *                    [-identifyurl url]
     * </pre>
     * @param args arguments.
     */
    public static void main(String args[]) {
        String usage = "Usage: LanguageIdentifier" 
            + " [-identifyrows filename maxlines]"
            + " [-identifyfile charset filename]"
            + " [-identifyfileset charset files]"
            + " [-identifytext text] ";
        int command = 0;

        final int IDFILE = 1;
        final int IDTEXT = 2;
        final int IDFILESET = 4;
        final int IDROWS = 5;

        Vector<String> fileset = new Vector<String>();
        String filename = "";
        String charset = "";
        String text = "";
        int max = 0;

        if (args.length == 0) {
            System.err.println(usage);
            System.exit(-1);
        }

        for (int i = 0; i < args.length; i++) { // parse command line
            if (args[i].equals("-identifyfile")) {
                command = IDFILE;
                charset = args[++i];
                filename = args[++i];
            }

            if (args[i].equals("-identifyrows")) {
                command = IDROWS;
                filename = args[++i];
                max = Integer.parseInt(args[++i]);
            }

            if (args[i].equals("-identifytext")) {
                command = IDTEXT;
                for (i++; i < args.length - 1; i++)
                    text += args[i] + " ";
            }

            if (args[i].equals("-identifyfileset")) {
                command = IDFILESET;
                charset = args[++i];
                for (i++; i < args.length; i++) {
                    File[] files = null;
                    File f = new File(args[i]);
                    if (f.isDirectory()) {
                        files = f.listFiles();
                    } else {
                        files = new File[] { f };
                    }
                    for (int j=0; j<files.length; j++) {
                        fileset.add(files[j].getAbsolutePath());
                    }
                }
            }

        }

        String lang = null;
        //LanguageIdentifier idfr = LanguageIdentifier.getInstance();
        LanguageIdentifier idfr = new LanguageIdentifier();
        File f;
        FileInputStream fis;
        try {
            switch (command) {

            case IDTEXT:
                lang = idfr.identify(text);
                break;

            case IDFILE:
                f = new File(filename);
                fis = new FileInputStream(f);
                lang = idfr.identify(fis, charset);
                fis.close();
                break;

            case IDROWS:
                f = new File(filename);
                BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(f)));
                String line;
                while (max > 0 && (line = br.readLine()) != null) {
                    line = line.trim();
                    if (line.length() > 2) {
                        max--;
                        lang = idfr.identify(line);
                        System.out.println("R=" + lang + ":" + line);
                    }
                }

                br.close();
                System.exit(0);
                break;

            case IDFILESET:
                System.out.println("FILESET");
                Iterator<String> i = fileset.iterator();
                while (i.hasNext()) {
                    try {
                        filename = i.next();
                        f = new File(filename);
                        fis = new FileInputStream(f);
                        lang = idfr.identify(fis, charset);
                        fis.close();
                    } catch (Exception e) {
                        System.out.println(e);
                    }
                    System.out.println(filename + " was identified as " + lang);
                }
                System.exit(0);
                break;
            }
        } catch (Exception e) {
            System.out.println(e);
        }
        System.out.println("text was identified as " + lang);
    }

    /**
     * Identify language of a content.
     * 
     * @param content is the content to analyze.
     * @return The 2 letter
     *         <a href="http://www.w3.org/WAI/ER/IG/ert/iso639.htm">ISO 639
     *         language code</a> (en, fi, sv, ...) of the language that best
     *         matches the specified content.
     */
    public String identify(String content) {
        return identify(new StringBuilder(content));
    }

    /**
     * Identify language of a content.
     * 
     * @param content is the content to analyze.
     * @return The 2 letter
     *         <a href="http://www.w3.org/WAI/ER/IG/ert/iso639.htm">ISO 639
     *         language code</a> (en, fi, sv, ...) of the language that best
     *         matches the specified content.
     */
    public String identify(StringBuilder content) {
        StringBuilder text = content;
        if ((analyzeLength > 0) && (content.length() > analyzeLength)) {
            text = new StringBuilder().append(content);
            text.setLength(analyzeLength);
        }
        suspect.analyze(text);

        return identify();
    }

    public String identify() {
        Iterator<NGramEntry> iter = suspect.getSorted().iterator();
        float topscore = Float.MIN_VALUE;
        String lang = "";
        HashMap<NGramProfile, Float> scores = new HashMap<NGramProfile, Float>();
        NGramEntry searched = null;

        while (iter.hasNext()) {
            searched = iter.next();
            NGramEntry[] ngrams = ngramsIdx.get(searched.getSeq());
            if (ngrams != null) {
                for (int j=0; j<ngrams.length; j++) {
                    NGramProfile profile = ngrams[j].getProfile();
                    Float pScore = scores.get(profile);
                    if (pScore == null) {
                        pScore = new Float(0);
                    }
                    float plScore = pScore.floatValue();
                    plScore += ngrams[j].getFrequency() + searched.getFrequency();
                    scores.put(profile, new Float(plScore));
                    if (plScore > topscore) {
                        topscore = plScore;
                        lang = profile.getName();
                    }
                }
            }
        }
        return lang;
    }

    /**
     * Identify language from input stream.
     * This method uses the platform default encoding to read the input stream.
     * For using a specific encoding, use the
     * {@link #identify(InputStream, String)} method.
     *
     * @param is is the input stream to analyze.
     * @return The 2 letter
     *         <a href="http://www.w3.org/WAI/ER/IG/ert/iso639.htm">ISO 639
     *         language code</a> (en, fi, sv, ...) of the language that best
     *         matches the content of the specified input stream.
     * @throws IOException if something wrong occurs on the input stream.
     */
    public String identify(InputStream is) throws IOException {
        return identify(is, null);
    }

    /**
     * Identify language from input stream.
     * 
     * @param is is the input stream to analyze.
     * @param charset is the charset to use to read the input stream.
     * @return The 2 letter
     *         <a href="http://www.w3.org/WAI/ER/IG/ert/iso639.htm">ISO 639
     *         language code</a> (en, fi, sv, ...) of the language that best
     *         matches the content of the specified input stream.
     * @throws IOException if something wrong occurs on the input stream.
     */
    public String identify(InputStream is, String charset) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[2048];
        int len = 0;

        while (((len = is.read(buffer)) != -1) &&
                ((analyzeLength == 0) || (out.size() < analyzeLength))) {
            if (analyzeLength != 0) {
                len = Math.min(len, analyzeLength - out.size());
            }
            out.write(buffer, 0, len);
        }
        return identify((charset == null) ? out.toString()
                : out.toString(charset));
    }

}
