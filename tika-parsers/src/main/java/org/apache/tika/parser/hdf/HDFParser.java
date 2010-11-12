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

package org.apache.tika.parser.hdf;

//JDK imports
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.Set;

//TIKA imports
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.parser.netcdf.NetCDFParser;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

//NetCDF imports
import ucar.nc2.Attribute;
import ucar.nc2.Group;
import ucar.nc2.NetcdfFile;

/**
 * 
 * Since the {@link NetCDFParser} depends on the <a
 * href="http://www.unidata.ucar.edu/software/netcdf-java" >NetCDF-Java</a> API,
 * we are able to use it to parse HDF files as well. See <a href=
 * "http://www.unidata.ucar.edu/software/netcdf-java/formats/FileTypes.html"
 * >this link</a> for more information.
 * 
 */
public class HDFParser implements Parser {

    private static final Set<MediaType> SUPPORTED_TYPES = Collections
            .singleton(MediaType.application("x-hdf"));

    /*
     * (non-Javadoc)
     * 
     * @see
     * org.apache.tika.parser.netcdf.NetCDFParser#getSupportedTypes(org.apache
     * .tika.parser.ParseContext)
     */
    public Set<MediaType> getSupportedTypes(ParseContext context) {
        return SUPPORTED_TYPES;
    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * org.apache.tika.parser.netcdf.NetCDFParser#parse(java.io.InputStream,
     * org.xml.sax.ContentHandler, org.apache.tika.metadata.Metadata,
     * org.apache.tika.parser.ParseContext)
     */
    public void parse(InputStream stream, ContentHandler handler,
            Metadata metadata, ParseContext context) throws IOException,
            SAXException, TikaException {
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        this.writeStreamToMemory(stream, os);

        NetcdfFile ncFile = NetcdfFile.openInMemory("", os.toByteArray());
        this.unravelStringMet(ncFile, null, metadata);
    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * org.apache.tika.parser.netcdf.NetCDFParser#parse(java.io.InputStream,
     * org.xml.sax.ContentHandler, org.apache.tika.metadata.Metadata)
     */
    public void parse(InputStream stream, ContentHandler handler,
            Metadata metadata) throws IOException, SAXException, TikaException {
        this.parse(stream, handler, metadata, new ParseContext());
    }

    protected void unravelStringMet(NetcdfFile ncFile, Group group, Metadata met) {
        if (group == null) {
            group = ncFile.getRootGroup();
        }

        // unravel its string attrs
        for (Attribute attribute : group.getAttributes()) {
            if (attribute.isString()) {
                met.add(attribute.getName(), attribute.getStringValue());
            } else {
                // try and cast its value to a string
                met.add(attribute.getName(), String.valueOf(attribute
                        .getNumericValue()));
            }
        }

        for (Group g : group.getGroups()) {
            unravelStringMet(ncFile, g, met);
        }
    }

    protected void writeStreamToMemory(InputStream is, ByteArrayOutputStream os)
            throws TikaException {
        byte[] buf = new byte[512];

        try {
            while ((is.read(buf, 0, 512)) != -1) {
                os.write(buf);
            }
        } catch (Exception e) {
            e.printStackTrace();
            throw new TikaException(e.getMessage());
        }
    }

}
