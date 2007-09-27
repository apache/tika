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
import java.util.Map;

import org.apache.log4j.Logger;
import org.apache.oro.text.regex.MalformedPatternException;
import org.apache.tika.config.Content;
import org.apache.tika.exception.TikaException;
import org.apache.tika.utils.RegexUtils;

/**
 * Abstract class Parser
 */
public abstract class Parser {

    private static final Logger logger = Logger.getLogger(Parser.class);

    private InputStream is;

    private String mimeType;

    private String namespace;

    private Map<String, Content> contents;

    private String contentStr;

    private boolean parsed = false; 

    public void setInputStream(InputStream is) {
        this.is = is;
    }

    /**
     * Get document mime type
     */
    public String getMimeType() {
        return mimeType;
    }

    /**
     * Set document mime type
     */
    public void setMimeType(String mimeType) {
        this.mimeType = mimeType;
    }

    public String getNamespace() {
        return namespace;
    }

    public void setNamespace(String namespace) {
        this.namespace = namespace;
    }

    /**
     * Get the string content of the document
     */
    public String getStrContent() {
        getContents();
        return contentStr;
    }

    /**
     * Get a content object, this object is configured from the TikaConfig Xml.
     * It could be a document metadata, XPath selection, regex selection or
     * fulltext
     */
    public Content getContent(String name) {
        return getContents().get(name);
    }

    /**
     * Returns the text associated with the Content named 'name',
     * or null if such a Content does not exist.
     *
     * @param name name of Content the caller wants the value of
     * @return the found Content's value, or null if not found
     */
    public String getContentValue(String name) {
        Content content = getContent(name);

        return content != null
                ? content.getValue()
                : null;
    }

    /**
     * Get a List of contents objects, this objects are configured from the
     * TikaConfig Xml file. It could be a document metadata, XPath selection,
     * regex selection or fulltext
     */
    public Map<String, Content> getContents() {
        if (!parsed) {
            try {
                try {
                    contentStr = parse(is, contents.values());
                } finally {
                    is.close();
                }

                for (Content content : contents.values()) {
                    if ("fulltext".equalsIgnoreCase(content.getTextSelect())) {
                        content.setValue(contentStr);
                    } else if ("summary".equalsIgnoreCase(content.getTextSelect())) {
                        int length = Math.min(contentStr.length(), 500);
                        String summary = contentStr.substring(0, length);
                        content.setValue(summary);
                    } else if (content.getRegexSelect() != null) {
                        String regex = content.getRegexSelect();
                        try {
                            List<String> values =
                                RegexUtils.extract(contentStr, regex);
                            if (values.size() > 0) {
                                content.setValue(values.get(0));
                                content.setValues(
                                        values.toArray(new String[values.size()]));
                            }
                        } catch (MalformedPatternException e) {
                            logger.error(
                                    "Invalid regular expression: " + regex, e);
                        }
                    }
                }
            } catch (Exception e) {
                logger.error("Parse error: " + e.getMessage(), e);
                contentStr = "";
            } finally {
                parsed = true;
            }
        }
        return contents;
    }

    public void setContents(Map<String, Content> contents) {
        this.contents = contents;
    }

    protected abstract String parse(
            InputStream stream, Iterable<Content> contents)
            throws IOException, TikaException;

}
