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
package org.apache.tika.parser.dwg;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;


public class DWGReadFormatRemoverTest {
    @Test
    public void testBasic()  {
        String formatted = "\\A1;\\fAIGDT|b0|i0;\\H2.5000;\\ln\\fArial|b0|i0;\\H2.5000;68{\\H1.3;\\S+0,8^+0,1;}";
        DWGReadFormatRemover dwgReadFormatter = new DWGReadFormatRemover();
        String expected = "n68+0,8/+0,1";
        assertEquals(expected, dwgReadFormatter.cleanupDwgString(formatted));
    }

    @Test
    public void testParameterizables()  {
        String formatted = "the quick \\A1;\\fAIGDT|b0|i0;\\H2.5000; brown fox";
        DWGReadFormatRemover dwgReadFormatter = new DWGReadFormatRemover();
        String expected = "the quick  brown fox";
        assertEquals(expected, dwgReadFormatter.cleanupDwgString(formatted));
    }
    @Test
    public void testEscapedSlashes()  {
        String formatted = "the quick \\\\ \\A3;\\fAIGDT|b0|i0;\\H2.5000;brown fox";
        DWGReadFormatRemover dwgReadFormatter = new DWGReadFormatRemover();
        String expected = "the quick \\ brown fox";
        assertEquals(expected, dwgReadFormatter.cleanupDwgString(formatted));
    }

    @Test
    public void testUnderlineEtc()  {
        String formatted = "l \\L open cu\\lrly bra\\Kck\\ket \\{ and a close " +
                "\\} right?";
        DWGReadFormatRemover dwgReadFormatter = new DWGReadFormatRemover();
        String expected = "l  open curly bracket { and a close } right?";
        assertEquals(expected, dwgReadFormatter.cleanupDwgString(formatted));

    }
    @Test
    public void testEscaped()  {
        String formatted = "then an actual \\P open curly bracket \\{ and a close \\} right?";
        DWGReadFormatRemover dwgReadFormatter = new DWGReadFormatRemover();
        String expected = "then an actual \n open curly bracket { and a close } right?";
        assertEquals(expected, dwgReadFormatter.cleanupDwgString(formatted));
    }

    @Test
    public void testStackedFractions()  {
        String formatted = "abc \\S+0,8^+0,1; efg";
        DWGReadFormatRemover dwgReadFormatter = new DWGReadFormatRemover();
        String expected = "abc +0,8/+0,1 efg";
        assertEquals(expected, dwgReadFormatter.cleanupDwgString(formatted));
    }

}
