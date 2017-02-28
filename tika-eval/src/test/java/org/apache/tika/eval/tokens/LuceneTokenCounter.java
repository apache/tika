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

package org.apache.tika.eval.tokens;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.math3.stat.descriptive.SummaryStatistics;
import org.apache.commons.math3.util.FastMath;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.index.LeafReader;
import org.apache.lucene.index.Terms;
import org.apache.lucene.index.TermsEnum;
import org.apache.lucene.index.memory.MemoryIndex;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.util.BytesRef;

/**
 * Experimental class uses Lucene's MemoryIndex to effectively build the
 * token info.
 */
public class LuceneTokenCounter {
    private static final String ALPHA_IDEOGRAPH_SUFFIX = "_a";

    private final LeafReader leafReader;
    private final MemoryIndex memoryIndex;
    private final Analyzer generalAnalyzer;
    private int topN = 10;

    Map<String, TokenStatistics> fieldStats = new HashMap<>();

    public LuceneTokenCounter(Analyzer generalAnalyzer) throws IOException {
        memoryIndex = new MemoryIndex();
        IndexSearcher searcher = memoryIndex.createSearcher();
        leafReader = (LeafReader)searcher.getIndexReader();
        this.generalAnalyzer = generalAnalyzer;
    }

    public void add(String field, String content) throws IOException {
        memoryIndex.addField(field, content, generalAnalyzer);
        //memoryIndex.addField(field+ALPHA_IDEOGRAPH_SUFFIX,
        //        content, alphaIdeographAnalyzer);
        count(field);
        //count(field+ALPHA_IDEOGRAPH_SUFFIX);

    }


    void count(String field) throws IOException {
        long tokenCount = leafReader.getSumTotalTermFreq(field);
        if (tokenCount > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("can't handle longs");
        }
        int tokenCountInt = (int)tokenCount;
        int uniqueTokenCount = 0;
        SummaryStatistics summStats = new SummaryStatistics();
        double ent = 0.0d;
        double p = 0.0d;
        double base = 2.0;

        Terms terms = leafReader.terms(field);
        if (terms == null) {
            //if there were no terms
            fieldStats.put(field, new TokenStatistics(uniqueTokenCount, tokenCountInt,
                    new TokenIntPair[0], ent, summStats));
            return;

        }
        TermsEnum termsEnum = terms.iterator();
        BytesRef bytesRef = termsEnum.next();
        TokenCountPriorityQueue queue= new TokenCountPriorityQueue(topN);

        while (bytesRef != null) {

            long termFreq = termsEnum.totalTermFreq();
            if (termFreq > Integer.MAX_VALUE) {
                throw new IllegalArgumentException("Sorry can't handle longs yet");
            }
            int tf = (int)termFreq;
            //TODO: figure out how to avoid Stringifying this
            //to get codepoint count
            String t = bytesRef.utf8ToString();
            int len = t.codePointCount(0, t.length());
            for (int i = 0; i < tf; i++) {
                summStats.addValue(len);
            }
            p = (double) tf / (double) tokenCount;
            ent += p * FastMath.log(base, p);

            if (queue.top() == null || queue.size() < topN ||
                    tf >= queue.top().getValue()) {
                queue.insertWithOverflow(new TokenIntPair(t, tf));
            }

            uniqueTokenCount++;
            bytesRef = termsEnum.next();
        }
        if (tokenCountInt > 0) {
            ent = (-1.0d / (double)tokenCountInt) * ent;
        }

        fieldStats.put(field, new TokenStatistics(uniqueTokenCount, tokenCountInt,
                queue.getArray(), ent, summStats));
    }

    public void setTopN(int topN) {
        this.topN = topN;
    }

    public TokenStatistics getTokenStatistics(String field) {
        return fieldStats.get(field);
    }

    public Terms getTerms(String field) throws IOException {
        return leafReader.terms(field);
    }


    public void clear() {
        memoryIndex.reset();
        fieldStats.clear();
    }
/*
    public ContrastStatistics contrast(String fieldA, String fieldB) throws IOException {
        long diceDenom = getUniqueTokenCount(fieldA) +
                getUniqueTokenCount(fieldB);

        long diceNum = 0;
        long overlapNum = 0;

        Terms termsA = getTerms(fieldA);
        Terms termsB = getTerms(fieldB);

        TermsEnum termsEnumA = termsA.iterator();
        TermsEnum termsEnumB = termsB.iterator();

        BytesRef bytesRefA = termsEnumA.next();
        BytesRef bytesRefB = termsEnumB.next();

        while (bytesRefA != null) {
            int compare = bytesRefA.compareTo(bytesRefB);
            while (compare > 0) {
                if (bytesRefB == null) {
                    break;
                }
                //handle term in B, but not A

                compare = bytesRefA.compareTo(bytesRefB);
                bytesRefB = termsEnumB.next();
            }
            if (compare == 0) {
                diceNum += 2;
                overlapNum += 2 * Math.min(termsEnumA.totalTermFreq(), termsEnumB.totalTermFreq());
            }

            bytesRefA = termsEnumA.next();
        }


        for (PairCount p : tokens.values()) {
            if (p.a > 0 && p.b > 0) {
                diceNum += 2;
                overlapNum += 2 * Math.min(p.a, p.b);
            }
        }

        float dice = (float) diceNum / (float) diceDenom;
        float overlap = (float) overlapNum / (float) (theseTokens.getTokenCount() + thoseTokens.getTokenCount());
    }
*/
}
