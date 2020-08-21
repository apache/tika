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
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTUnderline;

/**
 * WARNING: This class is mutable.  Make a copy of it
 * if you want persistence!
 */

public class RunProperties {
    boolean italics = false;
    boolean bold = false;
    boolean strikeThrough = false;

    UnderlinePatterns underline = UnderlinePatterns.NONE;

    public boolean isItalics() {
        return italics;
    }

    public boolean isBold() {
        return bold;
    }

    public void setItalics(boolean italics) {
        this.italics = italics;
    }

    public void setBold(boolean bold) {
        this.bold = bold;
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
}
