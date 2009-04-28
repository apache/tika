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
 * Defines a clause to be evaluated.
 * 
 * 
 */
interface Clause {

    /** A clause that is always true. */
    final static Clause TRUE = new True();

    /** A clause that is always false. */
    final static Clause FALSE = new False();

    /**
     * Evaluates this clause with the specified chunk of data.
     */
    public boolean eval(byte[] data);

    /**
     * Returns the size of this clause. The size of a clause is the number of
     * chars it is composed of.
     */
    public int size();

    final static class False implements Clause {
        public boolean eval(byte[] data) {
            return false;
        }

        public int size() {
            return 0;
        }

        public String toString() {
            return "FALSE";
        }
    }

    final static class True implements Clause {
        public boolean eval(byte[] data) {
            return true;
        }

        public int size() {
            return 0;
        }

        public String toString() {
            return "TRUE";
        }
    }

}
