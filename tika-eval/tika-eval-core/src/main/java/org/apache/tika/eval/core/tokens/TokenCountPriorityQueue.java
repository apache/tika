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

import java.util.Comparator;
import java.util.PriorityQueue;

/**
 * Bounded min-heap that keeps the top-N TokenIntPairs by value.
 * Replaces the former Lucene PriorityQueue-based implementation.
 */
public class TokenCountPriorityQueue {

    private final int maxSize;
    // Min-heap: smallest value at head so we can evict it when full
    private final PriorityQueue<TokenIntPair> queue;

    TokenCountPriorityQueue(int maxSize) {
        this.maxSize = maxSize;
        this.queue = new PriorityQueue<>(maxSize + 1,
                Comparator.comparingLong(TokenIntPair::getValue)
                        .thenComparing(Comparator.comparing(TokenIntPair::getToken).reversed()));
    }

    public TokenIntPair top() {
        return queue.peek();
    }

    public int size() {
        return queue.size();
    }

    public void insertWithOverflow(TokenIntPair element) {
        if (queue.size() < maxSize) {
            queue.offer(element);
        } else if (queue.peek() != null && element.value > queue.peek().value) {
            queue.poll();
            queue.offer(element);
        }
    }

    public TokenIntPair pop() {
        return queue.poll();
    }

    public TokenIntPair[] getArray() {
        TokenIntPair[] topN = new TokenIntPair[queue.size()];
        // Pop all (ascending by value), then reverse to get descending
        int i = topN.length - 1;
        while (!queue.isEmpty() && i >= 0) {
            topN[i--] = queue.poll();
        }
        return topN;
    }
}
