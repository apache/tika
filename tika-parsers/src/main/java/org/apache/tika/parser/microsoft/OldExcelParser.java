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
package org.apache.tika.parser.microsoft;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import org.apache.poi.hssf.extractor.OldExcelExtractor;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.AbstractParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.XHTMLContentHandler;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

/**
 * A POI-powered Tika Parser for very old versions of Excel, from
 *  pre-OLE2 days, such as Excel 4.
 */
public class OldExcelParser extends AbstractParser {
   private static final long serialVersionUID = 4611820730372823452L;
   
   private static final Set<MediaType> SUPPORTED_TYPES =
        Collections.unmodifiableSet(new HashSet<MediaType>(Arrays.asList(
              MediaType.application("vnd.ms-excel.sheet.4"),
              MediaType.application("vnd.ms-excel.workspace.4"),
              MediaType.application("vnd.ms-excel.sheet.3"),
              MediaType.application("vnd.ms-excel.workspace.3"),
              MediaType.application("vnd.ms-excel.sheet.2")
         )));

    public Set<MediaType> getSupportedTypes(ParseContext context) {
        return SUPPORTED_TYPES;
    }

    /**
     * Extracts properties and text from an MS Document input stream
     */
    public void parse(
            InputStream stream, ContentHandler handler,
            Metadata metadata, ParseContext context)
            throws IOException, SAXException, TikaException {
       // Open the POI provided extractor
       OldExcelExtractor extractor = new OldExcelExtractor(stream);
       
       // We can't do anything about metadata, as these old formats
       //  didn't have any stored with them
       
       // Set the content type
       // TODO Get the version and type, to set as the Content Type
       
       // Have the text extracted and given to our Content Handler
       XHTMLContentHandler xhtml = new XHTMLContentHandler(handler, metadata);
       parse(extractor, xhtml);
    }
    
    protected static void parse(OldExcelExtractor extractor, 
            XHTMLContentHandler xhtml) throws TikaException, IOException, SAXException {
        // Get the whole text, as a single string
        String text = extractor.getText();
        
        // Split and output
        xhtml.startDocument();
        
        String line;
        BufferedReader reader = new BufferedReader(new StringReader(text));
        while ((line = reader.readLine()) != null) {
            xhtml.startElement("p");
            xhtml.characters(line);
            xhtml.endElement("p");
        }
        
        xhtml.endDocument();
    }
}
