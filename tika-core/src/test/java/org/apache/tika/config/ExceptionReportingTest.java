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
package org.apache.tika.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import org.apache.tika.parser.ParseContext;

public class ExceptionReportingTest {

    @Test
    public void testDefaults() {
        ExceptionReporting defaults = new ExceptionReporting();
        assertEquals(ExceptionReporting.Level.FULL, defaults.getLevel());
        assertEquals(ExceptionReporting.UNLIMITED, defaults.getMaxLength());
        assertEquals(defaults, ExceptionReporting.get(null));
        assertEquals(defaults, ExceptionReporting.get(new ParseContext()));
        // fresh instance each call
        assertNotSame(ExceptionReporting.get(null), ExceptionReporting.get(null));
    }

    @Test
    public void invalidMaxLength() {
        assertThrows(IllegalArgumentException.class,
                () -> new ExceptionReporting(ExceptionReporting.Level.FULL, 0));
        assertThrows(IllegalArgumentException.class,
                () -> new ExceptionReporting(ExceptionReporting.Level.FULL, -2));
        assertThrows(NullPointerException.class, () -> new ExceptionReporting(null, -1));
    }
}
