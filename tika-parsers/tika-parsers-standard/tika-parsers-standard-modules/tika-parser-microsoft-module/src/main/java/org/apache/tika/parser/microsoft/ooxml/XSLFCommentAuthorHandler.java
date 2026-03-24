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
package org.apache.tika.parser.microsoft.ooxml;

import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

/**
 * SAX handler that parses PPTX commentAuthors.xml and populates
 * a {@link CommentAuthors} map with author names and initials by ID.
 */
class XSLFCommentAuthorHandler extends DefaultHandler {

    private final CommentAuthors commentAuthors;

    XSLFCommentAuthorHandler(CommentAuthors commentAuthors) {
        this.commentAuthors = commentAuthors;
    }

    @Override
    public void startElement(String uri, String localName, String qName, Attributes atts)
            throws SAXException {
        if ("cmAuthor".equals(localName)) {
            String id = null;
            String name = null;
            String initials = null;
            for (int i = 0; i < atts.getLength(); i++) {
                if ("id".equals(atts.getLocalName(i))) {
                    id = atts.getValue(i);
                } else if ("name".equals(atts.getLocalName(i))) {
                    name = atts.getValue(i);
                } else if ("initials".equals(atts.getLocalName(i))) {
                    initials = atts.getValue(i);
                }
            }
            commentAuthors.add(id, name, initials);
        }
    }
}
