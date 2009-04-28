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
 * Defines a MagicClause.
 * 
 */
class MagicClause implements Clause {

    private Operator op = null;

    private Clause c1 = null;

    private Clause c2 = null;

    private int size = 0;

    MagicClause(Operator op, Clause c1, Clause c2) {
        this.op = op;
        this.c1 = c1;
        this.c2 = c2;
        this.size = c1.size() + c2.size();
    }

    public boolean eval(byte[] data) {
        return op.eval(c1.eval(data), c2.eval(data));
    }

    public int size() {
        return size;
    }

    public String toString() {
        return new StringBuffer().append("(").append(c1).append(" ").append(op)
                .append(" ").append(c2).append(")").toString();
    }
}
