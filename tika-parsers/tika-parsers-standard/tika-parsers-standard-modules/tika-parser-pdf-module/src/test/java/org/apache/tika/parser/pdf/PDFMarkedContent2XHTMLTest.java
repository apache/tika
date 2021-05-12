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
package org.apache.tika.parser.pdf;

import static org.junit.Assert.assertEquals;

import java.util.List;

import org.junit.BeforeClass;
import org.junit.Test;

import org.apache.tika.TikaTest;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.parser.ParseContext;


public class PDFMarkedContent2XHTMLTest extends TikaTest {

    static ParseContext MARKUP_CONTEXT = new ParseContext();

    @BeforeClass
    public static void setUp() {
        PDFParserConfig config = new PDFParserConfig();
        config.setExtractMarkedContent(true);

        MARKUP_CONTEXT.set(PDFParserConfig.class, config);
    }

    @Test
    public void testJournal() throws Exception {
        String xml = getXML("testJournalParser.pdf", MARKUP_CONTEXT).xml;
        assertContains("<h1>I. INTRODUCTION</h1>", xml);
        assertContains("<table><tr>\t<td><p />", xml);
        assertContains("</td>\t<td><p>NHG</p>", xml);
        assertContains("</td>\t<td><p>STRING</p>", xml);
    }

    @Test
    public void testVarious() throws Exception {
        String xml = getXML("testPDFVarious.pdf", MARKUP_CONTEXT).xml;
        assertContains("<div class=\"textbox\"><p>Here is a text box</p>", xml);
        assertContains("<div class=\"footnote\"><p>1 This is a footnote.</p>", xml);
        assertContains("<ul>\t<li>Bullet 1</li>", xml);
        assertContains("<table><tr>\t<td><p>Row 1 Col 1</p>", xml);
        assertContains("<p>Here is a citation:</p>", xml);
        assertContains("a href=\"http://tika.apache.org/\">This is a hyperlink</a>", xml);
        assertContains("This is the header text.", xml);
        assertContains("This is the footer text.", xml);
    }

    @Test
    public void testChildAttachments() throws Exception {
        List<Metadata> metadataList =
                getRecursiveMetadata("testPDF_childAttachments.pdf", MARKUP_CONTEXT);

        //make sure that embedded docs are still getting extracted
        assertEquals(3, metadataList.size());

        String xml = metadataList.get(0).get(TikaCoreProperties.TIKA_CONTENT);
        //the point here is that in the annotations (that we
        // were grabbing by the classic PDF2XHTML),
        //the <a> content is identical to the href.  Here, they are not, which we only get from
        //marked up content...victory!!!
        assertContains("<a href=\"http://www.irs.gov\">IRS.gov</a>", xml);
        assertContains("<a href=\"http://www.irs.gov/pub15\">www.irs.gov/pub15</a>", xml);
    }

}
