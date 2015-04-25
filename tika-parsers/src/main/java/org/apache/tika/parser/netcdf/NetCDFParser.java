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

import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.Set;
import java.util.List;

import org.apache.tika.exception.TikaException;
import org.apache.tika.io.TemporaryResources;
import org.apache.tika.io.TikaInputStream;
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
import ucar.nc2.Variable;
import ucar.nc2.Dimension;

/**
 * A {@link Parser} for <a
 * href="http://www.unidata.ucar.edu/software/netcdf/index.html">NetCDF</a>
 * files using the UCAR, MIT-licensed <a
 * href="http://www.unidata.ucar.edu/software/netcdf-java/">NetCDF for Java</a>
 * API.
 */
public class NetCDFParser extends AbstractParser {

    /**
     * Serial version UID
     */
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

        TikaInputStream tis = TikaInputStream.get(stream, new TemporaryResources());
        try {
            NetcdfFile ncFile = NetcdfFile.open(tis.getFile().getAbsolutePath());
            metadata.set("File-Type-Description", ncFile.getFileTypeDescription());
            // first parse out the set of global attributes
            for (Attribute attr : ncFile.getGlobalAttributes()) {
                Property property = resolveMetadataKey(attr.getFullName());
                if (attr.getDataType().isString()) {
                    metadata.add(property, attr.getStringValue());
                } else if (attr.getDataType().isNumeric()) {
                    int value = attr.getNumericValue().intValue();
                    metadata.add(property, String.valueOf(value));
                }
            }


            XHTMLContentHandler xhtml = new XHTMLContentHandler(handler, metadata);
            xhtml.startDocument();
            xhtml.newline();
            xhtml.element("h1", "dimensions");
            xhtml.startElement("ul");
            xhtml.newline();
            for (Dimension dim : ncFile.getDimensions()) {
                xhtml.element("li", dim.getFullName() + " = " + dim.getLength());
            }
            xhtml.endElement("ul");

            xhtml.element("h1", "variables");
            xhtml.startElement("ul");
            xhtml.newline();
            for (Variable var : ncFile.getVariables()) {
                xhtml.startElement("li");
                xhtml.characters(var.getDataType() + " " + var.getNameAndDimensions());
                xhtml.newline();
                List<Attribute> attributes = var.getAttributes();
                if (!attributes.isEmpty()) {
                    xhtml.startElement("ul");
                    for (Attribute element : attributes) {
                        xhtml.element("li", element.toString());
                    }
                    xhtml.endElement("ul");
                }
                xhtml.endElement("li");
            }
            xhtml.endElement("ul");

            xhtml.endDocument();

        } catch (IOException e) {
            throw new TikaException("NetCDF parse error", e);
        }
    }

    private Property resolveMetadataKey(String localName) {
        if ("title".equals(localName)) {
            return TikaCoreProperties.TITLE;
        }
        return Property.internalText(localName);
    }
}