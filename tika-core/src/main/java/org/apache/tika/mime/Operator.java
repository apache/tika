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
 * Defines a Boolean Binary Operator.
 */
interface Operator {

    /**
     * Evaluates the specified bolean operands.
     * 
     * @param o1
     *            is the first boolean operand.
     * @param o2
     *            is the second boolean operand.
     * @return the value of this boolean operator applied on the specified
     *         boolean operands.
     */
    boolean eval(boolean o1, boolean o2);

    /** The AND Boolean operator */
    Operator AND = new Operator() {
        public boolean eval(boolean o1, boolean o2) {
            return o1 && o2;
        }
        public String toString() {
            return "AND";
        }
    };

    /** The OR Boolean operator */
    Operator OR = new Operator() {
        public boolean eval(boolean o1, boolean o2) {
            return o1 || o2;
        }
        public String toString() {
            return "OR";
        }
    };

}
