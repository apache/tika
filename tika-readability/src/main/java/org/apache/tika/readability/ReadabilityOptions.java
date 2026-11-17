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
package org.apache.tika.readability;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/** Options mirroring Readability.js's constructor options. Zero means "use the default". */
public final class ReadabilityOptions {

    public static final int DEFAULT_MAX_ELEMS_TO_PARSE = 0;
    public static final int DEFAULT_N_TOP_CANDIDATES = 5;
    public static final int DEFAULT_CHAR_THRESHOLD = 500;

    private int maxElemsToParse = DEFAULT_MAX_ELEMS_TO_PARSE;
    private long maxTextScanned;
    private int nbTopCandidates = DEFAULT_N_TOP_CANDIDATES;
    private int charThreshold = DEFAULT_CHAR_THRESHOLD;
    private final List<String> classesToPreserve = new ArrayList<>();
    private boolean keepClasses;
    private boolean disableJsonLd;
    private Pattern allowedVideoRegex;
    private double linkDensityModifier;
    private String documentUri;

    /** Throw instead of parsing a document with more elements than this; 0 for no limit. */
    public ReadabilityOptions maxElemsToParse(int v) {
        maxElemsToParse = v > 0 ? v : DEFAULT_MAX_ELEMS_TO_PARSE;
        return this;
    }

    /**
     * Throw once the text read across all scoring passes exceeds this many characters; 0 for no
     * limit. Not in upstream: cost grows with element count times nesting depth, which
     * {@link #maxElemsToParse} cannot bound.
     */
    public ReadabilityOptions maxTextScanned(long v) {
        maxTextScanned = Math.max(0, v);
        return this;
    }

    public ReadabilityOptions nbTopCandidates(int v) {
        nbTopCandidates = v > 0 ? v : DEFAULT_N_TOP_CANDIDATES;
        return this;
    }

    /** Minimum article length in characters before the extraction is accepted. */
    public ReadabilityOptions charThreshold(int v) {
        charThreshold = v > 0 ? v : DEFAULT_CHAR_THRESHOLD;
        return this;
    }

    public ReadabilityOptions classesToPreserve(List<String> v) {
        classesToPreserve.clear();
        classesToPreserve.addAll(v);
        return this;
    }

    public ReadabilityOptions keepClasses(boolean v) {
        keepClasses = v;
        return this;
    }

    public ReadabilityOptions disableJsonLd(boolean v) {
        disableJsonLd = v;
        return this;
    }

    /** Embeds whose attributes match this survive cleaning; defaults to common video hosts. */
    public ReadabilityOptions allowedVideoRegex(Pattern v) {
        allowedVideoRegex = v;
        return this;
    }

    public ReadabilityOptions linkDensityModifier(double v) {
        linkDensityModifier = v;
        return this;
    }

    /** URI relative links resolve against; defaults to the jsoup document's location. */
    public ReadabilityOptions documentUri(String v) {
        documentUri = v;
        return this;
    }

    int getMaxElemsToParse() {
        return maxElemsToParse;
    }

    long getMaxTextScanned() {
        return maxTextScanned;
    }

    int getNbTopCandidates() {
        return nbTopCandidates;
    }

    int getCharThreshold() {
        return charThreshold;
    }

    List<String> getClassesToPreserve() {
        return classesToPreserve;
    }

    boolean isKeepClasses() {
        return keepClasses;
    }

    boolean isDisableJsonLd() {
        return disableJsonLd;
    }

    Pattern getAllowedVideoRegex() {
        return allowedVideoRegex;
    }

    double getLinkDensityModifier() {
        return linkDensityModifier;
    }

    String getDocumentUri() {
        return documentUri;
    }
}
