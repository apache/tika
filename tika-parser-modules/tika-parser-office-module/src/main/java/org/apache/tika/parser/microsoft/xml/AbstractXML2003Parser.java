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

import org.apache.tika.metadata.*;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.xml.ElementMetadataHandler;
import org.apache.tika.parser.xml.XMLParser;
import org.apache.tika.sax.TeeContentHandler;
import org.xml.sax.Attributes;
import org.xml.sax.ContentHandler;
import org.xml.sax.helpers.AttributesImpl;


public abstract class AbstractXML2003Parser extends XMLParser {

    protected final static String MS_OFFICE_PROPERTIES_URN = "urn:schemas-microsoft-com:office:office";
    protected final static String MS_DOC_PROPERTIES_URN = "urn:schemas-microsoft-com:office:office";
    protected final static String MS_SPREADSHEET_URN = "urn:schemas-microsoft-com:office:spreadsheet";
    protected final static String WORD_ML_URL = "http://schemas.microsoft.com/office/word/2003/wordml";
    protected final static Attributes EMPTY_ATTRS = new AttributesImpl();

    protected final static String DOCUMENT_PROPERTIES = "DocumentProperties";
    protected final static String PICT = "pict";
    protected final static String BIN_DATA = "binData";

    protected final static String A = "a";
    protected final static String IMG = "img";
    protected final static String HREF = "href";
    protected final static String CDATA = "cdata";
    protected final static String TABLE = "table";
    protected final static String TBODY = "tbody";

    protected final static String HLINK = "hlink";
    protected final static String HLINK_DEST = "dest";
    protected final static String NAME_ATTR = "name";


    private static ContentHandler getMSPropertiesHandler(
            Metadata metadata, Property property, String element) {
        return new ElementMetadataHandler(
                MS_DOC_PROPERTIES_URN, element,
                metadata, property);
    }

    @Override
    protected ContentHandler getContentHandler(ContentHandler ch, Metadata md, ParseContext context) {
        ch = new TeeContentHandler(
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

        return ch;
    }
}
