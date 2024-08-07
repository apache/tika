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
package org.apache.tika.parser.csv;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import com.google.common.collect.ImmutableSet;
import org.junit.jupiter.api.Test;

import org.apache.tika.TikaTest;
import org.apache.tika.mime.MediaType;

public class CSVSnifferTest extends TikaTest {

    private static final Set<Character> DELIMITERS = ImmutableSet.of(',', ';', '\t', '|');

    private static final byte[] CSV_BASIC =
            ("the,quick,brown\tfox\n" + "jumped \tover,the\tlazy,\tdog\n" +
                    "and then,ran,down\tthe\tstreet").getBytes(StandardCharsets.UTF_8);

    private static final byte[] CSV_BASIC2 =
            ("the;quick;brown\tfox\n" + "jumped \tover;the\tlazy;\tdog\n" +
                    "and then;ran;down\tthe\tstreet").getBytes(StandardCharsets.UTF_8);

    private static final byte[] CSV_BASIC3 =
            ("the|quick|brown\tfox\n" + "jumped \tover|the\tlazy|\tdog\n" +
                    "and then|ran|down\tthe\tstreet").getBytes(StandardCharsets.UTF_8);

    private static final byte[] TSV_BASIC =
            ("the\tquick\tbrown,fox\n" + "jumped ,over\tthe,lazy\t,dog\n" +
                    "and then\tran\tdown,the,street").getBytes(StandardCharsets.UTF_8);

    private static final byte[] CSV_MID_CELL_QUOTE_EXCEPTION =
            ("the,quick,brown\"fox\n" + "jumped over,the lazy,dog\n" +
                    "and then,ran,down the street").getBytes(StandardCharsets.UTF_8);


    private static final byte[] ALLOW_SPACES_BEFORE_QUOTE =
            ("the,quick,         \"brown\"\"fox\"\n" + "jumped over,the lazy,dog\n" +
                    "and then,ran,down the street").getBytes(StandardCharsets.UTF_8);

    private static final byte[] ALLOW_SPACES_AFTER_QUOTE =
            ("the,\"quick\"     ,brown  fox\n" + "jumped over,the lazy,dog\n" +
                    "and then,ran,down the street").getBytes(StandardCharsets.UTF_8);

    private static List<CSVResult> sniff(Set<Character> delimiters, byte[] bytes, Charset charset)
            throws IOException {
        CSVSniffer sniffer = new CSVSniffer(delimiters);
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(new ByteArrayInputStream(bytes), charset))) {
            return sniffer.sniff(reader);
        }
    }

    @Test
    public void testCSVBasic() throws Exception {
        List<CSVResult> results = sniff(DELIMITERS, CSV_BASIC, StandardCharsets.UTF_8);
        assertEquals(4, results.size());
        assertEquals(Character.valueOf(','), results.get(0).getDelimiter());

        results = sniff(DELIMITERS, CSV_BASIC2, StandardCharsets.UTF_8);
        assertEquals(4, results.size());
        assertEquals(Character.valueOf(';'), results.get(0).getDelimiter());

        results = sniff(DELIMITERS, CSV_BASIC3, StandardCharsets.UTF_8);
        assertEquals(4, results.size());
        assertEquals(Character.valueOf('|'), results.get(0).getDelimiter());

        results = sniff(DELIMITERS, TSV_BASIC, StandardCharsets.UTF_8);
        assertEquals(4, results.size());
        assertEquals(Character.valueOf('\t'), results.get(0).getDelimiter());
    }

    @Test
    public void testCSVMidCellQuoteException() throws Exception {
        List<CSVResult> results =
                sniff(DELIMITERS, CSV_MID_CELL_QUOTE_EXCEPTION, StandardCharsets.UTF_8);

        assertEquals(4, results.size());
    }

    @Test
    public void testAllowWhiteSpacesAroundAQuote() throws Exception {
        List<CSVResult> results =
                sniff(DELIMITERS, ALLOW_SPACES_BEFORE_QUOTE, StandardCharsets.UTF_8);
        assertEquals(4, results.size());
        assertEquals(Character.valueOf(','), results.get(0).getDelimiter());

        results = sniff(DELIMITERS, ALLOW_SPACES_AFTER_QUOTE, StandardCharsets.UTF_8);
        assertEquals(4, results.size());
        assertEquals(Character.valueOf(','), results.get(0).getDelimiter());
    }

    @Test
    public void testSort() {
        List<CSVResult> list = new ArrayList<>();
        list.add(new CSVResult(0.1, MediaType.TEXT_HTML, '-'));
        list.add(new CSVResult(0.2, MediaType.TEXT_PLAIN, ','));
        Collections.sort(list);
        assertEquals(0.2, list.get(0).getConfidence(), 0.00001);
    }
}
