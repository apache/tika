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
package org.apache.tika.parser.iwork;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.xml.sax.helpers.AttributesImpl;

import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.StrictXHTMLValidator;
import org.apache.tika.sax.ToXMLContentHandler;
import org.apache.tika.sax.XHTMLContentHandler;

public class KeynoteContentHandlerTest {

    /**
     * TIKA-4744: Keynote tables whose final row contains fewer cells than
     * numberOfColumns left {@code <tr>} open when {@code </sf:tabular-model>}
     * fired {@code </table>}. Drive the handler with a synthetic 3-column
     * table whose final row has only 2 cells and assert the XHTML stays
     * balanced through endDocument.
     */
    @Test
    public void testIncompleteFinalRowClosesTr() throws Exception {
        Metadata md = new Metadata();
        ToXMLContentHandler xml = new ToXMLContentHandler();
        XHTMLContentHandler xhtml = new XHTMLContentHandler(
                new StrictXHTMLValidator(xml), md, new ParseContext());
        KeynoteContentHandler h = new KeynoteContentHandler(xhtml, md);

        xhtml.startDocument();

        // <key:slide>
        h.startElement("", "slide", "key:slide", new AttributesImpl());
        // <sf:tabular-model sfa:ID="t1">
        AttributesImpl tabAttrs = new AttributesImpl();
        tabAttrs.addAttribute("", "sfa:ID", "sfa:ID", "CDATA", "t1");
        h.startElement("", "tabular-model", "sf:tabular-model", tabAttrs);
        // <sf:columns sf:count="3">
        AttributesImpl colAttrs = new AttributesImpl();
        colAttrs.addAttribute("", "sf:count", "sf:count", "CDATA", "3");
        h.startElement("", "columns", "sf:columns", colAttrs);
        h.endElement("", "columns", "sf:columns");
        // Two cells (less than 3) -> incomplete final row.
        AttributesImpl cell = new AttributesImpl();
        cell.addAttribute("", "sfa:s", "sfa:s", "CDATA", "A");
        h.startElement("", "ct", "sf:ct", cell);
        h.endElement("", "ct", "sf:ct");
        cell = new AttributesImpl();
        cell.addAttribute("", "sfa:s", "sfa:s", "CDATA", "B");
        h.startElement("", "ct", "sf:ct", cell);
        h.endElement("", "ct", "sf:ct");
        // </sf:tabular-model> -- without the fix this emits </table> while
        // <tr> is still topmost and StrictXHTMLValidator throws.
        h.endElement("", "tabular-model", "sf:tabular-model");
        // </key:slide>
        h.endElement("", "slide", "key:slide");

        xhtml.endDocument();

        String out = xml.toString();
        // Sanity: the cells emitted, the row closed, and the table closed.
        assertTrue(out.contains("<td>A</td>"), "expected td A; got: " + out);
        assertTrue(out.contains("<td>B</td>"), "expected td B; got: " + out);
        assertTrue(out.contains("</tr>"), "expected </tr>; got: " + out);
        assertTrue(out.contains("</table>"), "expected </table>; got: " + out);
    }
}
