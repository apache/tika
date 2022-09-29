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

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.DublinCore;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.Office;
import org.apache.tika.metadata.OfficeOpenXMLCore;
import org.apache.tika.metadata.OfficeOpenXMLExtended;
import org.apache.tika.metadata.PagedText;
import org.apache.tika.metadata.Property;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.xml.AttributeDependantMetadataHandler;
import org.apache.tika.parser.xml.AttributeMetadataHandler;
import org.apache.tika.parser.xml.ElementMetadataHandler;
import org.apache.tika.parser.xml.MetadataHandler;
import org.apache.tika.parser.xml.XMLParser;
import org.apache.tika.sax.TeeContentHandler;
import org.apache.tika.sax.xpath.CompositeMatcher;
import org.apache.tika.sax.xpath.Matcher;
import org.apache.tika.sax.xpath.MatchingContentHandler;
import org.apache.tika.sax.xpath.XPathParser;

/**
 * Parser for OpenDocument <code>meta.xml</code> files.
 */
public class OpenDocumentMetaParser extends XMLParser {

    public static final String ODF_VERSION_KEY = "odf:version";
    /**
     * Serial version UID
     */
    private static final long serialVersionUID = -8739250869531737584L;

    private static final String META_NS = "urn:oasis:names:tc:opendocument:xmlns:meta:1.0";

    private static final String OFFICE_NS = "urn:oasis:names:tc:opendocument:xmlns:office:1.0";
    private static final XPathParser META_XPATH = new XPathParser("meta", META_NS);

    private static final XPathParser OFFICE_XPATH = new XPathParser("office", OFFICE_NS);
    private static ContentHandler getDublinCoreHandler(Metadata metadata, Property property,
                                                       String element) {
        return new ElementMetadataHandler(DublinCore.NAMESPACE_URI_DC, element, metadata, property);
    }

    private static ContentHandler getMeta(ContentHandler ch, Metadata md, Property property,
                                          String element) {
        Matcher matcher = new CompositeMatcher(META_XPATH.parse("//meta:" + element),
                META_XPATH.parse("//meta:" + element + "//text()"));
        ContentHandler branch =
                new MatchingContentHandler(new MetadataHandler(md, property), matcher);
        return new TeeContentHandler(ch, branch);
    }

    private static ContentHandler getUserDefined(ContentHandler ch, Metadata md) {
        Matcher matcher = new CompositeMatcher(META_XPATH.parse("//meta:user-defined/@meta:name"),
                META_XPATH.parse("//meta:user-defined//text()"));
        // eg <meta:user-defined meta:name="Info1">Text1</meta:user-defined> becomes
        // custom:Info1=Text1
        ContentHandler branch = new MatchingContentHandler(
                new AttributeDependantMetadataHandler(md, "meta:name",
                        Office.USER_DEFINED_METADATA_NAME_PREFIX), matcher);
        return new TeeContentHandler(ch, branch);
    }

    private static ContentHandler getVersion(ContentHandler ch, Metadata md) {
        Matcher matcher = OFFICE_XPATH.parse("/office:document-meta/@office:version");
        ContentHandler branch = new MatchingContentHandler(
                new AttributeMetadataHandler(
                        OFFICE_NS, "version", md,
                        ODF_VERSION_KEY), matcher);
        return new TeeContentHandler(ch, branch);
    }

    @Deprecated
    private static ContentHandler getStatistic(ContentHandler ch, Metadata md, String name,
                                               String attribute) {
        Matcher matcher = META_XPATH.parse("//meta:document-statistic/@meta:" + attribute);
        ContentHandler branch = new MatchingContentHandler(
                new AttributeMetadataHandler(META_NS, attribute, md, name), matcher);
        return new TeeContentHandler(ch, branch);
    }

    private static ContentHandler getStatistic(ContentHandler ch, Metadata md, Property property,
                                               String attribute) {
        Matcher matcher = META_XPATH.parse("//meta:document-statistic/@meta:" + attribute);
        ContentHandler branch = new MatchingContentHandler(
                new AttributeMetadataHandler(META_NS, attribute, md, property), matcher);
        return new TeeContentHandler(ch, branch);
    }

    static ContentHandler getContentHandler(Metadata md, ParseContext context,
                                            ContentHandler... handlers) {
        // We can no longer extend DcXMLParser due to the handling of dc:subject and dc:date
        // Process the Dublin Core Attributes
        ContentHandler ch =
                new TeeContentHandler(getDublinCoreHandler(md, TikaCoreProperties.TITLE, "title"),
                        getDublinCoreHandler(md, TikaCoreProperties.CREATOR, "creator"),
                        getDublinCoreHandler(md, TikaCoreProperties.DESCRIPTION, "description"),
                        getDublinCoreHandler(md, TikaCoreProperties.PUBLISHER, "publisher"),
                        getDublinCoreHandler(md, TikaCoreProperties.CONTRIBUTOR, "contributor"),
                        getDublinCoreHandler(md, TikaCoreProperties.TYPE, "type"),
                        getDublinCoreHandler(md, TikaCoreProperties.FORMAT, "format"),
                        getDublinCoreHandler(md, TikaCoreProperties.IDENTIFIER, "identifier"),
                        getDublinCoreHandler(md, TikaCoreProperties.LANGUAGE, "language"),
                        getDublinCoreHandler(md, TikaCoreProperties.RIGHTS, "rights"));
        ch = getVersion(ch, md);
        // Process the OO Meta Attributes
        ch = getMeta(ch, md, TikaCoreProperties.CREATED, "creation-date");
        // ODF uses dc:date for modified
        ch = new TeeContentHandler(ch,
                new ElementMetadataHandler(DublinCore.NAMESPACE_URI_DC, "date", md,
                        TikaCoreProperties.MODIFIED));

        // ODF uses dc:subject for description
        ch = new TeeContentHandler(ch,
                new ElementMetadataHandler(DublinCore.NAMESPACE_URI_DC, "subject", md,
                        OfficeOpenXMLCore.SUBJECT));

        ch = getMeta(ch, md, Office.KEYWORDS, "keyword");

        ch = getMeta(ch, md, OfficeOpenXMLExtended.TOTAL_TIME, "editing-duration");
        ch = getMeta(ch, md, Property.externalText("editing-cycles"), "editing-cycles");
        ch = getMeta(ch, md, TikaCoreProperties.CREATOR, "initial-creator");
        ch = getMeta(ch, md, Property.externalText("generator"), "generator");

        // Process the user defined Meta Attributes
        ch = getUserDefined(ch, md);

        // Process the OO Statistics Attributes
        ch = getStatistic(ch, md, Office.OBJECT_COUNT, "object-count");
        ch = getStatistic(ch, md, Office.IMAGE_COUNT, "image-count");
        ch = getStatistic(ch, md, Office.PAGE_COUNT, "page-count");
        ch = getStatistic(ch, md, PagedText.N_PAGES, "page-count");
        ch = getStatistic(ch, md, Office.TABLE_COUNT, "table-count");
        ch = getStatistic(ch, md, Office.PARAGRAPH_COUNT, "paragraph-count");
        ch = getStatistic(ch, md, Office.WORD_COUNT, "word-count");
        ch = getStatistic(ch, md, Office.CHARACTER_COUNT, "character-count");

        if (handlers != null && handlers.length > 0) {
            ContentHandler[] newHandlers = new ContentHandler[handlers.length + 1];
            newHandlers[0] = ch;
            System.arraycopy(handlers, 0, newHandlers, 1, handlers.length);
            ch = new TeeContentHandler(newHandlers);
        }
        // Normalise the rest
        ch = new NSNormalizerContentHandler(ch);
        return ch;
    }

    protected ContentHandler getContentHandler(ContentHandler ch, Metadata md,
                                               ParseContext context) {
        return getContentHandler(md, context, super.getContentHandler(ch, md, context));
    }

    @Override
    public void parse(InputStream stream, ContentHandler handler, Metadata metadata,
                      ParseContext context) throws IOException, SAXException, TikaException {
        super.parse(stream, handler, metadata, context);
        // Copy subject to description for OO2
        String odfSubject = metadata.get(OfficeOpenXMLCore.SUBJECT);
        if (odfSubject != null && !odfSubject.equals("") &&
                (metadata.get(TikaCoreProperties.DESCRIPTION) == null ||
                        metadata.get(TikaCoreProperties.DESCRIPTION).equals(""))) {
            metadata.set(TikaCoreProperties.DESCRIPTION, odfSubject);
        }
        //reset the dc:subject to include both keywords and subject
        //We can't relying on composite keys in the MatchingContentHandlers
        //because those are "setting" not "adding" to the Metadata object
        List<String> subjects = new ArrayList<>();
        if (metadata.getValues(Office.KEYWORDS) != null) {
            subjects.addAll(Arrays.asList(metadata.getValues(Office.KEYWORDS)));
        }

        if (metadata.getValues(OfficeOpenXMLCore.SUBJECT) != null) {
            subjects.addAll(Arrays.asList(metadata.getValues(OfficeOpenXMLCore.SUBJECT)));
        }

        if (subjects.size() > 0) {
            metadata.set(TikaCoreProperties.SUBJECT, subjects.toArray(new String[0]));
        }
    }

}
