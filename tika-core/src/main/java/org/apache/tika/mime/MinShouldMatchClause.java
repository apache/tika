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
package org.apache.tika.mime;

import java.util.List;

class MinShouldMatchClause implements Clause {

    private final int min;
    private final List<Clause> clauses;

    /**
     * Minimum number of clauses that need to match.
     *
     * <p>Throws IllegalArgumentException if min <= 0, if clauses is null or has size == 0, or if
     * min > clauses.size()
     *
     * @param min
     * @param clauses
     */
    MinShouldMatchClause(int min, List<Clause> clauses) {

        if (clauses == null || clauses.size() == 0) {
            throw new IllegalArgumentException("clauses must be not null with size > 0");
        }

        if (min > clauses.size()) {
            throw new IllegalArgumentException(
                    "min (" + min + ") cannot be > clauses.size (" + clauses.size() + ")");
        } else if (min <= 0) {
            throw new IllegalArgumentException("min cannot be <= 0: " + min);
        }

        this.min = min;
        this.clauses = clauses;
    }

    public boolean eval(byte[] data) {
        int matches = 0;
        for (Clause clause : clauses) {
            if (clause.eval(data)) {
                if (++matches >= min) {
                    return true;
                }
            }
        }
        return false;
    }

    public int size() {
        int size = 0;
        for (Clause clause : clauses) {
            size = Math.max(size, clause.size());
        }
        return size;
    }

    public String toString() {
        return "minShouldMatch (min: " + min + ") " + clauses;
    }
}
