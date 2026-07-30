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
package org.apache.tika.parser.audio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

public class NumberAndTotalTest {

    @Test
    public void testPlainNumber() {
        NumberAndTotal value = NumberAndTotal.parse("3");
        assertEquals(3, value.number);
        assertNull(value.total);
    }

    @Test
    public void testCombinedForm() {
        NumberAndTotal value = NumberAndTotal.parse("3/12");
        assertEquals(3, value.number);
        assertEquals(12, value.total);
    }

    @Test
    public void testWhitespace() {
        NumberAndTotal value = NumberAndTotal.parse(" 1 / 2 ");
        assertEquals(1, value.number);
        assertEquals(2, value.total);
    }

    @Test
    public void testDegenerateForms() {
        assertNull(NumberAndTotal.parse(null));
        assertNull(NumberAndTotal.parse("   "));
        //non-numeric forms parse to nothing; the raw properties keep them
        assertNull(NumberAndTotal.parse("A1"));
        assertNull(NumberAndTotal.parse("3a/of twelve"));

        NumberAndTotal totalOnly = NumberAndTotal.parse("/12");
        assertNull(totalOnly.number);
        assertEquals(12, totalOnly.total);

        NumberAndTotal nonNumericTotal = NumberAndTotal.parse("3/of twelve");
        assertEquals(3, nonNumericTotal.number);
        assertNull(nonNumericTotal.total);

        NumberAndTotal zeroTotal = NumberAndTotal.parse("3/0");
        assertEquals(3, zeroTotal.number);
        assertNull(zeroTotal.total);
    }
}
