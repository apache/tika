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

import java.util.Set;

import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.Property;

import com.adobe.xmp.XMPConst;
import com.adobe.xmp.XMPException;
import com.adobe.xmp.XMPMeta;
import com.adobe.xmp.XMPMetaFactory;
import com.adobe.xmp.XMPSchemaRegistry;
import com.adobe.xmp.XMPUtils;
import com.adobe.xmp.options.PropertyOptions;

/**
 * Base class for Tika Metadata to XMP converter which provides some needed common functionality.
 */
public abstract class AbstractConverter implements ITikaToXMPConverter {
    private Metadata metadata;
    protected XMPMeta meta;

    abstract public XMPMeta process(Metadata metadata) throws XMPException;

    /**
     * Every Converter has to provide information about namespaces that are used additionally to the
     * core set of XMP namespaces.
     *
     * @return the additional namespace information
     */
    abstract protected Set<Namespace> getAdditionalNamespaces();

    public AbstractConverter() throws TikaException {
        meta = XMPMetaFactory.create();
        metadata = new Metadata();
        registerNamespaces( getAdditionalNamespaces() );
    }

    public void setMetadata(Metadata metadata) {
        this.metadata = metadata;
    }

    public XMPMeta getXMPMeta() {
        return meta;
    }

    // --- utility methods used by sub-classes ---

    /**
     * Registers a number <code>Namespace</code> information with XMPCore. Any already registered
     * namespace is not registered again.
     *
     * @param namespaces
     *            the list of namespaces to be registered
     * @throws TikaException
     *             in case a namespace oculd not be registered
     */
    protected void registerNamespaces(Set<Namespace> namespaces) throws TikaException {
        XMPSchemaRegistry registry = XMPMetaFactory.getSchemaRegistry();

        for (Namespace namespace : namespaces) {
            // Any already registered namespace is not registered again
            try {
                registry.registerNamespace( namespace.uri, namespace.prefix );
            }
            catch (XMPException e) {
                throw new TikaException(
                        "Namespace needed by converter could not be registiered with XMPCore", e );
            }
        }
    }

    /**
     * @see AbstractConverter#createProperty(String, String, String)
     */
    protected void createProperty(Property metadataProperty, String ns, String propertyName)
            throws XMPException {
        createProperty( metadataProperty.getName(), ns, propertyName );
    }

    /**
     * Creates a simple property.
     *
     * @param tikaKey
     *            Key in the Tika metadata map
     * @param ns
     *            namespace the property should be created in
     * @param propertyName
     *            name of the property
     * @throws XMPException
     *             if the property could not be created
     */
    protected void createProperty(String tikaKey, String ns, String propertyName)
            throws XMPException {
        String value = metadata.get( tikaKey );
        if (value != null && value.length() > 0) {
            meta.setProperty( ns, propertyName, value );
        }
    }

    /**
     * @see AbstractConverter#createLangAltProperty(String, String, String)
     */
    protected void createLangAltProperty(Property metadataProperty, String ns, String propertyName)
            throws XMPException {
        createLangAltProperty( metadataProperty.getName(), ns, propertyName );
    }

    /**
     * Creates a language alternative property in the x-default language
     *
     * @param tikaKey
     *            Key in the Tika metadata map
     * @param ns
     *            namespace the property should be created in
     * @param propertyName
     *            name of the property
     * @throws XMPException
     *             if the property could not be created
     */
    protected void createLangAltProperty(String tikaKey, String ns, String propertyName)
            throws XMPException {
        String value = metadata.get( tikaKey );
        if (value != null && value.length() > 0) {
            meta.setLocalizedText( ns, propertyName, null, XMPConst.X_DEFAULT, value );
        }
    }

    protected void createArrayProperty(Property metadataProperty, String nsDc,
            String arrayProperty, int arrayType) throws XMPException {
        createArrayProperty( metadataProperty.getName(), nsDc, arrayProperty, arrayType );
    }

    /**
     * Creates an array property from a list of values.
     *
     * @param tikaKey
     *            Key in the Tika metadata map
     * @param ns
     *            namespace the property should be created in
     * @param propertyName
     *            name of the property
     * @param arrayType
     *            depicts which kind of array shall be created
     * @throws XMPException
     *             if the property could not be created
     */
    protected void createArrayProperty(String tikaKey, String ns, String propertyName, int arrayType)
            throws XMPException {
        String[] values = metadata.getValues( tikaKey );
        if (values != null) {
            meta.setProperty( ns, propertyName, null, new PropertyOptions( arrayType ) );
            for (String value : values) {
                meta.appendArrayItem( ns, propertyName, value );
            }
        }
    }

    protected void createCommaSeparatedArray(Property metadataProperty, String nsDc,
            String arrayProperty, int arrayType) throws XMPException {
        createCommaSeparatedArray( metadataProperty.getName(), nsDc, arrayProperty, arrayType );
    }

    /**
     * Creates an array property from a comma separated list.
     *
     * @param tikaKey
     *            Key in the Tika metadata map
     * @param ns
     *            namespace the property should be created in
     * @param propertyName
     *            name of the property
     * @param arrayType
     *            depicts which kind of array shall be created
     * @throws XMPException
     *             if the property could not be created
     */
    protected void createCommaSeparatedArray(String tikaKey, String ns, String propertyName,
            int arrayType) throws XMPException {
        String value = metadata.get( tikaKey );
        if (value != null && value.length() > 0) {
            XMPUtils.separateArrayItems( meta, ns, propertyName, value, new PropertyOptions(
                    arrayType ), false );
        }
    }

}
