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
package org.apache.tika.parser.opendocument;

import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.xml.DcXMLParser;
import org.apache.tika.parser.xml.MetadataHandler;
import org.apache.tika.sax.TeeContentHandler;
import org.apache.tika.sax.xpath.CompositeMatcher;
import org.apache.tika.sax.xpath.Matcher;
import org.apache.tika.sax.xpath.MatchingContentHandler;
import org.apache.tika.sax.xpath.XPathParser;
import org.xml.sax.ContentHandler;
import org.xml.sax.helpers.DefaultHandler;

/**
 * Parser for OpenDocument <code>meta.xml</code> files.
 */
public class OpenOfficeMetaParser extends DcXMLParser {

    private static final XPathParser META_XPATH = new XPathParser(
            "meta", "urn:oasis:names:tc:opendocument:xmlns:meta:1.0");

    private static DefaultHandler getMeta(
            ContentHandler ch, Metadata md, String name, String element) {
        Matcher matcher = new CompositeMatcher(
                META_XPATH.parse("//meta:" + element),
                META_XPATH.parse("//meta:" + element + "//text()"));
        ContentHandler branch =
            new MatchingContentHandler(new MetadataHandler(md, name), matcher);
        return new TeeContentHandler(ch, branch);
    }

    private static DefaultHandler getStatistic(
            ContentHandler ch, Metadata md, String name, String attribute) {
        Matcher matcher =
            META_XPATH.parse("//meta:document-statistic/@meta:" + attribute);
        ContentHandler branch =
            new MatchingContentHandler(new MetadataHandler(md, name), matcher);
        return new TeeContentHandler(ch, branch);
    }

    protected DefaultHandler getDefaultHandler(ContentHandler ch, Metadata md) {
        DefaultHandler dh = super.getDefaultHandler(ch, md);
        dh = getMeta(dh, md, Metadata.KEYWORDS, "keyword");
        dh = getMeta(dh, md, "generator", "generator");
        dh = getStatistic(dh, md, "nbTab", "table-count");
        dh = getStatistic(dh, md, "nbObject", "object-count");
        dh = getStatistic(dh, md, "nbImg", "image-count");
        dh = getStatistic(dh, md, "nbPage", "page-count");
        dh = getStatistic(dh, md, "nbPara", "paragraph-count");
        dh = getStatistic(dh, md, "nbWord", "word-count");
        dh = getStatistic(dh, md, "nbCharacter", "character-count");
        dh = new NSNormalizerContentHandler(dh);
        return dh;
    }

}
