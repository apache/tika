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

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.microsoft.OfficeParser;
import org.apache.tika.parser.microsoft.ooxml.OOXMLParser;
import org.apache.tika.parser.odf.OpenDocumentParser;
import org.apache.tika.parser.rtf.RTFParser;

import com.adobe.xmp.XMPException;
import com.adobe.xmp.XMPMeta;
import com.adobe.xmp.XMPMetaFactory;

public class TikaToXMP {
    /**
     * Map from mimetype to converter class Must only be accessed through
     * <code>getConverterMap</code>
     */
    private static Map<MediaType, Class<? extends ITikaToXMPConverter>> converterMap;

    // --- public API implementation---

    public TikaToXMP() {
        // Nothing to do
    }

    /**
     * @see TikaToXMP#convert(Metadata, String) But the mimetype is retrieved from the metadata
     *      map.
     */
    public static XMPMeta convert(Metadata tikaMetadata) throws TikaException {
        if (tikaMetadata == null) {
            throw new IllegalArgumentException( "Metadata parameter must not be null" );
        }

        String mimetype = tikaMetadata.get( Metadata.CONTENT_TYPE );
        if (mimetype == null) {
            mimetype = tikaMetadata.get( TikaCoreProperties.FORMAT );
        }

        return convert( tikaMetadata, mimetype );
    }

    /**
     * Convert the given Tika metadata map to XMP object. If a mimetype is provided in the Metadata
     * map, a specific converter can be used, that converts all available metadata. If there is no
     * mimetype provided or no specific converter available a generic conversion is done which will
     * convert only those properties that are in known namespaces and are using the correct
     * prefixes.
     *
     * @param tikaMetadata
     *            the Metadata map from Tika
     * @param mimetype
     *            depicts the format's converter to use
     * @return XMP object
     * @throws TikaException
     */
    public static XMPMeta convert(Metadata tikaMetadata, String mimetype) throws TikaException {
        if (tikaMetadata == null) {
            throw new IllegalArgumentException( "Metadata parameter must not be null" );
        }

        ITikaToXMPConverter converter = null;

        if (isConverterAvailable( mimetype )) {
            converter = getConverter( mimetype );
        }
        else {
            converter = new GenericConverter();
        }

        XMPMeta xmp = null;

        if (converter != null) {
            try {
                xmp = converter.process( tikaMetadata );
            }
            catch (XMPException e) {
                throw new TikaException( "Tika metadata could not be converted to XMP", e );
            }
        }
        else {
            xmp = XMPMetaFactory.create(); // empty packet
        }

        return xmp;
    }

    /**
     * Check if there is a converter available which allows to convert the Tika metadata to XMP
     *
     * @param mimetype
     *            the Mimetype
     * @return true if the Metadata object can be converted or false if not
     */
    public static boolean isConverterAvailable(String mimetype) {
        MediaType type = MediaType.parse( mimetype );

        if (type != null) {
            return (getConverterMap().get( type ) != null);
        }

        return false;
    }

    /**
     * Retrieve a specific converter according to the mimetype
     *
     * @param mimetype
     *            the Mimetype
     * @return the converter or null, if none exists
     * @throws TikaException
     */
    public static ITikaToXMPConverter getConverter(String mimetype) throws TikaException {
        if (mimetype == null) {
            throw new IllegalArgumentException( "mimetype must not be null" );
        }

        ITikaToXMPConverter converter = null;

        MediaType type = MediaType.parse( mimetype );

        if (type != null) {
            Class<? extends ITikaToXMPConverter> clazz = getConverterMap().get( type );
            if (clazz != null) {
                try {
                    converter = clazz.newInstance();
                }
                catch (Exception e) {
                    throw new TikaException(
                            "TikaToXMP converter class cannot be instantiated for mimetype: "
                                    + type.toString(), e );
                }
            }
        }

        return converter;
    }

    // --- Private methods ---

    private static Map<MediaType, Class<? extends ITikaToXMPConverter>> getConverterMap() {
        if (converterMap == null) {
            converterMap = new HashMap<MediaType, Class<? extends ITikaToXMPConverter>>();
            initialize();
        }
        return converterMap;
    }

    /**
     * Initializes the map with supported converters.
     */
    private static void initialize() {
        // No particular parsing context is needed
        ParseContext parseContext = new ParseContext();

        // MS Office Binary File Format
        addConverter( new OfficeParser().getSupportedTypes( parseContext ),
                MSOfficeBinaryConverter.class );

        // Rich Text Format
        addConverter( new RTFParser().getSupportedTypes( parseContext ), RTFConverter.class );

        // MS Open XML Format
        addConverter( new OOXMLParser().getSupportedTypes( parseContext ),
                MSOfficeXMLConverter.class );

        // Open document format
        addConverter( new OpenDocumentParser().getSupportedTypes( parseContext ),
                OpenDocumentConverter.class );
    }

    private static void addConverter(Set<MediaType> supportedTypes,
            Class<? extends ITikaToXMPConverter> converter) {
        for (MediaType type : supportedTypes) {
            getConverterMap().put( type, converter );
        }
    }
}
