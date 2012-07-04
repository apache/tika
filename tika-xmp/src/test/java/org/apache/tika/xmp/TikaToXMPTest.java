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
package org.apache.tika.xmp;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.OfficeOpenXMLCore;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.xmp.convert.ITikaToXMPConverter;
import org.apache.tika.xmp.convert.MSOfficeXMLConverter;
import org.apache.tika.xmp.convert.TikaToXMP;
import org.junit.Before;
import org.junit.Test;

import com.adobe.xmp.XMPConst;
import com.adobe.xmp.XMPException;
import com.adobe.xmp.XMPIterator;
import com.adobe.xmp.XMPMeta;
import com.adobe.xmp.XMPMetaFactory;
import com.adobe.xmp.properties.XMPProperty;

/**
 * Tests the Tika <code>Metadata</code> to XMP conversion functionatlity
 */
public class TikaToXMPTest {
    private Metadata tikaMetadata;

    private static final String OOXML_MIMETYPE = "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
    private static final String GENERIC_MIMETYPE = "generic/mimetype";

    // --- Set up ---
    @Before
    public void setup() {
        tikaMetadata = new Metadata();
    }

    private void setupOOXMLMetadata(Metadata metadata) {
        // simple property
        metadata.set( TikaCoreProperties.LANGUAGE, "language" );
        // language alternative
        metadata.set( TikaCoreProperties.TITLE, "title" );
        // comma separated array
        metadata.set( TikaCoreProperties.KEYWORDS, "keyword1,keyword2" );
        // OOXML specific simple prop
        metadata.set( TikaCoreProperties.MODIFIER, "lastModifiedBy" );
    }

    private void checkOOXMLMetadata(XMPMeta xmp) throws XMPException {
        // check simple property
        XMPProperty prop = xmp.getProperty( XMPConst.NS_DC, "language" );
        assertNotNull( prop );
        assertEquals( "language", prop.getValue() );

        // check lang alt
        prop = xmp.getLocalizedText( XMPConst.NS_DC, "title", null, XMPConst.X_DEFAULT );
        assertNotNull( prop );
        assertEquals( "title", prop.getValue() );

        // check array
        prop = xmp.getArrayItem( XMPConst.NS_DC, "subject", 1 );
        assertNotNull( prop );
        assertEquals( "keyword1", prop.getValue() );
        prop = xmp.getArrayItem( XMPConst.NS_DC, "subject", 2 );
        assertNotNull( prop );
        assertEquals( "keyword2", prop.getValue() );

        // check OOXML specific simple property
        prop = xmp.getProperty( OfficeOpenXMLCore.NAMESPACE_URI, "lastModifiedBy" );
        assertNotNull( prop );
        assertEquals( "lastModifiedBy", prop.getValue() );
    }

    // --- TESTS ---
    @Test
    public void convert_OOXMLMetadataWithMimetype_everythingConverted() throws XMPException,
            TikaException {
        setupOOXMLMetadata( tikaMetadata );
        tikaMetadata.set( Metadata.CONTENT_TYPE, OOXML_MIMETYPE );

        XMPMeta xmp = TikaToXMP.convert( tikaMetadata );

        checkOOXMLMetadata( xmp );
    }

    @Test
    public void convert_OOXMLMetadataWithExtraMimetype_everythingConverted() throws XMPException,
            TikaException {
        setupOOXMLMetadata( tikaMetadata );

        XMPMeta xmp = TikaToXMP.convert( tikaMetadata, OOXML_MIMETYPE );

        checkOOXMLMetadata( xmp );
    }

    @Test
    public void convert_OOXMLMetadataWithoutMimetype_onlyGeneralMetadataconverted()
            throws XMPException, TikaException {
        setupOOXMLMetadata( tikaMetadata );

        XMPMeta xmp = TikaToXMP.convert( tikaMetadata, null );

        // general metadata is converted
        // check simple property
        XMPProperty prop = xmp.getProperty( XMPConst.NS_DC, "language" );
        assertNotNull( prop );
        assertEquals( "language", prop.getValue() );

        // check lang alt
        prop = xmp.getLocalizedText( XMPConst.NS_DC, "title", null, XMPConst.X_DEFAULT );
        assertNotNull( prop );
        assertEquals( "title", prop.getValue() );

        // OOXML one is not, the namespace has also not been registiered as the converter has not
        // been used
        XMPMetaFactory.getSchemaRegistry().registerNamespace( OfficeOpenXMLCore.NAMESPACE_URI,
                OfficeOpenXMLCore.PREFIX );
        prop = xmp.getProperty( OfficeOpenXMLCore.NAMESPACE_URI, "lastModifiedBy" );
        assertNull( prop );
    }

    @Test
    public void convert_genericMetadataAllQualified_allConverted() throws XMPException,
            TikaException {
        // simple property
        tikaMetadata.set( TikaCoreProperties.FORMAT, GENERIC_MIMETYPE );
        // language alternative
        tikaMetadata.set( TikaCoreProperties.TITLE, "title" );
        // array
        tikaMetadata.set( TikaCoreProperties.KEYWORDS, new String[] { "keyword1", "keyword2" } );

        XMPMeta xmp = TikaToXMP.convert( tikaMetadata, null );

        // check simple property
        XMPProperty prop = xmp.getProperty( XMPConst.NS_DC, "format" );
        assertNotNull( prop );
        assertEquals( GENERIC_MIMETYPE, prop.getValue() );

        // check lang alt
        prop = xmp.getLocalizedText( XMPConst.NS_DC, "title", null, XMPConst.X_DEFAULT );
        assertNotNull( prop );
        assertEquals( "title", prop.getValue() );

        // check array
        prop = xmp.getArrayItem( XMPConst.NS_DC, "subject", 1 );
        assertNotNull( prop );
        assertEquals( "keyword1", prop.getValue() );
        prop = xmp.getArrayItem( XMPConst.NS_DC, "subject", 2 );
        assertNotNull( prop );
        assertEquals( "keyword2", prop.getValue() );
    }

    @Test
    public void convert_wrongGenericMetadata_notConverted() throws XMPException, TikaException {
        // unknown prefix
        tikaMetadata.set( "unknown:key", "unknownPrefixValue" );
        // not qualified key
        tikaMetadata.set( "wrongKey", "wrongKeyValue" );

        XMPMeta xmp = TikaToXMP.convert( tikaMetadata, null );

        // XMP is empty
        XMPIterator iter = xmp.iterator();
        assertFalse( iter.hasNext() );
    }

    @Test(expected = IllegalArgumentException.class)
    public void convert_nullInput_throw() throws TikaException {
        TikaToXMP.convert( null );
    }

    @Test
    public void isConverterAvailable_availableMime_true() {
        assertTrue( TikaToXMP.isConverterAvailable( OOXML_MIMETYPE ) );
    }

    @Test
    public void isConverterAvailable_noAvailableMime_false() {
        assertFalse( TikaToXMP.isConverterAvailable( GENERIC_MIMETYPE ) );
    }

    @Test
    public void isConverterAvailable_nullInput_false() {
        assertFalse( TikaToXMP.isConverterAvailable( null ) );
    }

    @Test
    public void getConverter_ConverterAvailable_class() throws TikaException {
        ITikaToXMPConverter converter = TikaToXMP.getConverter( OOXML_MIMETYPE );
        assertNotNull( converter );
        assertTrue( converter instanceof MSOfficeXMLConverter );
    }

    @Test
    public void getConverter_noConverterAvailable_null() throws TikaException {
        ITikaToXMPConverter converter = TikaToXMP.getConverter( GENERIC_MIMETYPE );
        assertNull( converter );
    }

    @Test(expected = IllegalArgumentException.class)
    public void getConverter_nullInput_throw() throws TikaException {
        TikaToXMP.getConverter( null );
    }
}
