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

package org.apache.tika.parser.dwg;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * DWGReadFormatRemover removes the formatting from the text from libredwg files so only
 * the raw text remains.
 * What needs to be cleaned has been found on the following websites:
 * <p>
 * <a href="https://www.cadforum.cz/en/text-formatting-codes-in-mtext-objects-tip8640">
 * https://www.cadforum.cz/en/text-formatting-codes-in-mtext-objects-tip8640</a>
 * <p>
 * <a href="https://adndevblog.typepad.com/autocad/2017/09/dissecting-mtext-format-codes.html">
 * https://adndevblog.typepad.com/autocad/2017/09/dissecting-mtext-format-codes.html</a>
 * <p>
 */

public class DWGReadFormatRemover {
    private static final String underlineStrikeThrough = "((?:\\\\\\\\)+|\\\\[LlOoKk])";
    private static final String endMarks = "((?:\\\\\\\\)+|\\\\(?:A|H|pi|pxt|pxi|pt|X|Q|f|F|W|C|T)[^;]{0,100};)";
    private static final String newLine = "((?:\\\\\\\\)+|\\\\P)";
    private static final  String stackFrac = "(\\\\\\\\)+|\\\\S([^/^#]{1,20})[/^#]([^;]{1,20});";
    private static final String curlyBraces = "(\\\\)+[{}]|([{}])";
    private static final String escapeChars = "(?<!\\\\)(\\\\)(?!\\\\)";
    public String cleanupDwgString(String dwgString) {
        String cleanString = dwgString;
        StringBuffer sb = new StringBuffer();
        //Strip off start/stop underline/overstrike/strike throughs
        Matcher m = Pattern.compile(underlineStrikeThrough).matcher(cleanString);
        while (m.find()) {
            if (! m.group(1).endsWith("\\")) {
                m.appendReplacement(sb, "");
            }
        }
        m.appendTail(sb);
        cleanString = sb.toString();

        //Strip off semi-colon ended markers
        m = Pattern.compile(endMarks).matcher(cleanString);
        sb.setLength(0);
        while (m.find()) {
            if (! m.group(1).endsWith("\\")) {
                m.appendReplacement(sb, "");
            }
        }
        m.appendTail(sb);
        cleanString = sb.toString();

            //new line marker \\P replace with actual new line
        m = Pattern.compile(newLine).matcher(cleanString);
        sb.setLength(0);
        while (m.find()) {
            if (m.group(1).endsWith("P")) {
                m.appendReplacement(sb, "\n");
            }
        }
        m.appendTail(sb);
        cleanString = sb.toString();

            //stacking fractions
        m = Pattern.compile(stackFrac).matcher(cleanString);
        sb.setLength(0);
        while (m.find()) {
            if (m.group(1) == null) {
                m.appendReplacement(sb, m.group(2) + "/" + m.group(3));
            }
        }
        m.appendTail(sb);
        cleanString = sb.toString();

        //strip brackets around text, make sure they aren't escaped
        m = Pattern.compile(curlyBraces).matcher(cleanString);
        sb.setLength(0);
        while (m.find()) {
            if (m.group(1) == null) {
                m.appendReplacement(sb, "");
            }
        }
        m.appendTail(sb);
        cleanString = sb.toString();
            //now get rid of escape characters
        cleanString = cleanString.replaceAll(escapeChars, "");
        //now unescape backslash
        cleanString = cleanString.replaceAll("(\\\\\\\\)", "\\\\");
        return cleanString;
    }

}
