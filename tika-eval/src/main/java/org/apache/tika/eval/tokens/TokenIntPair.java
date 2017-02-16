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


public class TokenIntPair implements Comparable<TokenIntPair> {

    final String token;
    final int value;

    public TokenIntPair(String token, int value) {
        this.token = token;
        this.value = value;
    }

    public long getValue() {
        return value;
    }

    public String getToken() {
        return token;
    }


    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        TokenIntPair that = (TokenIntPair) o;

        if (value != that.value) return false;
        return token.equals(that.token);
    }

    @Override
    public int hashCode() {
        int result = token.hashCode();
        result = 31 * result + value;
        return result;
    }

    /**
     * Descending by value, ascending by token
     *
     * @param o other tokenlong pair
     * @return comparison
     */
    @Override
    public int compareTo(TokenIntPair o) {
        if (this.value > o.value) {
            return -1;
        } else if (this.value < o.value) {
            return 1;
        }
        return this.token.compareTo(o.token);
    }

    @Override
    public String toString() {
        return "TokenIntPair{" +
                "token='" + token + '\'' +
                ", value=" + value +
                '}';
    }
}
