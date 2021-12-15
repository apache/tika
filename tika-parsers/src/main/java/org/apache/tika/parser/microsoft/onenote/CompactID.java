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

package org.apache.tika.parser.microsoft.onenote;

class CompactID {
    char n;
    long guidIndex; //only occupies 24 bits
    ExtendedGUID guid;

    public char getN() {
        return n;
    }

    public CompactID setN(char n) {
        this.n = n;
        return this;
    }

    public long getGuidIndex() {
        return guidIndex;
    }

    public CompactID setGuidIndex(long guidIndex) {
        this.guidIndex = guidIndex;
        return this;
    }

    public ExtendedGUID getGuid() {
        return guid;
    }

    public CompactID setGuid(ExtendedGUID guid) {
        this.guid = guid;
        return this;
    }

    public String getCompactIDString() {
        return new StringBuilder().append(guid).append(", index=").append(guidIndex).append(", n=")
                .append((int) n).toString();
    }
}
