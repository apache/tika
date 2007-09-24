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
import java.util.List;

import org.apache.log4j.Logger;
import org.apache.oro.text.regex.MalformedPatternException;
import org.apache.oro.text.regex.MatchResult;
import org.apache.oro.text.regex.Pattern;
import org.apache.oro.text.regex.PatternCompiler;
import org.apache.oro.text.regex.PatternMatcher;
import org.apache.oro.text.regex.PatternMatcherInput;
import org.apache.oro.text.regex.Perl5Compiler;
import org.apache.oro.text.regex.Perl5Matcher;

/**
 * Inspired from Nutch code class OutlinkExtractor. Apply regex to extract
 * content
 * 
 * 
 */
public class RegexUtils {

    static Logger logger = Logger.getRootLogger();

    public static List<String> extract(String content, String regex)
            throws MalformedPatternException {

        List<String> extractions = new ArrayList<String>();
        final PatternCompiler cp = new Perl5Compiler();
        final Pattern pattern = cp.compile(regex,
                Perl5Compiler.CASE_INSENSITIVE_MASK
                        | Perl5Compiler.READ_ONLY_MASK
                        | Perl5Compiler.MULTILINE_MASK);
        final PatternMatcher matcher = new Perl5Matcher();

        final PatternMatcherInput input = new PatternMatcherInput(content);

        MatchResult result;
        String extractedContent;

        while (matcher.contains(input, pattern)) {
            result = matcher.getMatch();
            extractedContent = result.group(0);
            extractions.add(extractedContent);
        }

        return extractions;

    }

}
