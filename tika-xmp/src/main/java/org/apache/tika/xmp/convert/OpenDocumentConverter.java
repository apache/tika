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
import org.apache.tika.metadata.MSOffice;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.Office;
import org.apache.tika.metadata.PagedText;
import org.apache.tika.metadata.TikaCoreProperties;

import com.adobe.xmp.XMPConst;
import com.adobe.xmp.XMPException;
import com.adobe.xmp.XMPMeta;
import com.adobe.xmp.options.PropertyOptions;

/**
 * Tika to XMP mapping for the Open Document formats: Text (.odt), Spreatsheet (.ods), Graphics
 * (.odg) and Presentation (.odp).
 */
public class OpenDocumentConverter extends AbstractConverter {
    protected static final Set<Namespace> ADDITIONAL_NAMESPACES = Collections
            .unmodifiableSet( new HashSet<Namespace>( Arrays.asList( new Namespace(
                    Office.NAMESPACE_URI_DOC_META, Office.PREFIX_DOC_META ) ) ) );

    public OpenDocumentConverter() throws TikaException {
        super();
    }

    /**
     * @throws XMPException
     *             Forwards XMP errors
     * @see ITikaToXMPConverter#process(Metadata)
     */
    @Override
    public XMPMeta process(Metadata metadata) throws XMPException {
        super.setMetadata( metadata );

        createProperty( HttpHeaders.CONTENT_TYPE, XMPConst.NS_DC, "format" );

        createProperty( Office.CHARACTER_COUNT, Office.NAMESPACE_URI_DOC_META, "character-count" );
        createProperty( TikaCoreProperties.CREATED, XMPConst.NS_XMP, "CreateDate" );
        createCommaSeparatedArray( TikaCoreProperties.CREATOR, XMPConst.NS_DC, "creator",
                PropertyOptions.ARRAY_ORDERED );
        createProperty( TikaCoreProperties.MODIFIED, XMPConst.NS_XMP, "ModifyDate" );
        createProperty( TikaCoreProperties.COMMENTS, XMPConst.NS_PDFX, "Comments" );
        createCommaSeparatedArray( TikaCoreProperties.KEYWORDS, XMPConst.NS_DC, "subject",
                PropertyOptions.ARRAY );
        createLangAltProperty( TikaCoreProperties.DESCRIPTION, XMPConst.NS_DC, "description" );
        createProperty( MSOffice.EDIT_TIME, Office.NAMESPACE_URI_DOC_META, "editing-duration" );
        createProperty( "editing-cycles", Office.NAMESPACE_URI_DOC_META, "editing-cycles" );
        createProperty( "generator", XMPConst.NS_XMP, "CreatorTool" );
        createProperty( Office.IMAGE_COUNT, Office.NAMESPACE_URI_DOC_META, "image-count" );
        createProperty( "initial-creator", Office.NAMESPACE_URI_DOC_META, "initial-creator" );
        createProperty( Office.OBJECT_COUNT, Office.NAMESPACE_URI_DOC_META, "object-count" );
        createProperty( PagedText.N_PAGES, XMPConst.TYPE_PAGEDFILE, "NPages" );
        createProperty( Office.PARAGRAPH_COUNT, Office.NAMESPACE_URI_DOC_META, "paragraph-count" );
        createProperty( Office.TABLE_COUNT, Office.NAMESPACE_URI_DOC_META, "table-count" );
        createLangAltProperty( TikaCoreProperties.TITLE, XMPConst.NS_DC, "title" );
        createProperty( Office.WORD_COUNT, Office.NAMESPACE_URI_DOC_META, "word-count" );

        // duplicate properties not mapped:
        // nbImg | 0
        // nbObject | 0
        // nbPage | 1
        // nbPara | 3
        // nbTab | 0
        // nbWord | 5

        return super.getXMPMeta();
    }

    @Override
    protected Set<Namespace> getAdditionalNamespaces() {
        return ADDITIONAL_NAMESPACES;
    }
}
