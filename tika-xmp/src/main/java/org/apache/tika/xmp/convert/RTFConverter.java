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
import org.apache.tika.metadata.OfficeOpenXMLCore;
import org.apache.tika.metadata.OfficeOpenXMLExtended;
import org.apache.tika.metadata.TikaCoreProperties;

import com.adobe.xmp.XMPConst;
import com.adobe.xmp.XMPException;
import com.adobe.xmp.XMPMeta;
import com.adobe.xmp.options.PropertyOptions;

/**
 * Tika to XMP mapping for the RTF format.
 */
public class RTFConverter extends AbstractConverter {
    protected static final Set<Namespace> ADDITIONAL_NAMESPACES = Collections
            .unmodifiableSet( new HashSet<Namespace>( Arrays.asList( new Namespace(
                    OfficeOpenXMLExtended.NAMESPACE_URI, OfficeOpenXMLExtended.PREFIX ) ) ) );

    public RTFConverter() throws TikaException {
        super();
    }

    @Override
    public XMPMeta process(Metadata metadata) throws XMPException {
        setMetadata( metadata );

        createProperty( HttpHeaders.CONTENT_TYPE, XMPConst.NS_DC, "format" );

        createCommaSeparatedArray( TikaCoreProperties.CREATOR, XMPConst.NS_DC, "creator",
                PropertyOptions.ARRAY_ORDERED );
        createLangAltProperty( TikaCoreProperties.TITLE, XMPConst.NS_DC, "title" );
        createLangAltProperty( TikaCoreProperties.DESCRIPTION, XMPConst.NS_DC, "description" );
        createCommaSeparatedArray( TikaCoreProperties.KEYWORDS, XMPConst.NS_DC, "subject",
                PropertyOptions.ARRAY );
        createProperty( OfficeOpenXMLCore.CATEGORY, XMPConst.NS_IPTCCORE, "intellectualGenre" );
        createProperty( OfficeOpenXMLExtended.TEMPLATE, OfficeOpenXMLExtended.NAMESPACE_URI,
                "Template" );
        createProperty( TikaCoreProperties.COMMENTS, XMPConst.NS_PDFX, "Comments" );
        createProperty( OfficeOpenXMLExtended.COMPANY, OfficeOpenXMLExtended.NAMESPACE_URI,
                "Company" );
        createProperty( OfficeOpenXMLExtended.MANAGER, OfficeOpenXMLExtended.NAMESPACE_URI,
                "Manager" );

        return getXMPMeta();
    }

    @Override
    protected Set<Namespace> getAdditionalNamespaces() {
        return ADDITIONAL_NAMESPACES;
    }
}
