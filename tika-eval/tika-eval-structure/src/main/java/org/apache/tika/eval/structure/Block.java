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
package org.apache.tika.eval.structure;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** One block-level unit of an extract: a paragraph, heading, list item, cell, or loose text. */
public final class Block {

    public enum Kind {
        P, H, LI, CELL, OTHER
    }

    final int index;
    final int page;
    final Kind kind;
    final boolean artifact;
    final boolean untagged;
    final List<String> tokens;
    private Map<String, Integer> counts;

    Block(int index, int page, Kind kind, boolean artifact, boolean untagged,
          List<String> tokens) {
        this.index = index;
        this.page = page;
        this.kind = kind;
        this.artifact = artifact;
        this.untagged = untagged;
        this.tokens = tokens;
    }

    /** Token multiset. */
    Map<String, Integer> counts() {
        if (counts == null) {
            Map<String, Integer> m = new HashMap<>();
            for (String t : tokens) {
                m.merge(t, 1, Integer::sum);
            }
            counts = m;
        }
        return counts;
    }

    int size() {
        return tokens.size();
    }

    @Override
    public String toString() {
        return "Block{" + index + " p" + page + " " + kind + " " + tokens + "}";
    }
}
