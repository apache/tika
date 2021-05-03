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
package org.apache.tika.utils;

public class CompareUtils {

    /**
     * Compare two classes by class names.
     * If both classes are Tika's or both are not Tika's class, compare by name String.
     * Otherwise one of these two class is Tika's class.
     * Then the non-Tika's class comes before Tika's class.
     *
     * @param o1 the object 1 to be compared
     * @param o2 the object 2 to be compared
     * @return a negative integer, zero, or a positive integer
     */
    public static int compareClassName(Object o1, Object o2) {
        // Get class names.
        String n1 = o1.getClass().getName();
        String n2 = o2.getClass().getName();

        // Judge if they are Tika's class by name.
        boolean tika1 = n1.startsWith("org.apache.tika.");
        boolean tika2 = n2.startsWith("org.apache.tika.");

        // If both classes are Tika's class or both are not Tika's class,
        // compare by name String.
        if (tika1 == tika2) {
            return n1.compareTo(n2);
        }

        // Otherwise one of these two class is Tika's class.
        // Then the non-Tika's class comes before Tika's class.
        return tika1 ? 1 : -1;
    }
}
