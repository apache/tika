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
    protected static final String[] headers = new String[] {
        "String (Num=)","Number","Date","Datetime","Number"
    };
    /**
     * Expected values, by <em>column</em>
     */
    protected static final String[][] table = new String[][] {
        // TODO All values
        new String[] {
                "Num=0"
        },
        new String[] {
                "0.0"
        },
        new String[] {
                "1899-12-30"
        },
        new String[] {
                "1900-01-01 11:00:00"
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
