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
 * Tika to XMP mapping for the binary MS formats Word (.doc), Excel (.xls) and PowerPoint (.ppt).
 */
public class MSOfficeBinaryConverter extends AbstractConverter {
    public MSOfficeBinaryConverter() throws TikaException {
        super();
    }

    protected static final Set<Namespace> ADDITIONAL_NAMESPACES = Collections
            .unmodifiableSet( new HashSet<Namespace>( Arrays.asList( new Namespace(
                    OfficeOpenXMLCore.NAMESPACE_URI, OfficeOpenXMLCore.PREFIX ), new Namespace(
                    OfficeOpenXMLExtended.NAMESPACE_URI, OfficeOpenXMLExtended.PREFIX ) ) ) );

    /**
     * @throws XMPException
     *             Forwards XMP errors
     * @see ITikaToXMPConverter#process(Metadata)
     */
    public XMPMeta process(Metadata metadata) throws XMPException {
        super.setMetadata( metadata );

        // For all formats, Tika uses the same keys
        createProperty( HttpHeaders.CONTENT_TYPE, XMPConst.NS_DC, "format" );
        createProperty( OfficeOpenXMLExtended.APPLICATION, XMPConst.NS_XMP, "CreatorTool" );
        createCommaSeparatedArray( TikaCoreProperties.CREATOR, XMPConst.NS_DC, "creator",
                PropertyOptions.ARRAY_ORDERED );
        createProperty( OfficeOpenXMLCore.CATEGORY, XMPConst.NS_IPTCCORE, "intellectualGenre" );
        createProperty( TikaCoreProperties.CREATED, XMPConst.NS_XMP, "CreateDate" );
        createProperty( Office.CHARACTER_COUNT, OfficeOpenXMLExtended.NAMESPACE_URI, "Characters" );
        createProperty( TikaCoreProperties.COMMENTS, XMPConst.NS_PDFX, "Comments" );
        createProperty( OfficeOpenXMLExtended.COMPANY, OfficeOpenXMLExtended.NAMESPACE_URI,
                "Company" );
        createCommaSeparatedArray( TikaCoreProperties.KEYWORDS, XMPConst.NS_DC, "subject",
                PropertyOptions.ARRAY );
        createLangAltProperty( TikaCoreProperties.DESCRIPTION, XMPConst.NS_DC, "description" );
        createProperty( TikaCoreProperties.LANGUAGE, OfficeOpenXMLCore.NAMESPACE_URI, "language" );
        createProperty( TikaCoreProperties.PRINT_DATE, OfficeOpenXMLCore.NAMESPACE_URI,
                "lastPrinted" );
        createProperty( TikaCoreProperties.MODIFIED, XMPConst.NS_XMP, "ModifyDate" );
        createProperty( Office.PAGE_COUNT, XMPConst.TYPE_PAGEDFILE, "NPages" );
        createProperty( OfficeOpenXMLCore.REVISION, OfficeOpenXMLCore.NAMESPACE_URI, "revision" );
        createProperty( Office.SLIDE_COUNT, OfficeOpenXMLExtended.NAMESPACE_URI, "Pages" );
        createProperty( OfficeOpenXMLExtended.TEMPLATE, OfficeOpenXMLExtended.NAMESPACE_URI,
                "Template" );
        createLangAltProperty( TikaCoreProperties.TITLE, XMPConst.NS_DC, "title" );
        createProperty( Office.WORD_COUNT, OfficeOpenXMLExtended.NAMESPACE_URI, "Words" );
        // Not mapped: (MSOffice) Edit-Time ???
        // Not mapped: (MSOffice) Last-Author ???
        // not mapped: (MSOffice) Security ???

        return super.getXMPMeta();
    }

    protected Set<Namespace> getAdditionalNamespaces() {
        return ADDITIONAL_NAMESPACES;
    }
}
