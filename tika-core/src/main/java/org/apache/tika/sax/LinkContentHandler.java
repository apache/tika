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

import static org.apache.tika.sax.XHTMLContentHandler.XHTML;

import java.util.HashMap;
import java.util.Map;

import org.xml.sax.Attributes;
import org.xml.sax.helpers.DefaultHandler;

public class LinkContentHandler extends DefaultHandler {

    private final Map<String, String> links = new HashMap<String, String>();

    private String href = null;

    private final StringBuilder text = new StringBuilder();

    public Map<String, String> getLinks() {
        return links;
    }

    protected void addLink(String href, String text) {
        links.put(href, text);
    }

    //-------------------------------------------------------< ContentHandler>

    @Override
    public void startElement(
            String uri, String local, String name, Attributes attributes) {
        if (XHTML.equals(uri) && "a".equals(local)) {
            href = attributes.getValue("", "href");
            text.setLength(0);
        }
    }

    @Override
    public void characters(char[] ch, int start, int length) {
        if (href != null) {
            text.append(ch, start, length);
        }
    }

    @Override
    public void ignorableWhitespace(char[] ch, int start, int length) {
        characters(ch, start, length);
    }

    @Override
    public void endElement(String uri, String local, String name) {
        if (XHTML.equals(uri) && "a".equals(local) && href != null) {
            addLink(href, text.toString());
            href = null;
            text.setLength(0);
        }
    }

}

