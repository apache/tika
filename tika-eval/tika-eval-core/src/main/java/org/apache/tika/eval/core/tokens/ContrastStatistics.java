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

package org.apache.tika.eval.core.tokens;

public class ContrastStatistics {


    double diceCoefficient;
    double overlap;

    TokenIntPair[] topNUniqueA;
    TokenIntPair[] topNUniqueB;
    TokenIntPair[] topNMoreA;
    TokenIntPair[] topNMoreB;

    public double getDiceCoefficient() {
        return diceCoefficient;
    }

    void setDiceCoefficient(double diceCoefficient) {
        this.diceCoefficient = diceCoefficient;
    }

    public double getOverlap() {
        return overlap;
    }

    void setOverlap(double overlap) {
        this.overlap = overlap;
    }

    public TokenIntPair[] getTopNUniqueA() {
        return topNUniqueA;
    }

    void setTopNUniqueA(TokenIntPair[] topNUniqueA) {
        this.topNUniqueA = topNUniqueA;
    }

    public TokenIntPair[] getTopNUniqueB() {
        return topNUniqueB;
    }

    void setTopNUniqueB(TokenIntPair[] topNUniqueB) {
        this.topNUniqueB = topNUniqueB;
    }

    public TokenIntPair[] getTopNMoreA() {
        return topNMoreA;
    }

    void setTopNMoreA(TokenIntPair[] topNMoreA) {
        this.topNMoreA = topNMoreA;
    }

    public TokenIntPair[] getTopNMoreB() {
        return topNMoreB;
    }

    void setTopNMoreB(TokenIntPair[] topNMoreB) {
        this.topNMoreB = topNMoreB;
    }
}
