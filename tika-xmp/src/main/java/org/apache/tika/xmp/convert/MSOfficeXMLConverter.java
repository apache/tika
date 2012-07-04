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
package org.apache.tika.xmp.convert;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.HttpHeaders;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.Office;
import org.apache.tika.metadata.OfficeOpenXMLCore;
import org.apache.tika.metadata.OfficeOpenXMLExtended;
import org.apache.tika.metadata.TikaCoreProperties;

import com.adobe.xmp.XMPConst;
import com.adobe.xmp.XMPException;
import com.adobe.xmp.XMPMeta;
import com.adobe.xmp.options.PropertyOptions;

/**
 * Tika to XMP mapping for the Office Open XML formats Word (.docx), Excel (.xlsx) and PowerPoint
 * (.pptx).
 */
public class MSOfficeXMLConverter extends AbstractConverter {
    protected static final Set<Namespace> ADDITIONAL_NAMESPACES = Collections
            .unmodifiableSet( new HashSet<Namespace>( Arrays.asList( new Namespace(
                    OfficeOpenXMLCore.NAMESPACE_URI, OfficeOpenXMLCore.PREFIX ), new Namespace(
                    OfficeOpenXMLExtended.NAMESPACE_URI, OfficeOpenXMLExtended.PREFIX ) ) ) );

    public MSOfficeXMLConverter() throws TikaException {
        super();
    }

    @Override
    public XMPMeta process(Metadata metadata) throws XMPException {
        super.setMetadata( metadata );

        createProperty( HttpHeaders.CONTENT_TYPE, XMPConst.NS_DC, "format" );

        // Core Properties
        createProperty( OfficeOpenXMLCore.CATEGORY, XMPConst.NS_IPTCCORE, "intellectualGenre" );
        createProperty( OfficeOpenXMLCore.CONTENT_STATUS, OfficeOpenXMLCore.NAMESPACE_URI,
                "contentStatus" );
        createProperty( TikaCoreProperties.CREATED, XMPConst.NS_XMP, "CreateDate" );
        createCommaSeparatedArray( TikaCoreProperties.CREATOR, XMPConst.NS_DC, "creator",
                PropertyOptions.ARRAY_ORDERED );
        createProperty( TikaCoreProperties.COMMENTS, XMPConst.NS_PDFX, "Comments" );
        createProperty( TikaCoreProperties.IDENTIFIER, XMPConst.NS_DC, "identifier" );
        createCommaSeparatedArray( TikaCoreProperties.KEYWORDS, XMPConst.NS_DC, "subject",
                PropertyOptions.ARRAY );
        createLangAltProperty( TikaCoreProperties.DESCRIPTION, XMPConst.NS_DC, "description" );
        createProperty( TikaCoreProperties.LANGUAGE, XMPConst.NS_DC, "language" );
        createProperty( TikaCoreProperties.MODIFIER, OfficeOpenXMLCore.NAMESPACE_URI,
                "lastModifiedBy" );
        createProperty( TikaCoreProperties.PRINT_DATE, OfficeOpenXMLCore.NAMESPACE_URI,
                "lastPrinted" );
        createProperty( TikaCoreProperties.MODIFIED, XMPConst.NS_XMP, "ModifyDate" );
        createProperty( OfficeOpenXMLCore.REVISION, OfficeOpenXMLCore.NAMESPACE_URI, "revision" );
        createLangAltProperty( TikaCoreProperties.TITLE, XMPConst.NS_DC, "title" );
        createProperty( OfficeOpenXMLCore.VERSION, OfficeOpenXMLCore.NAMESPACE_URI, "version" );

        // Extended Properties

        // Put both App name and version in xmp:CreatorTool
        String creatorTool = "";
        String value = metadata.get( OfficeOpenXMLExtended.APPLICATION );
        if (value != null && value.length() > 0) {
            creatorTool = value;

            value = metadata.get( OfficeOpenXMLExtended.APP_VERSION );
            if (value != null && value.length() > 0) {
                creatorTool += " " + value;
            }
        }

        if (creatorTool.length() > 0) {
            meta.setProperty( XMPConst.NS_XMP, "CreatorTool", creatorTool );
        }

        createProperty( Office.CHARACTER_COUNT, OfficeOpenXMLExtended.NAMESPACE_URI, "Characters" );
        createProperty( Office.CHARACTER_COUNT_WITH_SPACES, OfficeOpenXMLExtended.NAMESPACE_URI,
                "CharactersWithSpaces" );
        createProperty( TikaCoreProperties.PUBLISHER, OfficeOpenXMLExtended.NAMESPACE_URI,
                "Company" );
        createProperty( Office.LINE_COUNT, OfficeOpenXMLExtended.NAMESPACE_URI, "Lines" );
        createProperty( OfficeOpenXMLExtended.MANAGER, OfficeOpenXMLExtended.NAMESPACE_URI,
                "Manager" );
        createProperty( OfficeOpenXMLExtended.NOTES, OfficeOpenXMLExtended.NAMESPACE_URI, "Notes" );
        createProperty( Office.PAGE_COUNT, XMPConst.TYPE_PAGEDFILE, "NPages" );
        createProperty( Office.PARAGRAPH_COUNT, OfficeOpenXMLExtended.NAMESPACE_URI, "Paragraphs" );
        createProperty( OfficeOpenXMLExtended.PRESENTATION_FORMAT,
                OfficeOpenXMLExtended.NAMESPACE_URI, "PresentationFormat" );
        createProperty( Office.SLIDE_COUNT, OfficeOpenXMLExtended.NAMESPACE_URI, "Slides" );
        createProperty( OfficeOpenXMLExtended.TEMPLATE, OfficeOpenXMLExtended.NAMESPACE_URI,
                "Template" );
        createProperty( OfficeOpenXMLExtended.TOTAL_TIME, OfficeOpenXMLExtended.NAMESPACE_URI,
                "TotalTime" );
        createProperty( Office.WORD_COUNT, OfficeOpenXMLExtended.NAMESPACE_URI, "Words" );

        return super.getXMPMeta();
    }

    @Override
    protected Set<Namespace> getAdditionalNamespaces() {
        return ADDITIONAL_NAMESPACES;
    }

}
