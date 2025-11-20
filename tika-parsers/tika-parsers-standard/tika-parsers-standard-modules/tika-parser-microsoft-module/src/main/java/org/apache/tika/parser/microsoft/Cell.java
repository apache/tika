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
package org.apache.tika.parser.microsoft;

import org.apache.tika.sax.XHTMLContentHandler;
import org.xml.sax.SAXException;

/**
 * Cell of content. Classes that implement this interface are used by
 * Tika parsers (currently just the MS Excel parser) to keep track of
 * individual pieces of content before they are rendered to the XHTML
 * SAX event stream.
 */
public interface Cell {

    /**
     * Renders the content to the given XHTML SAX event stream.
     *
     * @param handler
     * @throws SAXException
     */
    void render(XHTMLContentHandler handler) throws SAXException;

}
