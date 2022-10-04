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

public class DWGReadFormatRemover {
    public String cleanupDwgString(String dwgString) {
        // Cleaning the formatting of the text has been found from the following
        // website's:
        // https://www.cadforum.cz/en/text-formatting-codes-in-mtext-objects-tip8640
        // https://adndevblog.typepad.com/autocad/2017/09/dissecting-mtext-format-codes.html
        // These have also been spotted (pxqc,pxqr,pxql,simplex)
        // We always to do a backwards look to make sure the string to replace hasn't
        // been escaped
        String cleanString;
        // replace A0-2 (Alignment)
        cleanString = dwgString.replaceAll("(?<!\\\\)\\\\A[0-2];", "");
        cleanString = cleanString.replaceAll("(?<!\\\\)\\\\P", "\n");
        cleanString = cleanString.replaceAll("(?<!\\\\)\\\\pi(.*?);", "");
        cleanString = cleanString.replaceAll("(?<!\\\\)\\\\pxi(.*?);", "");
        cleanString = cleanString.replaceAll("(?<!\\\\)\\\\pxt(.*?);", "");
        cleanString = cleanString.replaceAll("(?<!\\\\)\\\\pt(.*?);", "");
        cleanString = cleanString.replaceAll("(?<!\\\\)\\\\H[0-9]*(.*?);", "");
        cleanString = cleanString.replaceAll("(?<!\\\\)\\\\F|f(.*?);", "");
        cleanString = cleanString.replaceAll("(?<!\\\\)(\\\\L)(.*?)(\\\\l)", "$2");
        cleanString = cleanString.replaceAll("(?<!\\\\)(\\\\O)(.*?)(\\\\o)", "$2");
        cleanString = cleanString.replaceAll("(?<!\\\\)(\\\\K)(.*?)(\\\\k)", "$2");
        cleanString = cleanString.replaceAll("(?<!\\\\)(\\\\N)", "\t");
        cleanString = cleanString.replaceAll("(?<!\\\\)\\\\Q[\\d];", "");
        cleanString = cleanString.replaceAll("(?<!\\\\)\\\\W(.*?);", "");
        cleanString = cleanString.replaceAll("(?<!\\\\)\\\\S(.*?):", "");
        cleanString = cleanString.replaceAll("(?<!\\\\)(\\\\C|c[1-7];)", "");
        cleanString = cleanString.replaceAll("(?<!\\\\)(\\\\T(.*?);)", "");
        cleanString = cleanString.replaceAll("(?<!\\\\)(\\\\pxqc;)", "");
        cleanString = cleanString.replaceAll("(?<!\\\\)(\\\\pxqr;)", "");
        cleanString = cleanString.replaceAll("(?<!\\\\)(\\\\pxql;)", "");
        cleanString = cleanString.replaceAll("(?<!\\\\)(\\\\simplex\\|c(.*?);)", "");
        cleanString = cleanString.replaceAll("(\\\\)", "\\\\");
        cleanString = cleanString.replaceAll("(?<!\\\\)\\}|(?<!\\\\)\\{", "");
        return cleanString;

    }
}
