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

import org.apache.poi.xwpf.usermodel.UnderlinePatterns;
import org.apache.poi.xwpf.usermodel.VerticalAlign;

/**
 * WARNING: This class is mutable.  Make a copy of it
 * if you want persistence!
 */

public class RunProperties {
    boolean italics = false;
    boolean bold = false;
    boolean strikeThrough = false;

    UnderlinePatterns underline = UnderlinePatterns.NONE;

    VerticalAlign verticalAlign = VerticalAlign.BASELINE;
    boolean verticalAlignSet = false;

    // PPTX hlinkClick hyperlink URL — set from <a:hlinkClick> inside <a:rPr>
    String hlinkClickUrl = null;

    // docx character style (w:rStyle); direct b/i, once set, override it
    String styleID = null;
    boolean boldSet = false;
    boolean italicsSet = false;

    public boolean isItalics() {
        return italics;
    }

    public void setItalics(boolean italics) {
        this.italics = italics;
        this.italicsSet = true;
    }

    public boolean isItalicsSet() {
        return italicsSet;
    }

    public boolean isBold() {
        return bold;
    }

    public void setBold(boolean bold) {
        this.bold = bold;
        this.boldSet = true;
    }

    public boolean isBoldSet() {
        return boldSet;
    }

    public String getStyleID() {
        return styleID;
    }

    public void setStyleID(String styleID) {
        this.styleID = styleID;
    }

    public boolean isStrikeThrough() {
        return strikeThrough;
    }

    public void setStrike(boolean strikeThrough) {
        this.strikeThrough = strikeThrough;
    }

    public UnderlinePatterns getUnderline() {
        return underline;
    }

    public void setUnderline(String underlineString) {
        if (underlineString == null || underlineString.equals("")) {
            underline = UnderlinePatterns.SINGLE;
        } else if (UnderlinePatterns.NONE.name().equals(underlineString)) {
            underline = UnderlinePatterns.NONE;
        } else {
            //TODO -- fill out rest
            underline = UnderlinePatterns.SINGLE;
        }
    }

    public VerticalAlign getVerticalAlign() {
        return verticalAlign;
    }

    /** docx w:vertAlign w:val: superscript, subscript, baseline. */
    public void setVerticalAlign(String val) {
        verticalAlignSet = true;
        if ("superscript".equals(val)) {
            verticalAlign = VerticalAlign.SUPERSCRIPT;
        } else if ("subscript".equals(val)) {
            verticalAlign = VerticalAlign.SUBSCRIPT;
        } else {
            verticalAlign = VerticalAlign.BASELINE;
        }
    }

    public boolean isVerticalAlignSet() {
        return verticalAlignSet;
    }

    /** pptx a:rPr baseline, in thousandths of a percent: above 0 raised, below 0 lowered. */
    public void setBaselineOffset(String baseline) {
        try {
            int v = Integer.parseInt(baseline.trim());
            verticalAlign = v > 0 ? VerticalAlign.SUPERSCRIPT
                    : v < 0 ? VerticalAlign.SUBSCRIPT : VerticalAlign.BASELINE;
        } catch (NumberFormatException e) {
            verticalAlign = VerticalAlign.BASELINE;
        }
    }

    public String getHlinkClickUrl() {
        return hlinkClickUrl;
    }

    public void setHlinkClickUrl(String url) {
        this.hlinkClickUrl = url;
    }

    void copyFrom(RunProperties other) {
        italics = other.italics;
        bold = other.bold;
        strikeThrough = other.strikeThrough;
        underline = other.underline;
        verticalAlign = other.verticalAlign;
        verticalAlignSet = other.verticalAlignSet;
        hlinkClickUrl = other.hlinkClickUrl;
        styleID = other.styleID;
        boldSet = other.boldSet;
        italicsSet = other.italicsSet;
    }

    public void reset() {
        italics = false;
        bold = false;
        strikeThrough = false;
        underline = UnderlinePatterns.NONE;
        verticalAlign = VerticalAlign.BASELINE;
        verticalAlignSet = false;
        hlinkClickUrl = null;
        styleID = null;
        boldSet = false;
        italicsSet = false;
    }
}
