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

import java.io.IOException;
import java.io.NotSerializableException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.Calendar;
import java.util.Date;
import java.util.Enumeration;
import java.util.Map;
import java.util.Properties;

import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.Property;
import org.apache.tika.metadata.Property.PropertyType;
import org.apache.tika.metadata.PropertyTypeException;
import org.apache.tika.xmp.convert.TikaToXMP;

import com.adobe.xmp.XMPDateTime;
import com.adobe.xmp.XMPException;
import com.adobe.xmp.XMPIterator;
import com.adobe.xmp.XMPMeta;
import com.adobe.xmp.XMPMetaFactory;
import com.adobe.xmp.XMPSchemaRegistry;
import com.adobe.xmp.XMPUtils;
import com.adobe.xmp.options.IteratorOptions;
import com.adobe.xmp.options.PropertyOptions;
import com.adobe.xmp.options.SerializeOptions;
import com.adobe.xmp.properties.XMPProperty;

/**
 * Provides a conversion of the Metadata map from Tika to the XMP data model by also providing the
 * Metadata API for clients to ease transition. But clients can also work directly on the XMP data
 * model, by getting the XMPMeta reference from this class. Usually the instance would be
 * initialized by providing the Metadata object that had been returned from Tika-core which
 * populates the XMP data model with all properties that can be converted.
 *
 * This class is not serializable!
 */
@SuppressWarnings("serial")
public class XMPMetadata extends Metadata {
    /** The XMP data */
    private XMPMeta xmpData;
    /** Use the XMP namespace registry implementation */
    private static final XMPSchemaRegistry registry = XMPMetaFactory.getSchemaRegistry();

    /**
     * Initializes with an empty XMP packet
     */
    public XMPMetadata() {
        xmpData = XMPMetaFactory.create();
    }

    /**
     * @see #XMPMetadata(Metadata, String)
     * But the mimetype is retrieved from the metadata map.
     */
    public XMPMetadata(Metadata meta) throws TikaException {
        this.xmpData = TikaToXMP.convert( meta );
    }

    /**
     * Initializes the data by converting the Metadata information to XMP. If a mimetype is
     * provided, a specific converter can be used, that converts all available metadata. If there is
     * no mimetype provided or no specific converter available a generic conversion is done which
     * will convert only those properties that are in known namespaces and are using the correct
     * prefixes
     *
     * @param meta
     *            the Metadata information from Tika-core
     * @param mimetype
     *            mimetype information
     * @throws In
     *             case an error occured during conversion
     */
    public XMPMetadata(Metadata meta, String mimetype) throws TikaException {
        this.xmpData = TikaToXMP.convert( meta, mimetype );
    }

    /**
     * @see org.apache.tika.xmp.XMPMetadata#process(org.apache.tika.metadata.Metadata,
     *      java.lang.String)
     *  But the mimetype is retrieved from the metadata map.
     */
    public void process(Metadata meta) throws TikaException {
        this.xmpData = TikaToXMP.convert( meta );
    }

    /**
     * Converts the Metadata information to XMP. If a mimetype is provided, a specific converter can
     * be used, that converts all available metadata. If there is no mimetype provided or no
     * specific converter available a generic conversion is done which will convert only those
     * properties that are in known namespaces and are using the correct prefixes
     *
     * @param meta
     *            the Metadata information from Tika-core
     * @param mimetype
     *            mimetype information
     * @throws In
     *             case an error occured during conversion
     */
    public void process(Metadata meta, String mimetype) throws TikaException {
        this.xmpData = TikaToXMP.convert( meta, mimetype );
    }

    /**
     * Provides direct access to the XMP data model, in case a client prefers to work directly on it
     * instead of using the Metadata API
     *
     * @return the "internal" XMP data object
     */
    public XMPMeta getXMPData() {
        return xmpData;
    }

    // === Namespace Registry API === //
    /**
     * Register a namespace URI with a suggested prefix. It is not an error if the URI is already
     * registered, no matter what the prefix is. If the URI is not registered but the suggested
     * prefix is in use, a unique prefix is created from the suggested one. The actual registeed
     * prefix is always returned. The function result tells if the registered prefix is the
     * suggested one.
     * Note: No checking is presently done on either the URI or the prefix.
     *
     * @param namespaceURI
     *            The URI for the namespace. Must be a valid XML URI.
     * @param suggestedPrefix
     *            The suggested prefix to be used if the URI is not yet registered. Must be a valid
     *            XML name.
     * @return Returns the registered prefix for this URI, is equal to the suggestedPrefix if the
     *         namespace hasn't been registered before, otherwise the existing prefix.
     * @throws XMPException
     *             If the parameters are not accordingly set
     */
    public static String registerNamespace(String namespaceURI, String suggestedPrefix)
            throws XMPException {
        return registry.registerNamespace( namespaceURI, suggestedPrefix );
    }

    /**
     * Obtain the prefix for a registered namespace URI.
     * It is not an error if the namespace URI is not registered.
     *
     * @param namespaceURI
     *            The URI for the namespace. Must not be null or the empty string.
     * @return Returns the prefix registered for this namespace URI or null.
     */
    public static String getNamespacePrefix(String namespaceURI) {
        return registry.getNamespacePrefix( namespaceURI );
    }

    /**
     * Obtain the URI for a registered namespace prefix.
     * It is not an error if the namespace prefix is not registered.
     *
     * @param namespacePrefix
     *            The prefix for the namespace. Must not be null or the empty string.
     * @return Returns the URI registered for this prefix or null.
     */
    public static String getNamespaceURI(String namespacePrefix) {
        return registry.getNamespaceURI( namespacePrefix );
    }

    /**
     * @return Returns the registered prefix/namespace-pairs as map, where the keys are the
     *         namespaces and the values are the prefixes.
     */
    @SuppressWarnings("unchecked")
    public static Map<String, String> getNamespaces() {
        return registry.getNamespaces();
    }

    /**
     * @return Returns the registered namespace/prefix-pairs as map, where the keys are the prefixes
     *         and the values are the namespaces.
     */
    @SuppressWarnings("unchecked")
    public static Map<String, String> getPrefixes() {
        return registry.getPrefixes();
    }

    /**
     * Deletes a namespace from the registry.
     * <p>
     * Does nothing if the URI is not registered, or if the namespaceURI parameter is null or the
     * empty string.
     * <p>
     * Note: Not yet implemented.
     *
     * @param namespaceURI
     *            The URI for the namespace.
     */
    public static void deleteNamespace(String namespaceURI) {
        registry.deleteNamespace( namespaceURI );
    }

    // === Metadata API === //
    /**
     * @see org.apache.tika.xmp.XMPMetadata#isMultiValued(java.lang.String)
     */
    @Override
    public boolean isMultiValued(Property property) {
        return this.isMultiValued( property.getName() );
    }

    /**
     * Checks if the named property is an array.
     *
     * @see org.apache.tika.metadata.Metadata#isMultiValued(java.lang.String)
     */
    @Override
    public boolean isMultiValued(String name) {
        checkKey( name );

        String[] keyParts = splitKey( name );

        String ns = registry.getNamespaceURI( keyParts[0] );
        if (ns != null) {
            try {
                XMPProperty prop = xmpData.getProperty( ns, keyParts[1] );

                return prop.getOptions().isArray();
            }
            catch (XMPException e) {
                // Ignore
            }
        }

        return false;
    }

    /**
     * For XMP it is not clear what that API should return, therefor not implemented
     */
    @Override
    public String[] names() {
        throw new UnsupportedOperationException( "Not implemented" );
    }

    /**
     * Returns the value of a simple property or the first one of an array. The given name must
     * contain a namespace prefix of a registered namespace.
     *
     * @see org.apache.tika.metadata.Metadata#get(java.lang.String)
     */
    @Override
    public String get(String name) {
        checkKey( name );

        String value = null;
        String[] keyParts = splitKey( name );

        String ns = registry.getNamespaceURI( keyParts[0] );
        if (ns != null) {
            try {
                XMPProperty prop = xmpData.getProperty( ns, keyParts[1] );

                if (prop != null && prop.getOptions().isSimple()) {
                    value = prop.getValue();
                }
                else if (prop != null && prop.getOptions().isArray()) {
                    prop = xmpData.getArrayItem( ns, keyParts[1], 1 );
                    value = prop.getValue();
                }
                // in all other cases, null is returned
            }
            catch (XMPException e) {
                // Ignore
            }
        }

        return value;
    }

    /**
     * @see org.apache.tika.xmp.XMPMetadata#get(java.lang.String)
     */
    @Override
    public String get(Property property) {
        return this.get( property.getName() );
    }

    /**
     * @see org.apache.tika.xmp.XMPMetadata#get(java.lang.String)
     */
    @Override
    public Integer getInt(Property property) {
        Integer result = null;

        try {
            result = new Integer( XMPUtils.convertToInteger( this.get( property.getName() ) ) );
        }
        catch (XMPException e) {
            // Ignore
        }

        return result;
    }

    /**
     * @see org.apache.tika.xmp.XMPMetadata#get(java.lang.String)
     */
    @Override
    public Date getDate(Property property) {
        Date result = null;

        try {
            XMPDateTime xmpDate = XMPUtils.convertToDate( this.get( property.getName() ) );
            if (xmpDate != null) {
                Calendar cal = xmpDate.getCalendar();
                // TODO Timezone is currently lost
                // need another solution that preserves the timezone
                result = cal.getTime();
            }
        }
        catch (XMPException e) {
            // Ignore
        }

        return result;
    }

    /**
     * @see org.apache.tika.xmp.XMPMetadata#getValues(java.lang.String)
     */
    @Override
    public String[] getValues(Property property) {
        return this.getValues( property.getName() );
    }

    /**
     * Returns the value of a simple property or all if the property is an array and the elements
     * are of simple type. The given name must contain a namespace prefix of a registered namespace.
     *
     * @see org.apache.tika.metadata.Metadata#getValues(java.lang.String)
     */
    @Override
    public String[] getValues(String name) {
        checkKey( name );

        String[] value = null;
        String[] keyParts = splitKey( name );

        String ns = registry.getNamespaceURI( keyParts[0] );
        if (ns != null) {
            try {
                XMPProperty prop = xmpData.getProperty( ns, keyParts[1] );

                if (prop != null && prop.getOptions().isSimple()) {
                    value = new String[1];
                    value[0] = prop.getValue();
                }
                else if (prop != null && prop.getOptions().isArray()) {
                    int size = xmpData.countArrayItems( ns, keyParts[1] );
                    value = new String[size];
                    boolean onlySimpleChildren = true;

                    for (int i = 0; i < size && onlySimpleChildren; i++) {
                        prop = xmpData.getArrayItem( ns, keyParts[1], i + 1 );
                        if (prop.getOptions().isSimple()) {
                            value[i] = prop.getValue();
                        }
                        else {
                            onlySimpleChildren = false;
                        }
                    }

                    if (!onlySimpleChildren) {
                        value = null;
                    }
                }
                // in all other cases, null is returned
            }
            catch (XMPException e) {
                // Ignore
            }
        }

        return value;
    }

    /**
     * As this API could only possibly work for simple properties in XMP, it just calls the set
     * method, which replaces any existing value
     *
     * @see org.apache.tika.metadata.Metadata#add(java.lang.String, java.lang.String)
     */
    @Override
    public void add(String name, String value) {
        set( name, value );
    }

    /**
     * Sets the given property. If the property already exists, it is overwritten. Only simple
     * properties that use a registered prefix are stored in the XMP.
     *
     * @see org.apache.tika.metadata.Metadata#set(java.lang.String, java.lang.String)
     */
    @Override
    public void set(String name, String value) {
        checkKey( name );

        String[] keyParts = splitKey( name );

        String ns = registry.getNamespaceURI( keyParts[0] );
        if (ns != null) {
            try {
                xmpData.setProperty( ns, keyParts[1], value );
            }
            catch (XMPException e) {
                // Ignore
            }
        }
    }

    /**
     * @see org.apache.tika.xmp.XMPMetadata#set(java.lang.String, java.lang.String)
     */
    @Override
    public void set(Property property, String value) {
        this.set( property.getName(), value );
    }

    /**
     * @see org.apache.tika.xmp.XMPMetadata#set(java.lang.String, java.lang.String)
     */
    @Override
    public void set(Property property, int value) {
        // Can reuse the checks from the base class implementation which will call
        // the set(String, String) method in the end
        super.set( property, value );
    }

    /**
     * @see org.apache.tika.xmp.XMPMetadata#set(java.lang.String, java.lang.String)
     */
    @Override
    public void set(Property property, double value) {
        super.set( property, value );
    }

    /**
     * @see org.apache.tika.xmp.XMPMetadata#set(java.lang.String, java.lang.String)
     */
    @Override
    public void set(Property property, Date date) {
        super.set( property, date );
    }

    /**
     * Sets array properties. If the property already exists, it is overwritten. Only array
     * properties that use a registered prefix are stored in the XMP.
     *
     * @see org.apache.tika.metadata.Metadata#set(org.apache.tika.metadata.Property,
     *      java.lang.String[])
     */
    @Override
    public void set(Property property, String[] values) {
        checkKey( property.getName() );

        if (!property.isMultiValuePermitted()) {
            throw new PropertyTypeException( "Property is not of an array type" );
        }

        String[] keyParts = splitKey( property.getName() );

        String ns = registry.getNamespaceURI( keyParts[0] );
        if (ns != null) {
            try {
                int arrayType = tikaToXMPArrayType( property.getPrimaryProperty().getPropertyType() );
                xmpData.setProperty( ns, keyParts[1], null, new PropertyOptions( arrayType ) );

                for (String value : values) {
                    xmpData.appendArrayItem( ns, keyParts[1], value );
                }
            }
            catch (XMPException e) {
                // Ignore
            }
        }
    }

    /**
     * It will set all simple and array properties that have QName keys in registered namespaces.
     *
     * @see org.apache.tika.metadata.Metadata#setAll(java.util.Properties)
     */
    @Override
    public void setAll(Properties properties) {
        @SuppressWarnings("unchecked")
        Enumeration<String> names = (Enumeration<String>) properties.propertyNames();

        while (names.hasMoreElements()) {
            String name = names.nextElement();
            Property property = Property.get( name );
            if (property == null) {
                throw new PropertyTypeException( "Unknown property: " + name );
            }

            String value = properties.getProperty( name );

            if (property.isMultiValuePermitted()) {
                this.set( property, new String[] { value } );
            }
            else {
                this.set( property, value );
            }
        }
    }

    /**
     * @see org.apache.tika.xmp.XMPMetadata#remove(java.lang.String)
     */
    public void remove(Property property) {
        this.remove( property.getName() );
    }

    /**
     * Removes the given property from the XMP data. If it is a complex property the whole subtree
     * is removed
     *
     * @see org.apache.tika.metadata.Metadata#remove(java.lang.String)
     */
    @Override
    public void remove(String name) {
        checkKey( name );

        String[] keyParts = splitKey( name );

        String ns = registry.getNamespaceURI( keyParts[0] );
        if (ns != null) {
            xmpData.deleteProperty( ns, keyParts[1] );
        }
    }

    /**
     * Returns the number of top-level namespaces
     */
    @Override
    public int size() {
        int size = 0;

        try {
            // Get an iterator for the XMP packet, starting at the top level schema nodes
            XMPIterator nsIter = xmpData.iterator( new IteratorOptions().setJustChildren( true )
                    .setOmitQualifiers( true ) );
            // iterate all top level namespaces
            while (nsIter.hasNext()) {
                nsIter.next();
                size++;
            }
        }
        catch (XMPException e) {
            // ignore
        }

        return size;
    }

    /**
     * This method is not implemented, yet. It is very tedious to check for semantic equality of XMP
     * packets
     */
    @Override
    public boolean equals(Object o) {
        throw new UnsupportedOperationException( "Not implemented" );
    }

    /**
     * Serializes the XMP data in compact form without packet wrapper
     *
     * @see org.apache.tika.metadata.Metadata#toString()
     */
    @Override
    public String toString() {
        String result = null;
        try {
            result = XMPMetaFactory.serializeToString( xmpData, new SerializeOptions()
                    .setOmitPacketWrapper( true ).setUseCompactFormat( true ) );
        }
        catch (XMPException e) {
            // ignore
        }
        return result;
    }

    // The XMP object is not serializable!
    private void readObject(ObjectInputStream ois) throws ClassNotFoundException, IOException {
        throw new NotSerializableException();
    }

    // The XMP object is not serializable!
    private void writeObject(ObjectOutputStream ois) throws IOException {
        throw new NotSerializableException();
    }

    /**
     * Checks if the given key is a valid QName with a known standard namespace prefix
     *
     * @param key
     *            the key to check
     * @return true if the key is valid otherwise false
     */
    private void checkKey(String key) throws PropertyTypeException {
        if (key == null || key.length() == 0) {
            throw new PropertyTypeException( "Key must not be null" );
        }

        String[] keyParts = splitKey( key );
        if (keyParts == null) {
            throw new PropertyTypeException( "Key must be a QName in the form prefix:localName" );
        }

        if (registry.getNamespaceURI( keyParts[0] ) == null) {
            throw new PropertyTypeException( "Key does not use a registered Namespace prefix" );
        }
    }

    /**
     * Split the given key at the namespace prefix delimiter
     *
     * @param key
     *            the key to split
     * @return prefix and local name of the property or null if the key did not contain a delimiter
     *         or too much of them
     */
    private String[] splitKey(String key) {
        String[] keyParts = key.split( Metadata.NAMESPACE_PREFIX_DELIMITER );
        if (keyParts.length > 0 && keyParts.length <= 2) {
            return keyParts;
        }

        return null;
    }// checkKeyPrefix

    /**
     * Convert Tika array types to XMP array types
     *
     * @param type
     * @return
     */
    private int tikaToXMPArrayType(PropertyType type) {
        int result = 0;
        switch (type) {
            case BAG:
                result = PropertyOptions.ARRAY;
                break;
            case SEQ:
                result = PropertyOptions.ARRAY_ORDERED;
                break;
            case ALT:
                result = PropertyOptions.ARRAY_ALTERNATE;
                break;
        }
        return result;
    }
}
