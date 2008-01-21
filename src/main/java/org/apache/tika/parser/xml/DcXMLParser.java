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
package org.apache.tika.parser.xml;

import org.apache.tika.metadata.DublinCore;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.sax.TeeContentHandler;
import org.apache.tika.sax.xpath.CompositeMatcher;
import org.apache.tika.sax.xpath.Matcher;
import org.apache.tika.sax.xpath.MatchingContentHandler;
import org.apache.tika.sax.xpath.XPathParser;
import org.xml.sax.ContentHandler;
import org.xml.sax.helpers.DefaultHandler;

/**
 * Dublin Core metadata parser
 */
public class DcXMLParser extends XMLParser {

    private static final XPathParser DC_XPATH = new XPathParser(
            "dc", "http://purl.org/dc/elements/1.1/");

    private static DefaultHandler getDublinCore(
            ContentHandler ch, Metadata md, String name, String element) {
        Matcher matcher = new CompositeMatcher(
                DC_XPATH.parse("//dc:" + element),
                DC_XPATH.parse("//dc:" + element + "//text()"));
        ContentHandler branch =
            new MatchingContentHandler(new MetadataHandler(md, name), matcher);
        return new TeeContentHandler(ch, branch);
    }

    protected DefaultHandler getDefaultHandler(ContentHandler ch, Metadata md) {
        DefaultHandler dh = super.getDefaultHandler(ch, md);
        dh = getDublinCore(dh, md, DublinCore.TITLE, "title");
        dh = getDublinCore(dh, md, DublinCore.SUBJECT, "subject");
        dh = getDublinCore(dh, md, DublinCore.CREATOR, "creator");
        dh = getDublinCore(dh, md, DublinCore.DESCRIPTION, "description");
        dh = getDublinCore(dh, md, DublinCore.PUBLISHER, "publisher");
        dh = getDublinCore(dh, md, DublinCore.CONTRIBUTOR, "contributor");
        dh = getDublinCore(dh, md, DublinCore.DATE, "date");
        dh = getDublinCore(dh, md, DublinCore.TYPE, "type");
        dh = getDublinCore(dh, md, DublinCore.FORMAT, "format");
        dh = getDublinCore(dh, md, DublinCore.IDENTIFIER, "identifier");
        dh = getDublinCore(dh, md, DublinCore.LANGUAGE, "language");
        dh = getDublinCore(dh, md, DublinCore.RIGHTS, "rights");
        return dh;
    }

}
