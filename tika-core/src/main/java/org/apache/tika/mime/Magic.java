/**
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

    private int priority = 50;

    private Clause clause = null;

    Magic(MimeType type) {
        this.type = type;
    }

    MimeType getType() {
        return type;
    }

    void setPriority(int priority) {
        this.priority = priority;
    }

    int getPriority() {
        return priority;
    }

    void setClause(Clause clause) {
        this.clause = clause;
    }

    public boolean eval(byte[] data) {
        return clause.eval(data);
    }

    public int size() {
        return clause.size();
    }

    public String toString() {
        StringBuffer buf = new StringBuffer();
        buf.append("[").append(priority).append("/").append(clause).append("]");
        return buf.toString();
    }

    public int compareTo(Magic o) {
        int diff = o.priority - priority;
        if (diff == 0) {
            diff = o.size() - size();
        }
        if (diff == 0) {
            diff = o.toString().compareTo(toString());
        }
        return diff;
    }

}
