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
package org.apache.tika.parser.sas;

import java.io.IOException;
import java.io.InputStream;
import java.text.Format;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import com.epam.parso.Column;
import com.epam.parso.DataWriterUtil;
import com.epam.parso.SasFileProperties;
import com.epam.parso.SasFileReader;
import com.epam.parso.impl.SasFileReaderImpl;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Database;
import org.apache.tika.metadata.HttpHeaders;
import org.apache.tika.metadata.MachineMetadata;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.OfficeOpenXMLExtended;
import org.apache.tika.metadata.PagedText;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.sax.XHTMLContentHandler;

/**
 * Processes the SAS7BDAT data columnar database file used by SAS and
 * other similar languages.
 */
public class SAS7BDATParser implements Parser {
    private static final long serialVersionUID = -2775485539937983150L;

    private static final MediaType TYPE_SAS7BDAT = MediaType.application("x-sas-data");
    private static final Set<MediaType> SUPPORTED_TYPES = Collections.singleton(TYPE_SAS7BDAT);

    @Override
    public Set<MediaType> getSupportedTypes(ParseContext context) {
        return SUPPORTED_TYPES;
    }

    @Override
    public void parse(InputStream stream, ContentHandler handler, Metadata metadata,
                      ParseContext context) throws IOException, SAXException, TikaException {
        metadata.set(Metadata.CONTENT_TYPE, TYPE_SAS7BDAT.toString());

        XHTMLContentHandler xhtml = new XHTMLContentHandler(handler, metadata);
        xhtml.startDocument();

        SasFileReader sas = new SasFileReaderImpl(stream);
        SasFileProperties props = sas.getSasFileProperties();

        // Record the interesting parts of the file's metadata
        metadata.set(TikaCoreProperties.TITLE, props.getName());
        metadata.set(TikaCoreProperties.CREATED, props.getDateCreated());
        metadata.set(TikaCoreProperties.MODIFIED, props.getDateModified());

        metadata.set(PagedText.N_PAGES, (int) props.getPageCount());
        metadata.set(Database.COLUMN_COUNT, (int) props.getColumnsCount());
        metadata.set(Database.ROW_COUNT, (int) props.getRowCount());

        // TODO Can we find more general properties for these / move
        //  these to more general places?
        metadata.set(HttpHeaders.CONTENT_ENCODING, props.getEncoding());
        metadata.set(OfficeOpenXMLExtended.APPLICATION, props.getServerType());
        metadata.set(OfficeOpenXMLExtended.APP_VERSION, props.getSasRelease());
        metadata.set(MachineMetadata.ARCHITECTURE_BITS, props.isU64() ? "64" : "32");
        metadata.set(MachineMetadata.ENDIAN,
                props.getEndianness() == 1 ? MachineMetadata.Endian.LITTLE.getName() :
                        MachineMetadata.Endian.BIG.getName());

        // The following SAS Metadata fields are currently ignored:
        // compressionMethod
        // sessionEncoding
        // fileType
        // osName - 
        // osType - 
        // mixPageRowCount
        // headerLength
        // pageLength
        // rowLength

        // Process the column metadata
        // TODO Find keys to record the format and the type
        for (Column c : sas.getColumns()) {
            String name = c.getLabel();
            if (name == null || name.isEmpty()) {
                name = c.getName();
            }
            metadata.add(Database.COLUMN_NAME, name);
        }


        // Output file contents as a table
        xhtml.element("h1", props.getName());
        xhtml.startElement("table");
        xhtml.newline();

        // Do the column headings
        xhtml.startElement("tr");
        for (Column c : sas.getColumns()) {
            String label = c.getLabel();
            if (label == null || label.isEmpty()) {
                label = c.getName();
            }

            xhtml.startElement("th", "title", c.getName());
            xhtml.characters(label);
            xhtml.endElement("th");
        }
        xhtml.endElement("tr");
        xhtml.newline();

        //TODO: initialize this on the first row and then apply
        Map<Integer, Format> formatMap = new HashMap<>();

        // Process each row in turn
        Object[] row = null;
        while ((row = sas.readNext()) != null) {
            xhtml.startElement("tr");
            for (String val : DataWriterUtil.getRowValues(sas.getColumns(), row, formatMap)) {
                // Use explicit start/end, rather than element, to 
                //  ensure that empty cells still get output
                xhtml.startElement("td");
                xhtml.characters(val);
                xhtml.endElement("td");
            }
            xhtml.endElement("tr");
            xhtml.newline();
        }

        // Finish
        xhtml.endElement("table");
        xhtml.endDocument();
    }
}
