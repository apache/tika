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
package org.apache.tika.ml.chardetect;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Pooled candidate from {@link LogLinearCombiner}: label, raw summed score
 * (larger is better, not normalized), and the specialists that contributed.
 */
public final class ScoredCandidate {

    private final String label;
    private final float score;
    private final Set<String> contributingSpecialists;

    public ScoredCandidate(String label, float score, Set<String> contributingSpecialists) {
        this.label = label;
        this.score = score;
        this.contributingSpecialists =
                Collections.unmodifiableSet(new LinkedHashSet<>(contributingSpecialists));
    }

    public String getLabel() {
        return label;
    }

    public float getScore() {
        return score;
    }

    public Set<String> getContributingSpecialists() {
        return contributingSpecialists;
    }

    @Override
    public String toString() {
        return "ScoredCandidate{" + label + "=" + score + " from " + contributingSpecialists + "}";
    }
}
