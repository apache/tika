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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import org.apache.commons.io.input.UnsynchronizedByteArrayInputStream;
import org.junit.jupiter.api.Test;
import org.xml.sax.ContentHandler;

import org.apache.tika.exception.TikaException;
import org.apache.tika.exception.WriteLimitReachedException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.BodyContentHandler;
import org.apache.tika.sax.ToXMLContentHandler;
import org.apache.tika.sax.XHTMLContentHandler;

public class XFAExtractorTest {

    private static final String XDP = "<xdp:xdp xmlns:xdp=\"http://ns.adobe.com/xdp/\">\n" +
            "<template xmlns=\"http://www.xfa.org/schema/xfa-template/3.3/\">\n" +
            " <subform name=\"form1\">\n" +
            "  <field name=\"School_Name\">\n" +
            "   <?PDF_OBJR 12 0 R?>\n" +
            "   dropped: text directly under a field\n" +
            "   <assist><toolTip><p>School</p><p>Name</p></toolTip></assist>\n" +
            "  </field>\n" +
            "  <field name=\"Room_1\"><assist><toolTip>  </toolTip></assist></field>\n" +
            "  <field><assist><toolTip>Nameless</toolTip></assist></field>\n" +
            "  <draw><value><text>Mount <![CDATA[Rushmore]]></text></value></draw>\n" +
            "  <draw><value><exData>intro<p>first</p><p></p>tail</exData></value></draw>\n" +
            "  <draw><value><speak>  </speak></value></draw>\n" +
            "  <assist><toolTip>Top-level tip</toolTip></assist>\n" +
            " </subform>\n" +
            "</template>\n" +
            "<other><field name=\"Not_A_Template_Field\"/></other>\n" +
            "<xfa:datasets xmlns:xfa=\"http://www.xfa.org/schema/xfa-data/1.0/\">\n" +
            " <xfa:data><form1>" +
            "<School_Name>my_school</School_Name>" +
            "<Room_1>my_room1</Room_1><Room_1>my_room2</Room_1>" +
            "before<Unbound>inner</Unbound>after" +
            "</form1></xfa:data>\n" +
            "</xfa:datasets>\n" +
            "</xdp:xdp>";

    @Test
    public void testFieldsValuesAndText() throws Exception {
        String xml = extract(XDP, new ToXMLContentHandler());
        String body = xml.substring(xml.indexOf("<body>") + "<body>".length(),
                xml.indexOf("</body>"));
        //text before a <p> merges into that paragraph; every </p> in a toolTip adds "\n"
        assertEquals("<div class=\"xfa_content\"><p>Mount Rushmore</p>\n" +
                "<p>introfirst</p>\n<p>tail</p>\n" +
                "<p>Top-level tip</p>\n" +
                "<div class=\"xfa_form\"><ol>" +
                "\t<li fieldName=\"School_Name\">School\nName\n: my_school</li>\n" +
                "\t<li fieldName=\"Room_1\">Room_1: my_room1</li>\n" +
                "\t<li fieldName=\"Room_1\">Room_1: my_room2</li>\n" +
                "\t<li fieldName=\"\">Nameless: </li>\n" +
                "</ol>\n</div>\n" +
                "</div>\n", body);
    }

    @Test
    public void testMalformedIsTikaExceptionAndDivBalanced() throws Exception {
        ToXMLContentHandler handler = new ToXMLContentHandler();
        XHTMLContentHandler xhtml = xhtml(handler);
        xhtml.startDocument();
        try (InputStream is = stream("<xdp><template xmlns=\"http://www.xfa.org/schema/" +
                "xfa-template/3.3/\"><text>partial</text><field")) {
            assertThrows(TikaException.class,
                    () -> new XFAExtractor().extract(is, xhtml, new Metadata(),
                            new ParseContext()));
        }
        xhtml.endDocument();
        String xml = handler.toString();
        assertTrue(xml.contains("<div class=\"xfa_content\"><p>partial</p>\n</div>\n</body>"), xml);
    }

    @Test
    public void testDownstreamExceptionPropagatesUnwrapped() {
        assertThrows(WriteLimitReachedException.class,
                () -> extract(XDP, new BodyContentHandler(3)));
    }

    private static String extract(String xdp, ContentHandler handler) throws Exception {
        XHTMLContentHandler xhtml = xhtml(handler);
        xhtml.startDocument();
        try (InputStream is = stream(xdp)) {
            new XFAExtractor().extract(is, xhtml, new Metadata(), new ParseContext());
        }
        xhtml.endDocument();
        return handler.toString();
    }

    private static XHTMLContentHandler xhtml(ContentHandler handler) {
        return new XHTMLContentHandler(handler, new Metadata());
    }

    private static InputStream stream(String xml) throws Exception {
        return UnsynchronizedByteArrayInputStream.builder()
                .setByteArray(xml.getBytes(StandardCharsets.UTF_8)).get();
    }
}
