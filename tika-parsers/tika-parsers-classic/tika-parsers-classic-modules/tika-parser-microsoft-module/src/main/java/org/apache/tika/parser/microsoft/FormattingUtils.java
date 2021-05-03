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

import java.util.Deque;
import java.util.EnumSet;
import java.util.Locale;

import org.apache.poi.wp.usermodel.CharacterRun;
import org.apache.poi.xwpf.usermodel.UnderlinePatterns;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.xml.sax.SAXException;

import org.apache.tika.sax.XHTMLContentHandler;

public class FormattingUtils {
    private FormattingUtils() {
    }

    /**
     * Closes all tags until {@code currentState} contains only tags from {@code desired} set,
     * then open all required tags to reach desired state.
     *
     * @param xhtml        handler
     * @param desired      desired formatting state
     * @param currentState current formatting state (stack of open formatting tags)
     * @throws SAXException pass underlying handler exception
     */
    public static void ensureFormattingState(XHTMLContentHandler xhtml, EnumSet<Tag> desired,
                                             Deque<Tag> currentState) throws SAXException {
        EnumSet<FormattingUtils.Tag> undesired = EnumSet.complementOf(desired);

        while (!currentState.isEmpty() && currentState.stream().anyMatch(undesired::contains)) {
            xhtml.endElement(currentState.pop().tagName());
        }

        desired.removeAll(currentState);
        for (FormattingUtils.Tag tag : desired) {
            currentState.push(tag);
            xhtml.startElement(tag.tagName());
        }
    }

    /**
     * Closes all formatting tags.
     *
     * @param xhtml           handler
     * @param formattingState current formatting state (stack of open formatting tags)
     * @throws SAXException pass underlying handler exception
     */
    public static void closeStyleTags(XHTMLContentHandler xhtml, Deque<Tag> formattingState)
            throws SAXException {
        ensureFormattingState(xhtml, EnumSet.noneOf(Tag.class), formattingState);
    }

    public static EnumSet<Tag> toTags(CharacterRun run) {
        EnumSet<Tag> tags = EnumSet.noneOf(Tag.class);
        if (run.isBold()) {
            tags.add(Tag.B);
        }
        if (run.isItalic()) {
            tags.add(Tag.I);
        }
        if (run.isStrikeThrough()) {
            tags.add(Tag.S);
        }
        if (run instanceof XWPFRun) {
            XWPFRun xwpfRun = (XWPFRun) run;
            if (xwpfRun.getUnderline() != UnderlinePatterns.NONE) {
                tags.add(Tag.U);
            }
        } else if (run instanceof org.apache.poi.hwpf.usermodel.CharacterRun) {
            org.apache.poi.hwpf.usermodel.CharacterRun hwpfRun =
                    (org.apache.poi.hwpf.usermodel.CharacterRun) run;
            if (hwpfRun.getUnderlineCode() != 0) {
                tags.add(Tag.U);
            }
        }
        return tags;
    }

    public enum Tag {
        // DON'T reorder elements to avoid breaking tests: EnumSet is iterated in natural order
        // as enum variants are declared
        B, I, S, U;

        public String tagName() {
            return name().toLowerCase(Locale.ROOT);
        }
    }
}
