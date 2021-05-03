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

package org.apache.tika.parser.mock;


import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.xml.sax.SAXException;

import org.apache.tika.config.Field;
import org.apache.tika.sax.XHTMLContentHandler;

/**
 * only parses vowels as specified in "vowel" field.
 */
public class VowelParser extends MockParser {

    private static final long serialVersionUID = 1L;

    @Field
    private String vowel = "aeiou";

    protected void write(Node action, XHTMLContentHandler xhtml) throws SAXException {
        NamedNodeMap attrs = action.getAttributes();
        Node eNode = attrs.getNamedItem("element");
        String elementType = "p";
        if (eNode != null) {
            elementType = eNode.getTextContent();
        }
        String text = action.getTextContent();
        StringBuilder sb = new StringBuilder();
        Matcher m = Pattern.compile("(?i)([" + vowel + "])").matcher(text);
        while (m.find()) {
            sb.append(m.group(1));
        }
        xhtml.startElement(elementType);
        xhtml.characters(sb.toString());
        xhtml.endElement(elementType);
    }

}
