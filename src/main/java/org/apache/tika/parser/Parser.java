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

import java.io.InputStream;
import java.util.Map;

import org.apache.tika.config.Content;

/**
 * Abstract class Parser
 */
public abstract class Parser {

    private InputStream is;

    private String mimeType;

    private String namespace;

    private Map<String, Content> contents;

    protected String contentStr;

    public void setInputStream(InputStream is) {
        this.is = is;
    }

    public InputStream getInputStream() {
        return is;
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
     * Get a List of contents objects, this objects are configured from the
     * TikaConfig Xml file. It could be a document metadata, XPath selection,
     * regex selection or fulltext
     */
    public Map<String, Content> getContents() {
        return contents;
    }

    public void setContents(Map<String, Content> contents) {
        this.contents = contents;
    }

}
