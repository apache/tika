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
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Collections;
import java.util.Set;

import org.apache.commons.io.FileUtils;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;
import ucar.nc2.Attribute;
import ucar.nc2.Dimension;
import ucar.nc2.NetcdfFile;
import ucar.nc2.Variable;
import ucar.nc2.dataset.NetcdfDataset;

import org.apache.tika.annotation.TikaComponent;
import org.apache.tika.exception.TikaException;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.ClimateForcast;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.sax.XHTMLContentHandler;

@TikaComponent
public class GribParser implements Parser {

    public static final String GRIB_MIME_TYPE = "application/x-grib2";
    private static final long serialVersionUID = 7855458954474247655L;
    private final Set<MediaType> SUPPORTED_TYPES =
            Collections.singleton(MediaType.application("x-grib2"));

    public Set<MediaType> getSupportedTypes(ParseContext context) {
        return SUPPORTED_TYPES;
    }

    public void parse(TikaInputStream tis, ContentHandler handler, Metadata metadata,
                      ParseContext context) throws IOException, SAXException, TikaException {

        //Set MIME type as grib2
        metadata.set(Metadata.CONTENT_TYPE, GRIB_MIME_TYPE);
        //grib was not cleaning up its temp files no matter what we tried
        //this is a work around the creates a temp directory then copies the full input file
        //into that tmp directory.  We then delete the directory in the finally statement.
        Path tmpDir = Files.createTempDirectory("tika-grib-");

        try {
            XHTMLContentHandler xhtml;
            Path gribFile = Files.createTempFile(tmpDir, "tika-file", ".grib2");
            Files.copy(tis, gribFile, StandardCopyOption.REPLACE_EXISTING);

            try (NetcdfFile ncFile = NetcdfDataset.openFile(gribFile.toString(), null)) {

                // first parse out the set of global attributes
                for (Attribute attr : ncFile.getGlobalAttributes()) {
                    if (attr.getDataType().isString()) {
                        addGlobalAttribute(metadata, attr.getFullName(), attr.getStringValue());
                    } else if (attr.getDataType().isNumeric()) {
                        addGlobalAttribute(metadata, attr.getFullName(),
                                String.valueOf(attr.getNumericValue().intValue()));
                    }
                }

                xhtml = new XHTMLContentHandler(handler, metadata, context);

                xhtml.startDocument();

                xhtml.newline();
                xhtml.startElement("ul");
                xhtml.characters("dimensions:");
                xhtml.newline();

                for (Dimension dim : ncFile.getDimensions()) {
                    xhtml.element("li",
                            dim.getFullName() + "=" + String.valueOf(dim.getLength()) + ";");
                    xhtml.newline();
                }

                xhtml.startElement("ul");
                xhtml.characters("variables:");
                xhtml.newline();

                for (Variable var : ncFile.getVariables()) {
                    xhtml.element("p",
                            String.valueOf(var.getDataType()) + var.getNameAndDimensions() + ";");
                    for (Attribute element : var.getAttributes()) {
                        xhtml.element("li", " :" + element + ";");
                        xhtml.newline();
                    }
                }
            }
            xhtml.endElement("ul");
            xhtml.endElement("ul");
            xhtml.endDocument();

        } catch (IOException e) {
            throw new TikaException("NetCDF parse error", e);
        } finally {
            FileUtils.deleteDirectory(tmpDir.toFile());
        }
    }

    private static final Set<String> CF_GLOBAL_ATTRIBUTES = Set.of(
            ClimateForcast.PROGRAM_ID, ClimateForcast.COMMAND_LINE, ClimateForcast.HISTORY,
            ClimateForcast.TABLE_ID, ClimateForcast.INSTITUTION, ClimateForcast.SOURCE,
            ClimateForcast.CONTACT, ClimateForcast.PROJECT_ID, ClimateForcast.CONVENTIONS,
            ClimateForcast.REFERENCES, ClimateForcast.ACKNOWLEDGEMENT, ClimateForcast.REALIZATION,
            ClimateForcast.EXPERIMENT_ID, ClimateForcast.COMMENT, ClimateForcast.MODEL_NAME_ENGLISH);

    private static void addGlobalAttribute(Metadata metadata, String name, String value) {
        if ("title".equals(name)) {
            metadata.add(TikaCoreProperties.TITLE, value);
        } else if (CF_GLOBAL_ATTRIBUTES.contains(name)) {
            metadata.add(name, value);
        } else {
            metadata.add("grib:" + name, value);
        }
    }

}
