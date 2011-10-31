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
package org.apache.tika.parser.xml;

import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Service;
import org.apache.tika.metadata.DublinCore;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.sax.TeeContentHandler;
import org.xml.sax.ContentHandler;

/**
 * Dublin Core metadata parser
 */
@Component @Service(Parser.class)
public class DcXMLParser extends XMLParser {

    private static ContentHandler getDublinCoreHandler(
            Metadata metadata, String name, String element) {
        return new ElementMetadataHandler(
                "http://purl.org/dc/elements/1.1/", element,
                metadata, name);
    }

    protected ContentHandler getContentHandler(
            ContentHandler handler, Metadata metadata, ParseContext context) {
        return new TeeContentHandler(
                super.getContentHandler(handler, metadata, context),
                getDublinCoreHandler(metadata, DublinCore.TITLE, "title"),
                getDublinCoreHandler(metadata, DublinCore.SUBJECT, "subject"),
                getDublinCoreHandler(metadata, DublinCore.CREATOR, "creator"),
                getDublinCoreHandler(metadata, DublinCore.DESCRIPTION, "description"),
                getDublinCoreHandler(metadata, DublinCore.PUBLISHER, "publisher"),
                getDublinCoreHandler(metadata, DublinCore.CONTRIBUTOR, "contributor"),
                getDublinCoreHandler(metadata, DublinCore.DATE.getName(), "date"),
                getDublinCoreHandler(metadata, DublinCore.TYPE, "type"),
                getDublinCoreHandler(metadata, DublinCore.FORMAT, "format"),
                getDublinCoreHandler(metadata, DublinCore.IDENTIFIER, "identifier"),
                getDublinCoreHandler(metadata, DublinCore.LANGUAGE, "language"),
                getDublinCoreHandler(metadata, DublinCore.RIGHTS, "rights"));
    }

}
