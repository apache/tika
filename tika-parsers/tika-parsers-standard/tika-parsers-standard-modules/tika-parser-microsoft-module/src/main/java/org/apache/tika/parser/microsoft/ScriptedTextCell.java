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

import java.util.ArrayList;
import java.util.List;

import org.xml.sax.SAXException;

import org.apache.tika.parser.microsoft.FormattingUtils.Tag;
import org.apache.tika.sax.XHTMLContentHandler;

/**
 * Spreadsheet cell text with superscript or subscript in it: runs of text, each
 * plain or {@link Tag#SUP}/{@link Tag#SUB}, rendered as {@code <sup>}/{@code <sub>}.
 * Adjacent runs with the same tag are joined, so a cell split into several font
 * runs that are all superscript gives one {@code <sup>}.
 */
public class ScriptedTextCell implements Cell {

    private final List<Run> runs = new ArrayList<>();

    /**
     * @param tag {@link Tag#SUP}, {@link Tag#SUB}, or {@code null} for plain text
     */
    public ScriptedTextCell append(String text, Tag tag) {
        if (text == null || text.isEmpty()) {
            return this;
        }
        int last = runs.size() - 1;
        if (last >= 0 && runs.get(last).tag == tag) {
            runs.set(last, new Run(runs.get(last).text + text, tag));
        } else {
            runs.add(new Run(text, tag));
        }
        return this;
    }

    public boolean hasScript() {
        for (Run run : runs) {
            if (run.tag != null) {
                return true;
            }
        }
        return false;
    }

    public boolean isEmpty() {
        return runs.isEmpty();
    }

    /**
     * @return the text without the formatting
     */
    public String getText() {
        StringBuilder sb = new StringBuilder();
        for (Run run : runs) {
            sb.append(run.text);
        }
        return sb.toString();
    }

    /**
     * Trims whitespace off both ends of the text, as {@link String#trim()} does for
     * a {@link TextCell}; runs left empty are dropped.
     */
    public ScriptedTextCell trim() {
        while (!runs.isEmpty()) {
            Run first = runs.get(0);
            int start = 0;
            while (start < first.text.length() && first.text.charAt(start) <= ' ') {
                start++;
            }
            if (start < first.text.length()) {
                runs.set(0, new Run(first.text.substring(start), first.tag));
                break;
            }
            runs.remove(0);
        }
        while (!runs.isEmpty()) {
            int lastIndex = runs.size() - 1;
            Run last = runs.get(lastIndex);
            int end = last.text.length();
            while (end > 0 && last.text.charAt(end - 1) <= ' ') {
                end--;
            }
            if (end > 0) {
                runs.set(lastIndex, new Run(last.text.substring(0, end), last.tag));
                break;
            }
            runs.remove(lastIndex);
        }
        return this;
    }

    @Override
    public void render(XHTMLContentHandler handler) throws SAXException {
        for (Run run : runs) {
            if (run.tag == null) {
                handler.characters(run.text);
            } else {
                handler.startElement(run.tag.tagName());
                handler.characters(run.text);
                handler.endElement(run.tag.tagName());
            }
        }
    }

    /**
     * @param vertAlign a SpreadsheetML {@code vertAlign} {@code val}
     * @return {@link Tag#SUP}, {@link Tag#SUB}, or {@code null} for baseline
     */
    public static Tag fromVertAlign(String vertAlign) {
        if ("superscript".equals(vertAlign)) {
            return Tag.SUP;
        } else if ("subscript".equals(vertAlign)) {
            return Tag.SUB;
        }
        return null;
    }

    /**
     * @param superSubScript a BIFF FONT record's super/subscript: 1 superscript,
     *                       2 subscript
     * @return {@link Tag#SUP}, {@link Tag#SUB}, or {@code null} for neither
     */
    public static Tag fromSuperSubScript(int superSubScript) {
        if (superSubScript == 1) {
            return Tag.SUP;
        } else if (superSubScript == 2) {
            return Tag.SUB;
        }
        return null;
    }

    private record Run(String text, Tag tag) {
    }
}
