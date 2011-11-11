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

/**
 * Defines a magic for a MimeType. A magic is made of one or several
 * MagicClause.
 * 
 * 
 */
class Magic implements Clause, Comparable<Magic> {

    private final MimeType type;

    private final int priority;

    private final Clause clause;

    private final String string;

    Magic(MimeType type, int priority, Clause clause) {
        this.type = type;
        this.priority = priority;
        this.clause = clause;
        this.string = "[" + priority + "/" + clause + "]";
    }

    MimeType getType() {
        return type;
    }

    int getPriority() {
        return priority;
    }

    public boolean eval(byte[] data) {
        return clause.eval(data);
    }

    public int size() {
        return clause.size();
    }

    public String toString() {
        return string;
    }

    public int compareTo(Magic o) {
        int diff = o.priority - priority;
        if (diff == 0) {
            diff = o.size() - size();
        }
        if (diff == 0) {
            diff = o.type.compareTo(type);
        }
        if (diff == 0) {
            diff = o.string.compareTo(string);
        }
        return diff;
    }

    public boolean equals(Object o) {
        if (o instanceof Magic) {
            Magic that = (Magic) o;
            return type.equals(that.type) && string.equals(that.string);
        }
        return false;
    }

    public int hashCode() {
        return type.hashCode() ^ string.hashCode();
    }

}
