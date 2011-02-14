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

package org.apache.tika.extractor;

import org.apache.tika.metadata.Metadata;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.io.InputStream;

public interface EmbeddedDocumentExtractor {
    boolean shouldParseEmbedded(Metadata metadata);

    /**
     * Processes the supplied embedded resource, calling the delegating
     *  parser with the appropriate details.
     * @param stream The embedded resource
     * @param handler The handler to use
     * @param metadata The metadata for the embedded resource
     * @param outputHtml Should we output HTML for this resource, or has the parser already done so?
     * @throws org.xml.sax.SAXException
     * @throws java.io.IOException
     */
    void parseEmbedded(
            InputStream stream, ContentHandler handler, Metadata metadata, boolean outputHtml)
            throws SAXException, IOException;
}
