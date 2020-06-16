/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.tika.parser;


import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.text.DateFormatSymbols;

import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

import org.apache.tika.TikaTest;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Ensure that our various Table-based formats produce consistent,
 *  broadly similar output.
 * This is mostly focused on the XHTML output
 */
public class TabularFormatsTest extends TikaTest {
    private static final Logger LOG = LoggerFactory.getLogger(TabularFormatsTest.class);

    protected static final String[] columnNames = new String[] {
         "recnum","square","desc","pctdone","pctincr",
         "date","datetime","time"
    };
    protected static final String[] columnLabels = new String[] {
        "Record Number","Square of the Record Number",
        "Description of the Row","Percent Done",
        "Percent Increment","date","datetime","time"    
    };

    // to prevent this build test from failing outside the english speaking world, we need to have
    // both local and english month names (testCSV uses english names, the other tests local names)
    private static final String[] SHORT_MONTHS_EXPR;
    static {
        String[] shortMonthsEnglish = new DateFormatSymbols(Locale.ENGLISH).getShortMonths();
        String[] shortMonthsLocal = new DateFormatSymbols(Locale.getDefault()).getShortMonths();
        List<String> shortMonthsExpr = new ArrayList();
        for (int i = 0; i < 12; ++i)
        {
            String expr = shortMonthsEnglish[i].toUpperCase(Locale.ENGLISH) + 
                          "|" +
                          shortMonthsEnglish[i];
            if (!shortMonthsEnglish[i].equals(shortMonthsLocal[i])) {
                expr += "|" + 
                        shortMonthsLocal[i].toUpperCase(Locale.getDefault()) +
                        "|"  + 
                        shortMonthsLocal[i];
            }
            LOG.info(expr);
            shortMonthsExpr.add(expr);
        }
        SHORT_MONTHS_EXPR = shortMonthsExpr.toArray(new String[0]);
    };

    /**
     * Expected values, by <em>column</em>
     */
    protected static final Object[][] table = new Object[][] {
        new String[] {
             "0","1","2","3","4","5","6","7","8","9","10"
        },
        new String[] {
             "0","1","4","9","16","25","36","49","64","81","100"
        },
        new String[] {}, // Generated later
        new String[] {
                "0%","10%","20%","30%","40%","50%",
                "60%","70%","80%","90%","100%"
        },
        new String[] {
                "","0.0%","50.0%","66.7%",
                "75.0%","80.0%","83.3%","85.7%",
                "87.5%","88.9%","90.0%"
        },
        new Pattern[] {
                Pattern.compile("0?1-01-1960"),
                Pattern.compile("0?2-01-1960"),
                Pattern.compile("17-01-1960"),
                Pattern.compile("22-03-1960"),
                Pattern.compile("13-09-1960"),
                Pattern.compile("17-09-1961"),
                Pattern.compile("20-07-1963"),
                Pattern.compile("29-07-1966"),
                Pattern.compile("20-03-1971"),
                Pattern.compile("18-12-1977"),
                Pattern.compile("19-05-1987"),
        },
        new Pattern[] {
             Pattern.compile("01(" + SHORT_MONTHS_EXPR[0] + ")(60|1960)[:\\s]00:00:01(.00)?"),
             Pattern.compile("01(" + SHORT_MONTHS_EXPR[0] + ")(60|1960)[:\\s]00:00:10(.00)?"),
             Pattern.compile("01(" + SHORT_MONTHS_EXPR[0] + ")(60|1960)[:\\s]00:01:40(.00)?"),
             Pattern.compile("01(" + SHORT_MONTHS_EXPR[0] + ")(60|1960)[:\\s]00:16:40(.00)?"),
             Pattern.compile("01(" + SHORT_MONTHS_EXPR[0] + ")(60|1960)[:\\s]02:46:40(.00)?"),
             Pattern.compile("02(" + SHORT_MONTHS_EXPR[0] + ")(60|1960)[:\\s]03:46:40(.00)?"),
             Pattern.compile("12(" + SHORT_MONTHS_EXPR[0] + ")(60|1960)[:\\s]13:46:40(.00)?"),
             Pattern.compile("25(" + SHORT_MONTHS_EXPR[3] + ")(60|1960)[:\\s]17:46:40(.00)?"),
             Pattern.compile("03(" + SHORT_MONTHS_EXPR[2] + ")(63|1963)[:\\s]09:46:40(.00)?"),
             Pattern.compile("09(" + SHORT_MONTHS_EXPR[8] + ")(91|1991)[:\\s]01:46:40(.00)?"),
             Pattern.compile("19(" + SHORT_MONTHS_EXPR[10] + ")(76|2276)[:\\s]17:46:40(.00)?")
        },
        new Pattern[] {
             Pattern.compile("0?0:00:01(.\\d\\d)?"),
             Pattern.compile("0?0:00:03(.\\d\\d)?"),
             Pattern.compile("0?0:00:09(.\\d\\d)?"),
             Pattern.compile("0?0:00:27(.\\d\\d)?"),
             Pattern.compile("0?0:01:21(.\\d\\d)?"),
             Pattern.compile("0?0:04:03(.\\d\\d)?"),
             Pattern.compile("0?0:12:09(.\\d\\d)?"),
             Pattern.compile("0?0:36:27(.\\d\\d)?"),
             Pattern.compile("0?1:49:21(.\\d\\d)?"),
             Pattern.compile("0?5:28:03(.\\d\\d)?"),
             Pattern.compile("16:24:09(.\\d\\d)?")
        }
    };
    static {
        // Row text in 3rd column
        table[2] = new String[table[0].length];
        for (int i=0; i<table[0].length; i++) {
            table[2][i] = "This is row " + i + " of 10";
        }
    }
    // Which columns hold percentages? Not all parsers
    //  correctly format these...
    protected static final List<Integer> percentageColumns = 
            Arrays.asList(new Integer[] { 3, 4 });
    
    protected static String[] toCells(String row, boolean isTH) {
        // Split into cells, ignoring stuff before first cell
        String[] cells;
        if (isTH) {
            cells = row.split("<th");
        } else {
            cells = row.split("<td");
        }
        cells = Arrays.copyOfRange(cells, 1, cells.length);

        // Ignore the closing tag onwards, and normalise whitespace
        for (int i=0; i<cells.length; i++) {
            cells[i] = cells[i].trim();
            if (cells[i].equals("/>")) {
                cells[i] = "";
                continue;
            }

            int splitAt = cells[i].lastIndexOf("</");
            cells[i] = cells[i].substring(0, splitAt).trim();
            cells[i] = cells[i].replaceAll("\\s+", " ");
        }
        return cells;
    }

    protected void assertHeaders(String xml, boolean isTH, boolean hasLabel, boolean hasName) {
        // Find the first row
        int splitAt = xml.indexOf("</tr>");
        String hRow = xml.substring(0, splitAt);
        splitAt = xml.indexOf("<tr>");
        hRow = hRow.substring(splitAt+4);

        // Split into cells, ignoring stuff before first cell
        String[] cells = toCells(hRow, isTH);

        // Check we got the right number
        assertEquals("Wrong number of cells in header row " + hRow,
                     columnLabels.length, cells.length);

        // Check we got the right stuff
        for (int i=0; i<cells.length; i++) {
            if (hasLabel && hasName) {
                assertContains("title=\"" + columnNames[i] + "\"", cells[i]); 
                assertContains(">" + columnLabels[i], cells[i]); 
            } else if (hasName) {
                assertContains(">" + columnNames[i], cells[i]); 
            } else {
                assertContains(">" + columnLabels[i], cells[i]); 
            }
        }
    }
    protected void assertContents(String xml, boolean hasHeader, boolean doesPercents) {
        // Ignore anything before the first <tr>
        // Ignore the header row if there is one
        int ignores = 1;
        if (hasHeader) ignores++;

        // Split into rows, and discard the row closing (and anything after)
        String[] rows = xml.split("<tr>");
        rows = Arrays.copyOfRange(rows, ignores, rows.length);
        for (int i=0; i<rows.length; i++) {
            rows[i] = rows[i].split("</tr>")[0].trim();
        }

        // Check we got the right number of rows
        for (int cn=0; cn<table.length; cn++) {
            assertEquals("Wrong number of rows found compared to column " + (cn+1),
                         table[cn].length, rows.length);
        }

        // Check each row's values
        for (int rn=0; rn<rows.length; rn++) {
            String[] cells = toCells(rows[rn], false);
            assertEquals("Wrong number of values in row " + (rn+1),
                         table.length, cells.length);

            for (int cn=0; cn<table.length; cn++) {
                String val = cells[cn];

                // If the parser doesn't know about % formats,
                //  skip the cell if the column in a % one
                if (!doesPercents && percentageColumns.contains(cn)) continue;

                // Ignore cell attributes
                if (! val.isEmpty()) val = val.split(">")[1];
                // Check
                String error = "Wrong text in row " + (rn+1) + " and column " + 
                               (cn+1) + " - " + table[cn][rn] + " vs " + val;
                if (table[cn][rn] instanceof String) {
                    assertEquals(error, table[cn][rn], val);
                } else {
                    assertTrue(error, ((Pattern)table[cn][rn]).matcher(val).matches());
                }
            }
        }
    }

    @Test
    public void testSAS7BDAT() throws Exception {
        XMLResult result = getXML("test-columnar.sas7bdat");
        String xml = result.xml;
        assertHeaders(xml, true, true, true);
        // TODO Wait for https://github.com/epam/parso/issues/28 to be fixed
        //  then check the % formats again
        assertContents(xml, true, false);
    }
    @Test
    public void testXLS() throws Exception {
        XMLResult result = getXML("test-columnar.xls");
        String xml = result.xml;
        assertHeaders(xml, false, true, false);
        assertContents(xml, true, false);
    }
    @Test
    public void testXLSX() throws Exception {
        XMLResult result = getXML("test-columnar.xlsx");
        String xml = result.xml;
        assertHeaders(xml, false, true, false);
        assertContents(xml, true, false);
    }
    @Test
    public void testXLSB() throws Exception {
        XMLResult result = getXML("test-columnar.xlsb");
        String xml = result.xml;
        assertHeaders(xml, false, true, false);
        assertContents(xml, true, false);
    }

    // TODO Fix the ODS test - currently failing with
    // org.xml.sax.SAXException: Namespace http://www.w3.org/1999/xhtml not declared
//    @Test
//    public void testODS() throws Exception {
//        XMLResult result = getXML("test-columnar.ods");
//        String xml = result.xml;
//        assertHeaders(xml, false, true, false);
//        assertContents(xml, true, true);
//    }
    
    // TODO Test other formats, eg Database formats

    /**
     * Note - we don't have a dedicated CSV parser
     * 
     * This means we don't get proper HTML out...
     */
    @Test
    public void testCSV() throws Exception {
        XMLResult result = getXML("test-columnar.csv");
        String xml = result.xml;
        // Normalise whitespace before testing
        xml = xml.replaceAll("\\s+", " ");

        for (String label : columnLabels) {
            assertContains(label, xml);
        }
        for (Object[] vals : table) {
            for (Object val : vals) {
                if (val instanceof String)
                    assertContains((String)val, xml);
                else if (val instanceof Pattern)
                    assertTrue("Not matched: " + val, 
                            ((Pattern)val).matcher(xml).find());
            }
        }
    }
}
