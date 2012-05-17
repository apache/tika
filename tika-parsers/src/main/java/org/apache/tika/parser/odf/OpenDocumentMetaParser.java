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
package org.apache.tika.parser.odf;

import org.apache.tika.metadata.MSOffice;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.Office;
import org.apache.tika.metadata.PagedText;
import org.apache.tika.metadata.Property;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.xml.AttributeDependantMetadataHandler;
import org.apache.tika.parser.xml.AttributeMetadataHandler;
import org.apache.tika.parser.xml.DcXMLParser;
import org.apache.tika.parser.xml.MetadataHandler;
import org.apache.tika.sax.TeeContentHandler;
import org.apache.tika.sax.xpath.CompositeMatcher;
import org.apache.tika.sax.xpath.Matcher;
import org.apache.tika.sax.xpath.MatchingContentHandler;
import org.apache.tika.sax.xpath.XPathParser;
import org.xml.sax.ContentHandler;

/**
 * Parser for OpenDocument <code>meta.xml</code> files.
 */
public class OpenDocumentMetaParser extends DcXMLParser {
    /**
     * Serial version UID
     */
    private static final long serialVersionUID = -8739250869531737584L;
   
    private static final String META_NS = "urn:oasis:names:tc:opendocument:xmlns:meta:1.0"; 
    private static final XPathParser META_XPATH = new XPathParser("meta", META_NS);

    private static ContentHandler getMeta(
            ContentHandler ch, Metadata md, Property property, String element) {
        Matcher matcher = new CompositeMatcher(
                META_XPATH.parse("//meta:" + element),
                META_XPATH.parse("//meta:" + element + "//text()"));
        ContentHandler branch =
            new MatchingContentHandler(new MetadataHandler(md, property), matcher);
        return new TeeContentHandler(ch, branch);
    }

    private static ContentHandler getUserDefined(
            ContentHandler ch, Metadata md) {
        Matcher matcher = new CompositeMatcher(
                META_XPATH.parse("//meta:user-defined/@meta:name"),
                META_XPATH.parse("//meta:user-defined//text()"));
        // eg <meta:user-defined meta:name="Info1">Text1</meta:user-defined> becomes custom:Info1=Text1
        ContentHandler branch = new MatchingContentHandler(
              new AttributeDependantMetadataHandler(md, "meta:name", Metadata.USER_DEFINED_METADATA_NAME_PREFIX),
              matcher);
        return new TeeContentHandler(ch, branch);
    }

    @Deprecated private static ContentHandler getStatistic(
            ContentHandler ch, Metadata md, String name, String attribute) {
        Matcher matcher =
            META_XPATH.parse("//meta:document-statistic/@meta:"+attribute);
        ContentHandler branch = new MatchingContentHandler(
              new AttributeMetadataHandler(META_NS, attribute, md, name), matcher);
        return new TeeContentHandler(ch, branch);
    }
    private static ContentHandler getStatistic(
          ContentHandler ch, Metadata md, Property property, String attribute) {
      Matcher matcher =
          META_XPATH.parse("//meta:document-statistic/@meta:"+attribute);
      ContentHandler branch = new MatchingContentHandler(
            new AttributeMetadataHandler(META_NS, attribute, md, property), matcher);
      return new TeeContentHandler(ch, branch);
  }

    protected ContentHandler getContentHandler(ContentHandler ch, Metadata md, ParseContext context) {
        // Process the Dublin Core Attributes 
        ch = super.getContentHandler(ch, md, context);
        
        // Process the OO Meta Attributes
        ch = getMeta(ch, md, TikaCoreProperties.CREATION_DATE, "creation-date");
        ch = getMeta(ch, md, TikaCoreProperties.KEYWORDS, "keyword");
        
        ch = getMeta(ch, md, Property.externalText(MSOffice.EDIT_TIME), "editing-duration");        
        ch = getMeta(ch, md, Property.externalText("editing-cycles"), "editing-cycles");
        ch = getMeta(ch, md, Property.externalText("initial-creator"), "initial-creator");
        ch = getMeta(ch, md, Property.externalText("generator"), "generator");
        
        // Process the user defined Meta Attributes
        ch = getUserDefined(ch, md);
        
        // Process the OO Statistics Attributes
        ch = getStatistic(ch, md, Office.OBJECT_COUNT,  "object-count");
        ch = getStatistic(ch, md, Office.IMAGE_COUNT,   "image-count");
        ch = getStatistic(ch, md, Office.PAGE_COUNT,    "page-count");
        ch = getStatistic(ch, md, PagedText.N_PAGES,    "page-count");
        ch = getStatistic(ch, md, Office.TABLE_COUNT,   "table-count");
        ch = getStatistic(ch, md, Office.PARAGRAPH_COUNT, "paragraph-count");
        ch = getStatistic(ch, md, Office.WORD_COUNT,      "word-count");
        ch = getStatistic(ch, md, Office.CHARACTER_COUNT, "character-count");
        
        // Legacy, Tika-1.0 style attributes
        // TODO Remove these in Tika 2.0
        ch = getStatistic(ch, md, MSOffice.OBJECT_COUNT,  "object-count");
        ch = getStatistic(ch, md, MSOffice.IMAGE_COUNT,   "image-count");
        ch = getStatistic(ch, md, MSOffice.PAGE_COUNT,    "page-count");
        ch = getStatistic(ch, md, MSOffice.TABLE_COUNT,   "table-count");
        ch = getStatistic(ch, md, MSOffice.PARAGRAPH_COUNT, "paragraph-count");
        ch = getStatistic(ch, md, MSOffice.WORD_COUNT,      "word-count");
        ch = getStatistic(ch, md, MSOffice.CHARACTER_COUNT, "character-count");
        
        // Legacy Statistics Attributes, replaced with real keys above
        // TODO Remove these shortly, eg after Tika 1.1 (TIKA-770)
        ch = getStatistic(ch, md, "nbPage", "page-count");
        ch = getStatistic(ch, md, "nbPara", "paragraph-count");
        ch = getStatistic(ch, md, "nbWord", "word-count");
        ch = getStatistic(ch, md, "nbCharacter", "character-count");
        ch = getStatistic(ch, md, "nbTab", "table-count");
        ch = getStatistic(ch, md, "nbObject", "object-count");
        ch = getStatistic(ch, md, "nbImg", "image-count");
        
        // Normalise the rest
        ch = new NSNormalizerContentHandler(ch);
        return ch;
    }

}
