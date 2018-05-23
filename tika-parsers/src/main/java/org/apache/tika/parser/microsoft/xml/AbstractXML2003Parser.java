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
package org.apache.tika.parser.microsoft.xml;

import java.io.IOException;
import java.io.InputStream;

import org.apache.commons.io.input.CloseShieldInputStream;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.Office;
import org.apache.tika.metadata.OfficeOpenXMLCore;
import org.apache.tika.metadata.OfficeOpenXMLExtended;
import org.apache.tika.metadata.Property;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.parser.AbstractParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.xml.ElementMetadataHandler;
import org.apache.tika.sax.EmbeddedContentHandler;
import org.apache.tika.sax.OfflineContentHandler;
import org.apache.tika.sax.TaggedContentHandler;
import org.apache.tika.sax.TeeContentHandler;
import org.apache.tika.sax.XHTMLContentHandler;
import org.apache.tika.utils.XMLReaderUtils;
import org.xml.sax.Attributes;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.AttributesImpl;

import javax.xml.parsers.SAXParser;


public abstract class AbstractXML2003Parser extends AbstractParser {

    final static String MS_OFFICE_PROPERTIES_URN = "urn:schemas-microsoft-com:office:office";
    final static String MS_DOC_PROPERTIES_URN = "urn:schemas-microsoft-com:office:office";
    final static String MS_SPREADSHEET_URN = "urn:schemas-microsoft-com:office:spreadsheet";
    final static String MS_VML_URN = "urn:schemas-microsoft-com:vml";
    final static String WORD_ML_URL = "http://schemas.microsoft.com/office/word/2003/wordml";
    final static Attributes EMPTY_ATTRS = new AttributesImpl();

    final static String DOCUMENT_PROPERTIES = "DocumentProperties";
    final static String PICT = "pict";
    final static String BIN_DATA = "binData";

    final static String A = "a";
    final static String BODY = "body";
    final static String BR = "br";
    final static String CDATA = "cdata";
    final static String DIV = "div";
    final static String HREF = "href";
    final static String IMG = "img";
    final static String P = "p";
    final static String TD = "td";
    final static String TR = "tr";
    final static String TABLE = "table";
    final static String TBODY = "tbody";

    final static String HLINK = "hlink";
    final static String HLINK_DEST = "dest";
    final static String NAME_ATTR = "name";

    final static char[] NEWLINE = new char[] {'\n'};



    private static ContentHandler getMSPropertiesHandler(
            Metadata metadata, Property property, String element) {
        return new ElementMetadataHandler(
                MS_DOC_PROPERTIES_URN, element,
                metadata, property);
    }

    @Override
    public void parse(
            InputStream stream, ContentHandler handler,
            Metadata metadata, ParseContext context)
            throws IOException, SAXException, TikaException {
        setContentType(metadata);

        final XHTMLContentHandler xhtml =
                new XHTMLContentHandler(handler, metadata);
        xhtml.startDocument();

        TaggedContentHandler tagged = new TaggedContentHandler(xhtml);
        try {
            XMLReaderUtils.parseSAX(
                    new CloseShieldInputStream(stream),
                    new OfflineContentHandler(new EmbeddedContentHandler(
                            getContentHandler(tagged, metadata, context))),
                    context);
        } catch (SAXException e) {
            tagged.throwIfCauseOf(e);
            throw new TikaException("XML parse error", e);
        } finally {
            xhtml.endDocument();
        }
    }

    protected ContentHandler getContentHandler(ContentHandler ch, Metadata md, ParseContext context) {
        //ContentHandler is not currently used, but leave that as an option for
        //potential future additions
        return new TeeContentHandler(
                getMSPropertiesHandler(md, TikaCoreProperties.TITLE, "Title"),
                getMSPropertiesHandler(md, TikaCoreProperties.CREATOR, "Author"),
                getMSPropertiesHandler(md, Office.LAST_AUTHOR, "LastAuthor"),
                getMSPropertiesHandler(md, OfficeOpenXMLCore.REVISION, "Revision"),
                getMSPropertiesHandler(md, OfficeOpenXMLExtended.TOTAL_TIME, "TotalTime"),
                getMSPropertiesHandler(md, TikaCoreProperties.CREATED, "Created"),
                getMSPropertiesHandler(md, Office.SAVE_DATE, "LastSaved"),
                getMSPropertiesHandler(md, Office.PAGE_COUNT, "Pages"),
                getMSPropertiesHandler(md, Office.WORD_COUNT, "Words"),
                getMSPropertiesHandler(md, Office.CHARACTER_COUNT, "Characters"),
                getMSPropertiesHandler(md, Office.CHARACTER_COUNT_WITH_SPACES, "CharactersWithSpaces"),
                getMSPropertiesHandler(md, OfficeOpenXMLExtended.COMPANY, "Company"),
                getMSPropertiesHandler(md, Office.LINE_COUNT, "Lines"),
                getMSPropertiesHandler(md, Office.PARAGRAPH_COUNT, "Paragraphs"),
                getMSPropertiesHandler(md, OfficeOpenXMLCore.VERSION, "Version"));
    }

    abstract protected void setContentType(Metadata contentType);
}
