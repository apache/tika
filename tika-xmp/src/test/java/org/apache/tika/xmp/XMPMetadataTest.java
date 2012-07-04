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

import static org.junit.Assert.*;

import java.util.Date;
import java.util.Properties;

import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.DublinCore;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.Property;
import org.apache.tika.metadata.PropertyTypeException;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.metadata.XMPRights;
import org.junit.Before;
import org.junit.Test;

import com.adobe.xmp.XMPConst;
import com.adobe.xmp.XMPException;
import com.adobe.xmp.XMPMeta;
import com.adobe.xmp.XMPUtils;
import com.adobe.xmp.properties.XMPProperty;

public class XMPMetadataTest {
    private Metadata tikaMetadata;
    private XMPMetadata xmpMeta;

    private static final String GENERIC_MIMETYPE = "generic/mimetype";

    // --- SETUP ---
    @Before
    public void setUp() throws Exception {
        XMPMetadata.registerNamespace( DublinCore.NAMESPACE_URI_DC_TERMS,
                DublinCore.PREFIX_DC_TERMS );
        xmpMeta = new XMPMetadata();
        tikaMetadata = new Metadata();
        setupMetadata( tikaMetadata );
    }

    private void setupMetadata(Metadata metadata) {
        // simple property
        metadata.set( TikaCoreProperties.FORMAT, GENERIC_MIMETYPE );
        // language alternative
        metadata.set( TikaCoreProperties.TITLE, "title" );
        // array
        metadata.set( TikaCoreProperties.KEYWORDS, new String[] { "keyword1", "keyword2" } );
        // date
        metadata.set( TikaCoreProperties.MODIFIED, "2001-01-01T01:01" );
        // int simple property
        metadata.set( Property.internalInteger( "xmp:Integer" ), "2" );
    }

    // --- HELPER ---
    private void checkArrayValues(String[] values, String baseValue) {
        int i = 1;
        for (String value : values) {
            assertEquals( baseValue + i, value );
            i++;
        }
    }

    // --- TESTS ---
    @Test
    public void process_genericConversion_ok() throws TikaException, XMPException {
        xmpMeta.process( tikaMetadata, GENERIC_MIMETYPE );

        XMPMeta xmp = xmpMeta.getXMPData();

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
    public void isMultiValued_multiProp_true() throws TikaException {
        xmpMeta.process( tikaMetadata );

        assertTrue( xmpMeta.isMultiValued( TikaCoreProperties.KEYWORDS ) );
    }

    @Test
    public void isMultiValued_simpleProp_false() throws TikaException {
        xmpMeta.process( tikaMetadata );

        assertFalse( xmpMeta.isMultiValued( TikaCoreProperties.FORMAT ) );
    }

    @Test
    public void get_simpleProp_valueReturned() throws TikaException {
        xmpMeta.process( tikaMetadata );

        assertEquals( GENERIC_MIMETYPE, xmpMeta.get( TikaCoreProperties.FORMAT ) );
    }

    @Test
    public void get_arrayProp_firstValueReturned() throws TikaException {
        xmpMeta.process( tikaMetadata );

        assertEquals( "keyword1", xmpMeta.get( TikaCoreProperties.KEYWORDS ) );
    }

    @Test
    public void get_notExistingProp_null() throws TikaException {
        assertNull( xmpMeta.get( TikaCoreProperties.FORMAT ) );
    }

    @Test(expected = PropertyTypeException.class)
    public void get_nullInput_throw() {
        String notInitialized = null;
        xmpMeta.get( notInitialized );
    }

    @Test(expected = PropertyTypeException.class)
    public void get_notQualifiedKey_throw() {
        xmpMeta.get( "wrongKey" );
    }

    @Test(expected = PropertyTypeException.class)
    public void get_unknownPrefixKey_throw() {
        xmpMeta.get( "unknown:key" );
    }

    @Test
    public void getInt_IntegerProperty_valueReturned() throws TikaException {
        xmpMeta.process( tikaMetadata );

        assertEquals( new Integer( 2 ), xmpMeta.getInt( Property.get( "xmp:Integer" ) ) );
    }

    @Test
    public void getDate_DateProperty_valueReturned() throws TikaException, XMPException {
        xmpMeta.process( tikaMetadata );

        Date date = XMPUtils.convertToDate( "2001-01-01T01:01" ).getCalendar().getTime();
        assertTrue( date.equals( xmpMeta.getDate( TikaCoreProperties.MODIFIED ) ) );
    }

    @Test
    public void getValues_arrayProperty_allElementsReturned() throws TikaException {
        xmpMeta.process( tikaMetadata );

        String[] values = xmpMeta.getValues( TikaCoreProperties.KEYWORDS );
        assertEquals( 2, values.length );

        checkArrayValues( values, "keyword" );
    }

    @Test
    public void testSetAll() {
        Properties props = new Properties();
        props.put( TikaCoreProperties.FORMAT.getName(), "format" );
        props.put( TikaCoreProperties.KEYWORDS.getName(), "keyword" );

        xmpMeta.setAll( props );

        assertEquals( "format", xmpMeta.get( TikaCoreProperties.FORMAT ) );

        String[] values = xmpMeta.getValues( TikaCoreProperties.KEYWORDS );
        assertEquals( 1, values.length );

        assertEquals( "keyword", values[0] );
    }

    @Test
    public void set_simpleProp_ok() {
        xmpMeta.set( TikaCoreProperties.FORMAT, GENERIC_MIMETYPE );

        assertEquals( GENERIC_MIMETYPE, xmpMeta.get( TikaCoreProperties.FORMAT ) );
    }

    @Test(expected = PropertyTypeException.class)
    public void set_nullInput_throw() {
        String notInitialized = null;
        xmpMeta.set( notInitialized, "value" );
    }

    @Test(expected = PropertyTypeException.class)
    public void set_notQualifiedKey_throw() {
        xmpMeta.set( "wrongKey", "value" );
    }

    @Test(expected = PropertyTypeException.class)
    public void set_unknownPrefixKey_throw() {
        xmpMeta.set( "unknown:key", "value" );
    }

    @Test
    public void set_arrayProperty_ok() {
        xmpMeta.set( TikaCoreProperties.KEYWORDS, new String[] { "keyword1", "keyword2" } );

        String[] values = xmpMeta.getValues( TikaCoreProperties.KEYWORDS );
        assertEquals( 2, values.length );

        checkArrayValues( values, "keyword" );
    }

    @Test(expected = PropertyTypeException.class)
    public void set_simplePropWithMultipleValues_throw() {
        xmpMeta.set( TikaCoreProperties.FORMAT, new String[] { "value1", "value2" } );
    }

    @Test
    public void remove_existingProperty_propertyRemoved() throws TikaException {
        xmpMeta.process( tikaMetadata );

        assertNotNull( xmpMeta.get( TikaCoreProperties.FORMAT ) );

        xmpMeta.remove( TikaCoreProperties.FORMAT );

        assertNull( xmpMeta.get( TikaCoreProperties.FORMAT ) );
    }

    @Test
    public void size_numberOfNamespacesReturned() throws TikaException {
        xmpMeta.process( tikaMetadata );

        assertEquals( 3, xmpMeta.size() );

        xmpMeta.set( XMPRights.OWNER, "owner" );

        assertEquals( 4, xmpMeta.size() );
    }

}
