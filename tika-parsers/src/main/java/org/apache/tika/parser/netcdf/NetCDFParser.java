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
package org.apache.tika.parser.netcdf;

//JDK imports
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.Set;

import org.apache.tika.exception.TikaException;
import org.apache.tika.io.IOUtils;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.Property;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.AbstractParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.sax.XHTMLContentHandler;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import ucar.nc2.Attribute;
import ucar.nc2.NetcdfFile;

/**
 * A {@link Parser} for <a
 * href="http://www.unidata.ucar.edu/software/netcdf/index.html">NetCDF</a>
 * files using the UCAR, MIT-licensed <a
 * href="http://www.unidata.ucar.edu/software/netcdf-java/">NetCDF for Java</a>
 * API.
 */
public class NetCDFParser extends AbstractParser {

    /** Serial version UID */
    private static final long serialVersionUID = -5940938274907708665L;

    private final Set<MediaType> SUPPORTED_TYPES =
        Collections.singleton(MediaType.application("x-netcdf"));

    /*
     * (non-Javadoc)
     * 
     * @see
     * org.apache.tika.parser.Parser#getSupportedTypes(org.apache.tika.parser
     * .ParseContext)
     */
    public Set<MediaType> getSupportedTypes(ParseContext context) {
        return SUPPORTED_TYPES;
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.apache.tika.parser.Parser#parse(java.io.InputStream,
     * org.xml.sax.ContentHandler, org.apache.tika.metadata.Metadata,
     * org.apache.tika.parser.ParseContext)
     */
    public void parse(InputStream stream, ContentHandler handler,
            Metadata metadata, ParseContext context) throws IOException,
            SAXException, TikaException {
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        IOUtils.copy(stream, os);

        String name = metadata.get(Metadata.RESOURCE_NAME_KEY);
        if (name == null) {
            name = "";
        }

        try {
            NetcdfFile ncFile = NetcdfFile.openInMemory(name, os.toByteArray());

            // first parse out the set of global attributes
            for (Attribute attr : ncFile.getGlobalAttributes()) {
                Property property = resolveMetadataKey(attr.getName());
                if (attr.getDataType().isString()) {
                    metadata.add(property, attr.getStringValue());
                } else if (attr.getDataType().isNumeric()) {
                    int value = attr.getNumericValue().intValue();
                    metadata.add(property, String.valueOf(value));
                }
            }
        } catch (IOException e) {
            throw new TikaException("NetCDF parse error", e);
        } 

        XHTMLContentHandler xhtml = new XHTMLContentHandler(handler, metadata);
        xhtml.startDocument();
        xhtml.endDocument();
    }
    
    private Property resolveMetadataKey(String localName) {
        if ("title".equals(localName)) {
            return TikaCoreProperties.TITLE;
        }
        return Property.internalText(localName);
    }

}
