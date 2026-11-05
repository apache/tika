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
package org.apache.tika.ml.junkdetect;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

public class HtmlContentCleanerTest {

    /** Numeric references decode as a browser does (WHATWG numeric character reference end state). */
    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "&#150;|–",
            "&#x96;|–",
            "&#X92;|’",
            "&#128;|€",
            "&#159;|Ÿ",
            "&#129;|\u0081",
            "&#x9D;|\u009D",
            "&#0;|�",
            "&#x0;|�",
            "&#xD800;|�",
            "&#57343;|�",
            "&#1114112;|�",
            "&#99999999999999;|�",
            "&#x10FFFF;|􏿿",
            "&#0000065;|A",
            "&#36;|$",
            "&#92;|\\",
            "&#169;|©",
            "&#xe600;|"})
    public void numericReferences(String in, String expected) {
        assertEquals(expected, HtmlContentCleaner.expandHtmlEntities(in));
    }

    @Test
    public void expansionIsNeverReadAgain() {
        assertEquals("&#x41;", HtmlContentCleaner.expandHtmlEntities("&#38;#x41;"));
        assertEquals("&amp;", HtmlContentCleaner.expandHtmlEntities("&amp;amp;"));
        assertEquals("&#169;", HtmlContentCleaner.expandHtmlEntities("&#x26;#169;"));
    }

    @Test
    public void cleanedPageShowsWhatABrowserShows() {
        assertEquals("price – €5 “ok”",
                HtmlContentCleaner.clean("<p>price &#150; &#128;5 &#147;ok&#148;</p>").trim());
    }
}
