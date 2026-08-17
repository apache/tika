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
 * Parsing intent, set in the ParseContext before detection.
 * <p>
 * {@link #WILL_PARSE} signals detectors that parsing will follow, so they may
 * prepare durable state for the parser (e.g. salvage a corrupted ZIP into the
 * open container). Set automatically by {@link AutoDetectParser}.
 * <p>
 * Consumers must compare against a specific constant, never test for mere
 * presence, so that new intents can be added without changing their behavior.
 */
public enum ParsingIntent {

    /** Parsing will follow detection. */
    WILL_PARSE
}
