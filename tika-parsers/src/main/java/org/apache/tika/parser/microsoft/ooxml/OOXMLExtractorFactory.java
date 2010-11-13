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
package org.apache.tika.parser.microsoft.ooxml;

import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;

import org.apache.poi.POIXMLDocument;
import org.apache.poi.POIXMLTextExtractor;
import org.apache.poi.extractor.ExtractorFactory;
import org.apache.poi.openxml4j.exceptions.InvalidFormatException;
import org.apache.poi.openxml4j.exceptions.OpenXML4JException;
import org.apache.poi.openxml4j.opc.OPCPackage;
import org.apache.poi.xslf.XSLFSlideShow;
import org.apache.poi.xslf.extractor.XSLFPowerPointExtractor;
import org.apache.poi.xssf.extractor.XSSFExcelExtractor;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.tika.exception.TikaException;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;
import org.apache.xmlbeans.XmlException;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

/**
 * Figures out the correct {@link OOXMLExtractor} for the supplied document and
 * returns it.
 */
public class OOXMLExtractorFactory {

    public static void parse(
            InputStream stream, ContentHandler handler,
            Metadata metadata, ParseContext context)
            throws IOException, SAXException, TikaException {
        Locale locale = context.get(Locale.class, Locale.getDefault());
       
        try {
            OOXMLExtractor extractor;

            POIXMLTextExtractor poiExtractor;
            if(stream instanceof TikaInputStream && 
            	    ((TikaInputStream)stream).getOpenContainer() != null) {
               poiExtractor = ExtractorFactory.createExtractor(
                    (OPCPackage)((TikaInputStream)stream).getOpenContainer()
               );
            } else {
               poiExtractor = (POIXMLTextExtractor) ExtractorFactory.createExtractor(stream);
            }
            
            POIXMLDocument document = poiExtractor.getDocument();
            if (document instanceof XSLFSlideShow) {
                extractor = new XSLFPowerPointExtractorDecorator(
                        context, (XSLFPowerPointExtractor) poiExtractor);
            } else if (document instanceof XSSFWorkbook) {
                extractor = new XSSFExcelExtractorDecorator(
                        context, (XSSFExcelExtractor) poiExtractor, locale);
            } else if (document instanceof XWPFDocument) {
                extractor = new XWPFWordExtractorDecorator(
                        context, (XWPFWordExtractor) poiExtractor);
            } else {
                extractor = new POIXMLTextExtractorDecorator(context, poiExtractor);
            }

            extractor.getMetadataExtractor().extract(metadata);
            extractor.getXHTML(handler, metadata, context);
        } catch (IllegalArgumentException e) {
            if (e.getMessage().startsWith("No supported documents found")) {
                throw new TikaException(
                        "TIKA-418: RuntimeException while getting content"
                        + " for thmx and xps file types", e);
            } else {
                throw new TikaException("Error creating OOXML extractor", e);
            }
        } catch (InvalidFormatException e) {
            throw new TikaException("Error creating OOXML extractor", e);
        } catch (OpenXML4JException e) {
            throw new TikaException("Error creating OOXML extractor", e);
        } catch (XmlException e) {
            throw new TikaException("Error creating OOXML extractor", e);

        }
    }

}
