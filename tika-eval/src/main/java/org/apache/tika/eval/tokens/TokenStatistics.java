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

import java.util.Arrays;

import org.apache.commons.math3.stat.descriptive.SummaryStatistics;


public class TokenStatistics {

    private final int totalTokens;
    private final int totalUniqueTokens;
    private final TokenIntPair[] topN;
    private final double entropy;
    private final SummaryStatistics summaryStatistics;

    public TokenStatistics(int totalUniqueTokens, int totalTokens,
                           TokenIntPair[] topN,
                           double entropy, SummaryStatistics summaryStatistics) {
        this.totalUniqueTokens = totalUniqueTokens;
        this.totalTokens = totalTokens;
        this.topN = topN;
        this.entropy = entropy;
        this.summaryStatistics = summaryStatistics;
    }


    public int getTotalTokens() {

        return totalTokens;
    }

    public int getTotalUniqueTokens() {
        return totalUniqueTokens;
    }

    public TokenIntPair[] getTopN() {
        return topN;
    }

    public double getEntropy() {
        return entropy;
    }

    public SummaryStatistics getSummaryStatistics() {
        return summaryStatistics;
    }


    @Override
    public String toString() {
        return "TokenStatistics{" +
                "totalTokens=" + totalTokens +
                ", totalUniqueTokens=" + totalUniqueTokens +
                ", topN=" + Arrays.toString(topN) +
                ", entropy=" + entropy +
                ", summaryStatistics=" + summaryStatistics +
                '}';
    }

    @Override
    public boolean equals(Object o) {

        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        TokenStatistics that = (TokenStatistics) o;

        if (totalTokens != that.totalTokens) return false;
        if (totalUniqueTokens != that.totalUniqueTokens) return false;
        if (!doubleEquals(that.entropy, entropy)) return false;
        // Probably incorrect - comparing Object[] arrays with Arrays.equals
        if (!Arrays.equals(topN, that.topN)) return false;

        SummaryStatistics thatS = ((TokenStatistics) o).summaryStatistics;
        if (summaryStatistics.getN() != thatS.getN()) return false;

        //if both have n==0, don't bother with the stats
        if (summaryStatistics.getN() ==0L) return true;
        //TODO: consider adding others...
        if (!doubleEquals(summaryStatistics.getGeometricMean(), thatS.getGeometricMean())) return false;
        if (!doubleEquals(summaryStatistics.getMax(), thatS.getMax())) return false;
        if (!doubleEquals(summaryStatistics.getMean(), thatS.getMean())) return false;
        if (!doubleEquals(summaryStatistics.getMin(), thatS.getMin())) return false;
        if (!doubleEquals(summaryStatistics.getSum(), thatS.getSum())) return false;
        if (!doubleEquals(summaryStatistics.getStandardDeviation(), thatS.getStandardDeviation())) return false;
        return true;
    }

    @Override
    public int hashCode() {
        int result;
        long temp;
        result = (int) (totalTokens ^ (totalTokens >>> 32));
        result = 31 * result + (int) (totalUniqueTokens ^ (totalUniqueTokens >>> 32));
        result = 31 * result + Arrays.hashCode(topN);
        temp = Double.doubleToLongBits(entropy);
        result = 31 * result + (int) (temp ^ (temp >>> 32));
        result = 31 * result + summaryStatistics.hashCode();
        return result;
    }

    private static boolean doubleEquals(double a, double b) {
        return doubleEquals(a, b, 0.000000000001d);
    }

    private static boolean doubleEquals(double a, double b, double epsilon) {
        return a == b ? true : Math.abs(a - b) < epsilon;
    }


}
