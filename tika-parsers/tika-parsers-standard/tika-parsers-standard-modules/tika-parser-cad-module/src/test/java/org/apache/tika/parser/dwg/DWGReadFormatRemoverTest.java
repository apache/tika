package org.apache.tika.parser.dwg;

import org.junit.jupiter.api.Test;


import static org.junit.jupiter.api.Assertions.assertEquals;

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
        DWGReadFormatRemover dwgReadFormatter= new DWGReadFormatRemover();
        String expected = "the quick  brown fox";
        assertEquals(expected, dwgReadFormatter.cleanupDwgString(formatted));
    }
    @Test
    public void testEscapedSlashes()  {
        String formatted = "the quick \\\\ \\A3;\\fAIGDT|b0|i0;\\H2.5000;brown fox";
        DWGReadFormatRemover dwgReadFormatter= new DWGReadFormatRemover();
        String expected = "the quick \\ brown fox";
        assertEquals(expected, dwgReadFormatter.cleanupDwgString(formatted));
    }

    @Test
    public void testUnderlineEtc()  {
        String formatted = "l \\L open cu\\lrly bra\\Kck\\ket \\{ and a close " +
                "\\} right?";
        DWGReadFormatRemover dwgReadFormatter= new DWGReadFormatRemover();
        String expected = "l  open curly bracket { and a close } right?";
        assertEquals(expected, dwgReadFormatter.cleanupDwgString(formatted));

    }
    @Test
    public void testEscaped()  {
        String formatted = "then an actual \\P open curly bracket \\{ and a close \\} right?";
        DWGReadFormatRemover dwgReadFormatter= new DWGReadFormatRemover();
        String expected = "then an actual \n open curly bracket { and a close } right?";
        assertEquals(expected, dwgReadFormatter.cleanupDwgString(formatted));
    }

    @Test
    public void testStackedFractions()  {
        String formatted = "abc \\S+0,8^+0,1; efg";
        DWGReadFormatRemover dwgReadFormatter= new DWGReadFormatRemover();
        String expected = "abc +0,8/+0,1 efg";
        assertEquals(expected, dwgReadFormatter.cleanupDwgString(formatted));
    }

}
