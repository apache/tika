/**
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
package org.apache.tika.utils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Inspired from Nutch code class OutlinkExtractor. Apply regex to extract
 * content
 * 
 * 
 */
public class RegexUtils {

    /**
     * Regex pattern to get URLs within a plain text.
     * 
     * @see <a
     *      href="http://www.truerwords.net/articles/ut/urlactivation.html">http://www.truerwords.net/articles/ut/urlactivation.html
     *      </a>
     */
    private static final String LINKS_REGEX =
        "([A-Za-z][A-Za-z0-9+.-]{1,120}:"
        + "[A-Za-z0-9/](([A-Za-z0-9$_.+!*,;/?:@&~=-])|%[A-Fa-f0-9]{2}){1,333}"
        + "(#([a-zA-Z0-9][a-zA-Z0-9$_.+!*,;/?:@&~=%-]{0,1000}))?)";
    
    private static final Pattern LINKS_PATTERN = Pattern.compile(LINKS_REGEX, Pattern.CASE_INSENSITIVE + Pattern.MULTILINE);

    /**
     * Extract urls from plain text.
     *
     * @param content The plain text content to examine
     * @return List of urls within found in the plain text
     */
    public static List<String> extractLinks(String content) {
        if (content == null || content.length() == 0) {
            return Collections.emptyList();
        }

        List<String> extractions = new ArrayList<String>();
        final Matcher matcher = LINKS_PATTERN.matcher(content);
        while (matcher.find()) {
            extractions.add(matcher.group());
        }
        return extractions;

    }
}
