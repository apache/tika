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

public class DWGReadFormatRemover {
    public String cleanupDwgString(String dwgString) {
        // Cleaning the formatting of the text has been found from the following
        // website's:
        // https://www.cadforum.cz/en/text-formatting-codes-in-mtext-objects-tip8640
        // https://adndevblog.typepad.com/autocad/2017/09/dissecting-mtext-format-codes.html
        // These have also been spotted (pxqc,pxqr,pxql,simplex)
        // We always to do a backwards look to make sure the string to replace hasn't
        // been escaped
        String cleanString = dwgString;
        StringBuffer sb = new StringBuffer();
        //Strip off start/stop underline/overstrike/strike throughs
        Matcher m = Pattern.compile("((?:\\\\\\\\)+|\\\\[LlOoKk])").matcher(cleanString);
        while (m.find()) {
            if (! m.group(1).endsWith("\\")) {
                m.appendReplacement(sb, "");
            }
        }
        m.appendTail(sb);
        cleanString = sb.toString();

        //Strip off semi-colon ended markers
        m = Pattern.compile("((?:\\\\\\\\)+|\\\\(?:A|H|pi|pxt|pxi|X|Q|f|W|C|T)[^;]{0,100" +
                            "};)").matcher(cleanString);
        sb.setLength(0);
        while (m.find()) {
            if (! m.group(1).endsWith("\\")) {
                m.appendReplacement(sb, "");
            }
        }
        m.appendTail(sb);
        cleanString = sb.toString();

            //new line marker \\P replace with actual new line
        m = Pattern.compile("((?:\\\\\\\\)+|\\\\P)").matcher(cleanString);
        sb.setLength(0);
        while (m.find()) {
            if (m.group(1).endsWith("P")) {
                m.appendReplacement(sb, "\n");
            }
        }
        m.appendTail(sb);
        cleanString = sb.toString();

            //stacking fractions
        m = Pattern.compile("(\\\\\\\\)+|\\\\S([^/^#]{1,20})[/^#]([^;]{1,20});").matcher(cleanString);
        sb.setLength(0);
        while (m.find()) {
            if (m.group(1) == null) {
                m.appendReplacement(sb, m.group(2) + "/" + m.group(3));
            }
        }
        m.appendTail(sb);
        cleanString = sb.toString();

        //strip brackets around text, make sure they aren't escaped
        m = Pattern.compile("(\\\\)+[{}]|([{}])").matcher(cleanString);
        sb.setLength(0);
        while (m.find()) {
            if (m.group(1) == null) {
                m.appendReplacement(sb, "");
            }
        }
        m.appendTail(sb);
        cleanString = sb.toString();
            //now get rid of escape characters
        cleanString = cleanString.replaceAll("(?<!\\\\)(\\\\)(?!\\\\)", "");
        //now unescape backslash
        cleanString = cleanString.replaceAll("(\\\\\\\\)", "\\\\");
        return cleanString;
    }

}
