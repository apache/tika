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

package org.apache.tika.parser.grib;

import java.io.IOException;
import java.io.InputStream;
import java.io.File;
import java.util.Collections;
import java.util.Set;
import org.apache.tika.exception.TikaException;
import org.apache.tika.io.TemporaryResources;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.Property;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.AbstractParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.XHTMLContentHandler;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;
import ucar.nc2.Attribute;
import ucar.nc2.Dimension;
import ucar.nc2.NetcdfFile;
import ucar.nc2.Variable;
import ucar.nc2.dataset.NetcdfDataset;

public class GribParser extends AbstractParser {

    private static final long serialVersionUID = 7855458954474247655L;

    public static final String GRIB_MIME_TYPE = "application/x-grib2";

    private final Set<MediaType> SUPPORTED_TYPES =
            Collections.singleton(MediaType.application("x-grib2"));

    public Set<MediaType> getSupportedTypes(ParseContext context) {
        return SUPPORTED_TYPES;
    }

    public void parse(InputStream stream, ContentHandler handler,
                      Metadata metadata, ParseContext context) throws IOException,
            SAXException, TikaException {

        //Set MIME type as grib2
        metadata.set(Metadata.CONTENT_TYPE, GRIB_MIME_TYPE);

        TikaInputStream tis = TikaInputStream.get(stream, new TemporaryResources());
        File gribFile = tis.getFile();

        try {
            NetcdfFile ncFile = NetcdfDataset.openFile(gribFile.getAbsolutePath(), null);

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
            xhtml.startElement("ul");
            xhtml.characters("dimensions:");
            xhtml.newline();

            for (Dimension dim : ncFile.getDimensions()){
                xhtml.element("li", dim.getFullName() + "=" + String.valueOf(dim.getLength()) + ";");
                xhtml.newline();
            }

            xhtml.startElement("ul");
            xhtml.characters("variables:");
            xhtml.newline();

            for (Variable var : ncFile.getVariables()){
                xhtml.element("p", String.valueOf(var.getDataType()) + var.getNameAndDimensions() + ";");
                for(Attribute element : var.getAttributes()){
                    xhtml.element("li", " :" + element + ";");
                    xhtml.newline();
                }
            }
            xhtml.endElement("ul");
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