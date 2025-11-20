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
package org.apache.tika.parser.txt;

/**
 * Abstract class for recognizing a single charset.
 * Part of the implementation of ICU's CharsetDetector.
 * <p>
 * Each specific charset that can be recognized will have an instance
 * of some subclass of this class.  All interaction between the overall
 * CharsetDetector and the stuff specific to an individual charset happens
 * via the interface provided here.
 * <p>
 * Instances of CharsetDetector DO NOT have or maintain
 * state pertaining to a specific match or detect operation.
 * The WILL be shared by multiple instances of CharsetDetector.
 * They encapsulate const charset-specific information.
 */
abstract class CharsetRecognizer {
    /**
     * Get the IANA name of this charset.
     *
     * @return the charset name.
     */
    abstract String getName();

    /**
     * Get the ISO language code for this charset.
     *
     * @return the language code, or <code>null</code> if the language cannot be determined.
     */
    public String getLanguage() {
        return null;
    }

    /**
     * Test the match of this charset with the input text data
     * which is obtained via the CharsetDetector object.
     *
     * @param det The CharsetDetector, which contains the input text
     *            to be checked for being in this charset.
     * @return A CharsetMatch object containing details of match
     * with this charset, or null if there was no match.
     */
    abstract CharsetMatch match(CharsetDetector det);

}
