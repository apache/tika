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

/**
 * Dublin Core metadata parser
 */
public class DcXMLParser extends XMLParser {

    private static final XPathParser DC_XPATH = new XPathParser(
            "dc", "http://purl.org/dc/elements/1.1/");

    private static ContentHandler getDublinCore(
            ContentHandler ch, Metadata md, String name, String element) {
        Matcher matcher = new CompositeMatcher(
                DC_XPATH.parse("//dc:" + element),
                DC_XPATH.parse("//dc:" + element + "//text()"));
        ContentHandler branch =
            new MatchingContentHandler(new MetadataHandler(md, name), matcher);
        return new TeeContentHandler(ch, branch);
    }

    protected ContentHandler getContentHandler(ContentHandler ch, Metadata md) {
        ch = super.getContentHandler(ch, md);
        ch = getDublinCore(ch, md, DublinCore.TITLE, "title");
        ch = getDublinCore(ch, md, DublinCore.SUBJECT, "subject");
        ch = getDublinCore(ch, md, DublinCore.CREATOR, "creator");
        ch = getDublinCore(ch, md, DublinCore.DESCRIPTION, "description");
        ch = getDublinCore(ch, md, DublinCore.PUBLISHER, "publisher");
        ch = getDublinCore(ch, md, DublinCore.CONTRIBUTOR, "contributor");
        ch = getDublinCore(ch, md, DublinCore.DATE, "date");
        ch = getDublinCore(ch, md, DublinCore.TYPE, "type");
        ch = getDublinCore(ch, md, DublinCore.FORMAT, "format");
        ch = getDublinCore(ch, md, DublinCore.IDENTIFIER, "identifier");
        ch = getDublinCore(ch, md, DublinCore.LANGUAGE, "language");
        ch = getDublinCore(ch, md, DublinCore.RIGHTS, "rights");
        return ch;
    }

}
