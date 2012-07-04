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

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.DublinCore;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.Property;
import org.apache.tika.metadata.XMPRights;
import org.apache.tika.metadata.Property.PropertyType;

import com.adobe.xmp.XMPException;
import com.adobe.xmp.XMPMeta;
import com.adobe.xmp.XMPMetaFactory;
import com.adobe.xmp.XMPSchemaRegistry;
import com.adobe.xmp.options.PropertyOptions;

/**
 * Trys to convert as much of the properties in the <code>Metadata</code> map to XMP namespaces.
 * only those properties will be cnverted where the name contains a prefix and this prefix
 * correlates with a "known" prefix for a standard namespace. For example "dc:title" would be mapped
 * to the "title" property in the DublinCore namespace.
 */
public class GenericConverter extends AbstractConverter {
    public GenericConverter() throws TikaException {
        super();
    }

    @Override
    public XMPMeta process(Metadata metadata) throws XMPException {
        setMetadata( metadata );
        XMPSchemaRegistry registry = XMPMetaFactory.getSchemaRegistry();

        String[] keys = metadata.names();
        for (String key : keys) {
            String[] keyParts = key.split( Metadata.NAMESPACE_PREFIX_DELIMITER );
            if (keyParts.length > 0 && keyParts.length <= 2) {
                String uri = registry.getNamespaceURI( keyParts[0] );

                if (uri != null) {
                    // Tika properties where the type differs from the XMP specification
                    if (key.equals( DublinCore.TITLE.getName() )
                            || key.equals( DublinCore.DESCRIPTION.getName() )
                            || key.equals( XMPRights.USAGE_TERMS.getName() )) {
                        createLangAltProperty( key, uri, keyParts[1] );
                    }
                    else if (key.equals( DublinCore.CREATOR.getName() )) {
                        createArrayProperty( key, uri, keyParts[1], PropertyOptions.ARRAY_ORDERED );
                    }
                    else {
                        PropertyType type = Property.getPropertyType( key );
                        if (type != null) {
                            switch (type) {
                                case SIMPLE:
                                    createProperty( key, uri, keyParts[1] );
                                    break;
                                case BAG:
                                    createArrayProperty( key, uri, keyParts[1],
                                            PropertyOptions.ARRAY );
                                    break;
                                case SEQ:
                                    createArrayProperty( key, uri, keyParts[1],
                                            PropertyOptions.ARRAY_ORDERED );
                                    break;
                                case ALT:
                                    createArrayProperty( key, uri, keyParts[1],
                                            PropertyOptions.ARRAY_ALTERNATE );
                                    break;
                            // TODO Add support for structs and lang-alts, but those types are
                            // currently not used in Tika
                            }
                        }
                    }
                }
            } // ignore keys that are not qualified
        }

        return getXMPMeta();
    }

    @Override
    public Set<Namespace> getAdditionalNamespaces() {
        // no additional namespaces needed
        return Collections.unmodifiableSet( new HashSet<Namespace>() );
    }
}
