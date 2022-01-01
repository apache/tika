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

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class LangModel {
    public static final LangModel EMPTY_MODEL = new LangModel(-1);

    private final long totalTokens;
    private final double unseenPercentage;


    private Map<String, Double> percentages = new HashMap<>();
    private Map<String, Long> counts = new HashMap<>();

    public LangModel(long totalTokens) {
        this.totalTokens = totalTokens;
        this.unseenPercentage = (double) 1 / (double) totalTokens;
    }


    public boolean contains(String token) {
        return (percentages.containsKey(token));
    }

    public Set<String> getTokens() {
        return percentages.keySet();
    }

    public double getProbability(String token) {
        Double p = percentages.get(token);
        if (p != null) {
            return p;
        } else {
            return unseenPercentage;
        }
    }

    public void add(String t, long tf) {
        double p = (double) tf / (double) totalTokens;
        percentages.put(t, p);
        counts.put(t, tf);
    }

    public double getUnseenProbability() {
        return unseenPercentage;
    }

    public long getCount(String token) {
        Long cnt = counts.get(token);
        if (cnt == null) {
            return 0;
        } else {
            return cnt;
        }
    }

    public Map<String, Long> getCounts() {
        return counts;
    }
}
