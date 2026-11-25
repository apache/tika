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
package org.apache.tika.parser.microsoft;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.microsoft.FormattingUtils.Tag;
import org.apache.tika.sax.ToXMLContentHandler;
import org.apache.tika.sax.XHTMLContentHandler;

public class ScriptedTextCellTest {

    //TIKA-5071
    @Test
    public void testTrim() throws Exception {
        ScriptedTextCell cell = new ScriptedTextCell()
                .append(" ", null).append("\t", Tag.SUP).append("  x", Tag.SUB)
                .append("2", null).append(" ", Tag.SUP).append(" \n", null);
        assertEquals("<sub>x</sub>2", render(cell.trim()));

        assertTrue(new ScriptedTextCell().append(" ", null).append(" ", Tag.SUP)
                .trim().isEmpty());
        assertEquals("<sup>a b</sup>",
                render(new ScriptedTextCell().append(" a b ", Tag.SUP).trim()));
    }

    private static String render(ScriptedTextCell cell) throws Exception {
        ToXMLContentHandler out = new ToXMLContentHandler();
        XHTMLContentHandler xhtml = new XHTMLContentHandler(out, new Metadata());
        xhtml.startDocument();
        xhtml.startElement("p");
        cell.render(xhtml);
        xhtml.endElement("p");
        xhtml.endDocument();
        String xml = out.toString();
        return xml.substring(xml.indexOf("<p>") + 3, xml.indexOf("</p>"));
    }
}
