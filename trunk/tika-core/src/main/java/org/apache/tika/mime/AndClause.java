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

import java.util.Arrays;

class AndClause implements Clause {

    private final Clause[] clauses;

    AndClause(Clause... clauses) {
        this.clauses = clauses;
    }

    public boolean eval(byte[] data) {
        for (Clause clause : clauses) {
            if (!clause.eval(data)) {
                return false;
            }
        }
        return true;
    }

    public int size() {
        int size = 0;
        for (Clause clause : clauses) {
            size += clause.size();
        }
        return size;
    }

    public String toString() {
        return "and" + Arrays.toString(clauses);
    }

}
