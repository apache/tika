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

import org.apache.commons.collections4.bloomfilter.BloomFilter;

/**
 * The set of "common tokens" for a single language, backed by a Bloom filter.
 * <p>
 * Membership is approximate: {@link #contains(String)} may occasionally return {@code true}
 * for a token that is not actually common (a false positive), but never returns {@code false}
 * for one that is. See {@link CommonTokensBloom} for the rationale and false-positive rate.
 */
public class LangModel {

    /** Model used when a language has no common-tokens resource; nothing is "common". */
    public static final LangModel EMPTY_MODEL = new LangModel(null);

    private final BloomFilter filter;

    public LangModel(BloomFilter filter) {
        this.filter = filter;
    }

    /**
     * @return {@code true} if {@code token} is (probably) one of the common tokens for this
     * language; {@code false} means it is definitely not.
     */
    public boolean contains(String token) {
        return filter != null && CommonTokensBloom.mightContain(filter, token);
    }
}
