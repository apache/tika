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
package org.apache.tika.parser.pdf;

import java.io.Serializable;

/**
 * How the parser uses a PDF's structure tree (marked content / tagged PDF).
 * See {@link PDFMarkedContent2XHTML}.
 */
public class MarkedContentConfig implements Serializable {

    private static final long serialVersionUID = 1L;

    public enum Strategy {
        /** Structure tags on the pages that pass the per-page gate; the text stripper elsewhere. */
        AUTO,
        /** Structure tags on every page that has any; only pages with no tagged text fall back. */
        TAGS,
        /** The text stripper only. */
        NONE
    }

    private Strategy strategy = Strategy.NONE;

    private float minCoverage = 0.5f;

    private float maxDanglingRatio = 0.2f;

    public Strategy getStrategy() {
        return strategy;
    }

    public void setStrategy(Strategy strategy) {
        this.strategy = strategy == null ? Strategy.NONE : strategy;
    }

    public float getMinCoverage() {
        return minCoverage;
    }

    /**
     * AUTO gate: the smallest fraction of a page's text (artifacts included) that the
     * structure tree must claim for the page to use tags. Above 1 every page falls back.
     */
    public void setMinCoverage(float minCoverage) {
        this.minCoverage = minCoverage;
    }

    public float getMaxDanglingRatio() {
        return maxDanglingRatio;
    }

    /**
     * AUTO gate: the largest fraction of the tree's leaves for a page that may reference
     * content the page does not have.
     */
    public void setMaxDanglingRatio(float maxDanglingRatio) {
        this.maxDanglingRatio = maxDanglingRatio;
    }
}
