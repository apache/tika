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
package org.apache.tika.parser.mif;

import org.apache.commons.lang3.StringEscapeUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.tika.detect.AutoDetectReader;
import org.xml.sax.Attributes;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.AttributesImpl;

import java.io.IOException;


import java.util.ArrayList;
import java.util.EmptyStackException;
import java.util.List;
import java.util.Stack;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Helper Class to Parse and Extract Adobe MIF Files.
 */
public class MIFExtractor {

    private static final Pattern openTagPattern = Pattern.compile("<(\\S*).*");
    private static final Pattern selfTagPattern = Pattern.compile("(<(\\S*))(\\s)(.*)(\\>).*");
    private static final String OPEN_TAG_MARKER = "<";
    private static final String CLOSE_TAG_MARKER = ">";
    private static final String START_TAG_VALUE = "`";
    private static final String END_TAG_VALUE = "'";

    /**
     * Parsers the file supplied through the reader and emits events to the supplied content handler.
     *
     * @param reader the reader to use.
     * @param handler the content handler to use.
     * @throws IOException on any IO error.
     * @throws SAXException on any SAX error.
     */
    static void parse(AutoDetectReader reader, ContentHandler handler) throws IOException, SAXException {
        handler.startDocument();
        String line;
        Tag currentTag = new Tag();
        Stack<Tag> parents = new Stack<>();
        while ((line = reader.readLine()) != null) {
            if (line.contains(OPEN_TAG_MARKER) && !line.contains(CLOSE_TAG_MARKER)) {
                Matcher matcher = openTagPattern.matcher(line.trim());
                if (matcher.matches()) {
                    if (!parents.empty()) {
                        currentTag = new Tag();
                        currentTag.setParent(parents.peek());
                    }
                    currentTag.setName(matcher.group(1));
                    parents.push(currentTag);
                    Attributes attrs = new AttributesImpl();
                    handler.startElement(StringUtils.EMPTY, matcher.group(1), matcher.group(1), attrs);
                }
            } else if (line.trim().startsWith(CLOSE_TAG_MARKER)) {
                try {
                    String tmp = line.trim();
                    String tagName = tmp.substring(tmp.lastIndexOf(" ") + 1);
                    Tag parent = parents.peek();
                    if (tagName.equals(parent.getName())) {
                        parents.pop();
                    }
                    if (!parents.empty()) {
                        parents.peek().addChild(parent);
                    }
                    handler.endElement(StringUtils.EMPTY, parent.getName(), parent.getName());
                } catch (EmptyStackException ex ) {
                    // Shouldn't happen, swallow to keep parsing
                }
            } else {
                Matcher matcher = selfTagPattern.matcher(line.trim());
                if (matcher.matches()) {
                    if (!parents.empty()) {
                        Tag child = new Tag();
                        child.setName(matcher.group(2));
                        child.setValue(matcher.group(4));
                        child.setParent(parents.peek());
                        currentTag.addChild(child);
                        processTag(handler, child);
                    } else {
                        currentTag.setName(matcher.group(2));
                        currentTag.setValue(matcher.group(4));
                        processTag(handler, currentTag);
                        currentTag = new Tag();
                    }
                }
            }
        }
        handler.endDocument();
    }

    /**
     * Process a tag and emit events to Content Handler.
     *
     * @param handler the content handler.
     * @param tag the tag to process.
     * @throws SAXException on any SAX error.
     */
    private static void processTag(ContentHandler handler, Tag tag) throws SAXException {
        Attributes attrs = new AttributesImpl();
        handler.startElement(StringUtils.EMPTY, tag.getName(), tag.getName(), attrs);
        String value = StringUtils.removeStart(tag.getValue(), START_TAG_VALUE);
        value = StringUtils.removeEnd(value, END_TAG_VALUE);
        String content = StringEscapeUtils.escapeXml(value);
        handler.characters(content.toCharArray(), 0, content.length());
        handler.endElement(StringUtils.EMPTY, tag.getName(), tag.getName());
    }

    /**
     * Helper model class for a MIF Tag to support parsing.
     */
    private static final class Tag {

        private List<Tag> children = new ArrayList<>();
        private Tag parent;
        private String name;
        private String value;

        public void addChild(Tag child) {
            children.add(child);
        }

        public List<Tag> getChildren() {
            return children;
        }

        public void setChildren(List<Tag> children) {
            this.children = children;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getValue() {
            return value;
        }

        public void setValue(String value) {
            this.value = value;
        }

        public String toString() {
            return name + ":" + value;
        }

        public Tag getParent() {
            return parent;
        }

        public void setParent(Tag parent) {
            this.parent = parent;
        }

    }

}
