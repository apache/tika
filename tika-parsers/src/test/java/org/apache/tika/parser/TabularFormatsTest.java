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


import org.apache.tika.TikaTest;
import org.junit.Test;

/**
 * Ensure that our various Table-based formats produce consistent,
 *  broadly similar output.
 * This is mostly focused on the XHTML output
 */
public class TabularFormatsTest extends TikaTest {
    protected static final String[] columnNames = new String[] {
         "recnum","square","desc","pctdone","pctinc",
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
        // TODO All values
        new String[] {
             "0","1","2","3","4","5","6","7","8","9","10"
        },
        new String[] {
             "0","1","4" // etc
        },
        new String[] {  // etc
                "01-01-1960"
        },
        new String[] {  // etc
        },
        new String[] {
                ""
        }
    };

    protected void assertHeaders(String xml, boolean isTH) {
        // TODO Check for the first row, then TR or TH
    }
    protected void assertContents(String xml, boolean hasHeader) {
        // TODO Check the rows
    }

    @Test
    public void testCSV() throws Exception {
        XMLResult result = getXML("test-columnar.csv");
        String xml = result.xml;

        assertHeaders(xml, false);
        assertContents(xml, true);
    }
    // TODO SAS7BDAT
    // TODO Other formats
}
