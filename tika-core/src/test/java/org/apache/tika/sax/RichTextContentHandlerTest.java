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
package org.apache.tika.sax;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertEquals;

import java.io.ByteArrayOutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.Charset;

import org.apache.tika.metadata.Metadata;
import org.junit.Test;
import org.xml.sax.helpers.AttributesImpl;

/**
 * Test cases for the {@link RichTextContentHandler} class.
 */
public class RichTextContentHandlerTest {

    /**
     * Test to check img tags are detected and rich text version used.
     */
    @Test
    public void aTagTest() throws Exception {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();

        XHTMLContentHandler xhtml = new XHTMLContentHandler(
                new RichTextContentHandler(
                    new OutputStreamWriter(buffer, Charset.defaultCharset())),
                new Metadata());
        xhtml.startDocument();
        AttributesImpl attributes = new AttributesImpl();
        attributes.addAttribute("", "", "name", "", "value");
        xhtml.startElement("a", attributes);
        xhtml.endDocument();

        assertEquals("\n\n\n\n[bookmark: value]", buffer.toString(UTF_8.name()));
    }

    /**
     * Test to check a tags are detected and rich text version used.
     */
    @Test
    public void imgTagTest() throws Exception {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();

        XHTMLContentHandler xhtml = new XHTMLContentHandler(
                new RichTextContentHandler(
                    new OutputStreamWriter(buffer, Charset.defaultCharset())),
                new Metadata());
        xhtml.startDocument();
        AttributesImpl attributes = new AttributesImpl();
        attributes.addAttribute("", "", "alt", "", "value");
        xhtml.startElement("img", attributes);
        xhtml.endDocument();

        assertEquals("\n\n\n\n[image: value]", buffer.toString(UTF_8.name()));
    }

}
