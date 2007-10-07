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
package org.apache.tika.parser;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import org.apache.log4j.Logger;
import org.apache.oro.text.regex.MalformedPatternException;
import org.apache.tika.config.Content;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.utils.RegexUtils;

/**
 * Parser decorator that post-processes the results from a decorated parser.
 * The post-processing takes care of filling in any "fulltext", "summary", and
 * regexp {@link Content} objects with the full text content returned by
 * the decorated parser. The post-processing also catches and logs any
 * exceptions thrown by the decorated parser.
 */
public class ParserPostProcessor extends ParserDecorator {

    /**
     * Logger instance.
     */
    private static final Logger logger =
        Logger.getLogger(ParserPostProcessor.class);

    /**
     * Creates a post-processing decorator for the given parser.
     *
     * @param parser the parser to be decorated
     */
    public ParserPostProcessor(Parser parser) {
        super(parser);
    }

    /**
     * Forwards the call to the delegated parser and post-processes the
     * results as described above.
     */
    public String parse(
            InputStream stream, Iterable<Content> contents, Metadata metadata)
            throws IOException, TikaException {
        try {
            String contentStr = super.parse(stream, contents, metadata);

            for (Content content : contents) {
                if ("fulltext".equalsIgnoreCase(content.getTextSelect())) {
                    metadata.set(content.getName(), contentStr);
                } else if ("summary".equalsIgnoreCase(content.getTextSelect())) {
                    int length = Math.min(contentStr.length(), 500);
                    String summary = contentStr.substring(0, length);
                    metadata.set(content.getName(), summary);
                } else if (content.getRegexSelect() != null) {
                    String regex = content.getRegexSelect();
                    try {
                        List<String> values =
                            RegexUtils.extract(contentStr, regex);
                        for (String value : values) {
                            metadata.add(content.getName(), value);
                        }
                    } catch (MalformedPatternException e) {
                        logger.error(
                                "Invalid regular expression: " + regex, e);
                    }
                }
            }

            return contentStr;
        } catch (Exception e) {
            logger.error("Parse error: " + e.getMessage(), e);
            return "";
        }
    }

}
