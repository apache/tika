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
package org.apache.tika.parser;

/**
 * Marker class to indicate parsing intent in ParseContext.
 * <p>
 * When set in the ParseContext before detection, this signals to detectors
 * that parsing will follow detection. Detectors can use this hint to perform
 * additional preparation (like salvaging corrupted ZIP files) that would
 * benefit the subsequent parse operation.
 * <p>
 * This is automatically set by {@link AutoDetectParser} before calling
 * the detector.
 */
public final class ParsingIntent {

    /**
     * Singleton instance indicating that parsing will follow detection.
     */
    public static final ParsingIntent WILL_PARSE = new ParsingIntent();

    private ParsingIntent() {
        // Private constructor for singleton
    }
}
