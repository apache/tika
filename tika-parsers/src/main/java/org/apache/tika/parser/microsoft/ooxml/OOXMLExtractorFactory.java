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
import org.apache.poi.openxml4j.opc.PackageAccess;
import org.apache.poi.xslf.extractor.XSLFPowerPointExtractor;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xssf.extractor.XSSFEventBasedExcelExtractor;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.tika.exception.TikaException;
import org.apache.tika.io.CloseShieldInputStream;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.EmptyParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.pkg.ZipContainerDetector;
import org.apache.tika.sax.EndDocumentShieldingContentHandler;
import org.apache.xmlbeans.XmlException;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

/**
 * Figures out the correct {@link OOXMLExtractor} for the supplied document and
 * returns it.
 */
public class OOXMLExtractorFactory {

    public static void parse(
            InputStream stream, ContentHandler baseHandler,
            Metadata metadata, ParseContext context)
            throws IOException, SAXException, TikaException {
        Locale locale = context.get(Locale.class, Locale.getDefault());
        ExtractorFactory.setThreadPrefersEventExtractors(true);
        
        try {
            OOXMLExtractor extractor;
            OPCPackage pkg;

            // Locate or Open the OPCPackage for the file
            TikaInputStream tis = TikaInputStream.cast(stream);
            if (tis != null && tis.getOpenContainer() instanceof OPCPackage) {
                pkg = (OPCPackage) tis.getOpenContainer();
            } else if (tis != null && tis.hasFile()) {
                pkg = OPCPackage.open( tis.getFile().getPath(), PackageAccess.READ );
                tis.setOpenContainer(pkg);
            } else {
                InputStream shield = new CloseShieldInputStream(stream);
                pkg = OPCPackage.open(shield); 
            }
            
            // Get the type, and ensure it's one we handle
            MediaType type = ZipContainerDetector.detectOfficeOpenXML(pkg);
            if (type == null || OOXMLParser.UNSUPPORTED_OOXML_TYPES.contains(type)) {
               // Not a supported type, delegate to Empty Parser 
               EmptyParser.INSTANCE.parse(stream, baseHandler, metadata, context);
               return;
            }
            metadata.set(Metadata.CONTENT_TYPE, type.toString());

            // Have the appropriate OOXML text extractor picked
            POIXMLTextExtractor poiExtractor = ExtractorFactory.createExtractor(pkg);
            
            POIXMLDocument document = poiExtractor.getDocument();
            if (poiExtractor instanceof XSSFEventBasedExcelExtractor) {
               extractor = new XSSFExcelExtractorDecorator(
                   context, (XSSFEventBasedExcelExtractor)poiExtractor, locale);
            } else if (document == null) {
               throw new TikaException(
                     "Expecting UserModel based POI OOXML extractor with a document, but none found. " +
                     "The extractor returned was a " + poiExtractor
               );
            } else if (document instanceof XMLSlideShow) {
                extractor = new XSLFPowerPointExtractorDecorator(
                        context, (XSLFPowerPointExtractor) poiExtractor);
            } else if (document instanceof XWPFDocument) {
                extractor = new XWPFWordExtractorDecorator(
                        context, (XWPFWordExtractor) poiExtractor);
            } else {
                extractor = new POIXMLTextExtractorDecorator(context, poiExtractor);
            }
            
            // We need to get the content first, but not end 
            //  the document just yet
            EndDocumentShieldingContentHandler handler = 
               new EndDocumentShieldingContentHandler(baseHandler);
            extractor.getXHTML(handler, metadata, context);

            // Now we can get the metadata
            extractor.getMetadataExtractor().extract(metadata);
            
            // Then finish up
            handler.reallyEndDocument();
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
