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

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import org.apache.tika.TikaTest;
import org.junit.Test;

/**
 * Ensure that our various Table-based formats produce consistent,
 *  broadly similar output.
 * This is mostly focused on the XHTML output
 */
public class TabularFormatsTest extends TikaTest {
    protected static final String[] columnNames = new String[] {
         "recnum","square","desc","pctdone","pctincr",
         "date","datetime","time"
    };
    protected static final String[] columnLabels = new String[] {
        "Record Number","Square of the Record Number",
        "Description of the Row","Percent Done",
        "Percent Increment","date","datetime","time"    
    };

    /**
     * Expected values, by <em>column</em>
     */
    protected static final String[][] table = new String[][] {
        new String[] {
             "0","1","2","3","4","5","6","7","8","9","10"
        },
        new String[] {
             "0","1","4","9","16","25","36","49","64","81","100"
        },
        new String[] {}, // Done later
        new String[] {
                "0%","10%","20%","30%","40%","50%",
                "60%","70%","80%","90%","100%"
        },
        new String[] {
                "","0.0%","50.0%","66.7%",
                "75.0%","80.0%","83.3%","85.7%",
                "87.5%","88.9%","90.0%"
        },
        new String[] {
             "01-01-1960", "02-01-1960", "17-01-1960",
             "22-03-1960", "13-09-1960", "17-09-1961",
             "20-07-1963", "29-07-1966", "20-03-1971",
             "18-12-1977", "19-05-1987"
        },
        new String[] {
             "01JAN60:00:00:01",
             "01JAN60:00:00:10",
             "01JAN60:00:01:40",
             "01JAN60:00:16:40",
             "01JAN60:02:46:40",
             "02JAN60:03:46:40",
             "12JAN60:13:46:40",
             "25APR60:17:46:40",
             "03MAR63:09:46:40",
             "09SEP91:01:46:40",
             "19NOV76:17:46:40"
        },
        new String[] {
             "0:00:01",
             "0:00:03",
             "0:00:09",
             "0:00:27",
             "0:01:21",
             "0:04:03",
             "0:12:09",
             "0:36:27",
             "1:49:21",
             "5:28:03",
             "16:24:09"
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
    // Which columns hold dates? Some parsers output
    //  bits of the month in lower case, some all upper, eg JAN vs Jan
    protected static final List<Integer> dateColumns = 
            Arrays.asList(new Integer[] { 5, 6 });
    // TODO Handle 60 vs 1960
    
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
                if (dateColumns.contains(cn)) val = val.toUpperCase(Locale.ROOT);

                // Ignore cell attributes
                if (! val.isEmpty()) val = val.split(">")[1];
                // Check
                assertEquals("Wrong text in row " + (rn+1) + " and column " + (cn+1),
                             table[cn][rn], val);
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
//        assertContents(xml, true, false);
    }
    @Test
    public void testXLS() throws Exception {
        XMLResult result = getXML("test-columnar.xls");
        String xml = result.xml;
        assertHeaders(xml, false, true, false);
        // TODO Correctly handle empty cells then test
        //assertContents(xml, true, false);
    }
    @Test
    public void testXLSX() throws Exception {
        XMLResult result = getXML("test-columnar.xlsx");
        String xml = result.xml;
        assertHeaders(xml, false, true, false);
        // TODO Correctly handle empty cells then test
        //assertContents(xml, true, false);
    }
    // TODO Test ODS
    
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
        for (String[] vals : table) {
            for (String val : vals) {
                assertContains(val, xml);
            }
        }
    }
}
