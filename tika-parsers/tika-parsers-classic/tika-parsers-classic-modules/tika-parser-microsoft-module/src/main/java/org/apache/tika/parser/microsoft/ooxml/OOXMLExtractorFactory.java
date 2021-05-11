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

import java.io.EOFException;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;

import org.apache.commons.compress.archivers.zip.UnsupportedZipFeatureException;
import org.apache.commons.io.input.CloseShieldInputStream;
import org.apache.poi.ooxml.POIXMLDocument;
import org.apache.poi.ooxml.extractor.ExtractorFactory;
import org.apache.poi.ooxml.extractor.POIXMLTextExtractor;
import org.apache.poi.openxml4j.exceptions.InvalidOperationException;
import org.apache.poi.openxml4j.exceptions.OpenXML4JException;
import org.apache.poi.openxml4j.opc.OPCPackage;
import org.apache.poi.openxml4j.opc.PackageAccess;
import org.apache.poi.openxml4j.opc.PackagePart;
import org.apache.poi.openxml4j.opc.PackageRelationshipCollection;
import org.apache.poi.util.LocaleUtil;
import org.apache.poi.xslf.extractor.XSLFPowerPointExtractor;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xslf.usermodel.XSLFRelation;
import org.apache.poi.xslf.usermodel.XSLFSlideShow;
import org.apache.poi.xssf.extractor.XSSFBEventBasedExcelExtractor;
import org.apache.poi.xssf.extractor.XSSFEventBasedExcelExtractor;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFRelation;
import org.apache.xmlbeans.XmlException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import org.apache.tika.detect.microsoft.ooxml.OPCPackageDetector;
import org.apache.tika.exception.RuntimeSAXException;
import org.apache.tika.exception.TikaException;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.EmptyParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.microsoft.OfficeParserConfig;
import org.apache.tika.parser.microsoft.ooxml.xps.XPSExtractorDecorator;
import org.apache.tika.parser.microsoft.ooxml.xps.XPSTextExtractor;
import org.apache.tika.parser.microsoft.ooxml.xslf.XSLFEventBasedPowerPointExtractor;
import org.apache.tika.parser.microsoft.ooxml.xwpf.XWPFEventBasedWordExtractor;
import org.apache.tika.utils.RereadableInputStream;
import org.apache.tika.zip.utils.ZipSalvager;

/**
 * Figures out the correct {@link OOXMLExtractor} for the supplied document and
 * returns it.
 */
public class OOXMLExtractorFactory {

    private static final Logger LOG = LoggerFactory.getLogger(OOXMLExtractorFactory.class);
    private static final int MAX_BUFFER_LENGTH = 1000000;

    public static void parse(InputStream stream, ContentHandler baseHandler, Metadata metadata,
                             ParseContext context) throws IOException, SAXException, TikaException {
        Locale locale = context.get(Locale.class, LocaleUtil.getUserLocale());
        ExtractorFactory.setThreadPrefersEventExtractors(true);

        //if there's a problem opening the zip file;
        //create a tmp file, and copy what you can read of it.
        File tmpRepairedCopy = null;

        OPCPackage pkg = null;
        try {
            OOXMLExtractor extractor = null;

            // Locate or Open the OPCPackage for the file
            TikaInputStream tis = TikaInputStream.cast(stream);
            if (tis != null && tis.getOpenContainer() instanceof OPCPackage) {
                pkg = (OPCPackage) tis.getOpenContainer();
            } else if (tis != null && tis.hasFile()) {
                try {
                    pkg = OPCPackage.open(tis.getFile().getPath(), PackageAccess.READ);
                } catch (InvalidOperationException e) {
                    tmpRepairedCopy = File.createTempFile("tika-ooxml-repair-", "");
                    ZipSalvager.salvageCopy(tis.getFile(), tmpRepairedCopy);
                    pkg = OPCPackage.open(tmpRepairedCopy, PackageAccess.READ);
                }
                tis.setOpenContainer(pkg);
            } else {
                //OPCPackage slurps rris into memory so we can close rris
                //without apparent problems
                try (RereadableInputStream rereadableInputStream = new RereadableInputStream(stream,
                        MAX_BUFFER_LENGTH, false)) {
                    try {
                        pkg = OPCPackage.open(new CloseShieldInputStream(rereadableInputStream));
                    } catch (EOFException e) {
                        rereadableInputStream.rewind();
                        tmpRepairedCopy = File.createTempFile("tika-ooxml-repair-", "");
                        ZipSalvager.salvageCopy(rereadableInputStream, tmpRepairedCopy, false);
                        //if there isn't enough left to be opened as a package
                        //throw an exception -- we may want to fall back to streaming
                        //parsing
                        pkg = OPCPackage.open(tmpRepairedCopy, PackageAccess.READ);
                    } catch (UnsupportedZipFeatureException e) {
                        if (e.getFeature() !=
                                UnsupportedZipFeatureException.Feature.DATA_DESCRIPTOR) {
                            throw e;
                        }
                        rereadableInputStream.rewind();
                        tmpRepairedCopy = File.createTempFile("tika-ooxml-repair-", "");
                        ZipSalvager.salvageCopy(rereadableInputStream, tmpRepairedCopy, false);
                        //if there isn't enough left to be opened as a package
                        //throw an exception -- we may want to fall back to streaming
                        //parsing
                        pkg = OPCPackage.open(tmpRepairedCopy, PackageAccess.READ);
                    }
                }
            }

            if (pkg != null) {
                PackageRelationshipCollection prc =
                        pkg.getRelationshipsByType(OOXMLParser.SIGNATURE_RELATIONSHIP);
                if (prc != null && prc.size() > 0) {
                    metadata.set(TikaCoreProperties.HAS_SIGNATURE, "true");
                }
            }

            MediaType type = null;
            String mediaTypeString = metadata.get(Metadata.CONTENT_TYPE);
            if (mediaTypeString != null) {
                type = MediaType.parse(mediaTypeString);
            }
            if (type != null && OOXMLParser.UNSUPPORTED_OOXML_TYPES.contains(type)) {
                // Not a supported type, delegate to Empty Parser
                EmptyParser.INSTANCE.parse(stream, baseHandler, metadata, context);
                return;
            }

            if (type == null || !OOXMLParser.SUPPORTED_TYPES.contains(type)) {
                // Get the type, and ensure it's one we handle
                type = OPCPackageDetector.detectOfficeOpenXML(pkg);
            }

            if (type == null || OOXMLParser.UNSUPPORTED_OOXML_TYPES.contains(type)) {
                // Not a supported type, delegate to Empty Parser
                EmptyParser.INSTANCE.parse(stream, baseHandler, metadata, context);
                return;
            }
            metadata.set(Metadata.CONTENT_TYPE, type.toString());
            // Have the appropriate OOXML text extractor picked
            POIXMLTextExtractor poiExtractor = null;
            // This has already been set by OOXMLParser's call to configure()
            // We can rely on this being non-null.
            OfficeParserConfig config = context.get(OfficeParserConfig.class);
            if (config.isUseSAXDocxExtractor()) {
                poiExtractor = trySXWPF(pkg);
            }
            if (poiExtractor == null) {
                poiExtractor = tryXSLF(pkg, config.isUseSAXPptxExtractor());
            }
            if (type.equals(OOXMLParser.XPS)) {
                poiExtractor = new XPSTextExtractor(pkg);
            }

            if (poiExtractor == null) {
                poiExtractor = (POIXMLTextExtractor) ExtractorFactory.createExtractor(pkg);
            }

            POIXMLDocument document = poiExtractor.getDocument();
            if (poiExtractor instanceof XSSFBEventBasedExcelExtractor) {
                extractor = new XSSFBExcelExtractorDecorator(context, poiExtractor, locale);
            } else if (poiExtractor instanceof XSSFEventBasedExcelExtractor) {
                extractor = new XSSFExcelExtractorDecorator(context, poiExtractor, locale);
            } else if (poiExtractor instanceof XWPFEventBasedWordExtractor) {
                extractor = new SXWPFWordExtractorDecorator(metadata, context,
                        (XWPFEventBasedWordExtractor) poiExtractor);
                metadata.add(TikaCoreProperties.TIKA_PARSED_BY,
                        XWPFEventBasedWordExtractor.class.getCanonicalName());
            } else if (poiExtractor instanceof XSLFEventBasedPowerPointExtractor) {
                extractor = new SXSLFPowerPointExtractorDecorator(metadata, context,
                        (XSLFEventBasedPowerPointExtractor) poiExtractor);
                metadata.add(TikaCoreProperties.TIKA_PARSED_BY,
                        XSLFEventBasedPowerPointExtractor.class.getCanonicalName());
            } else if (poiExtractor instanceof XPSTextExtractor) {
                extractor = new XPSExtractorDecorator(context, poiExtractor);
            } else if (document == null) {
                throw new TikaException(
                        "Expecting UserModel based POI OOXML extractor with a document, but none" +
                                " found. " +
                                "The extractor returned was a " + poiExtractor);
            } else if (document instanceof XMLSlideShow) {
                extractor = new XSLFPowerPointExtractorDecorator(context,
                        (org.apache.poi.xslf.extractor.XSLFPowerPointExtractor) poiExtractor);
            } else if (document instanceof XWPFDocument) {
                extractor = new XWPFWordExtractorDecorator(metadata, context,
                        (XWPFWordExtractor) poiExtractor);
            } else {
                extractor = new POIXMLTextExtractorDecorator(context, poiExtractor);
            }


            // Get the bulk of the metadata first, so that it's accessible during
            //  parsing if desired by the client (see TIKA-1109)
            extractor.getMetadataExtractor().extract(metadata);

            // Extract the text, along with any in-document metadata
            extractor.getXHTML(baseHandler, metadata, context);
        } catch (IllegalArgumentException e) {
            if (e.getMessage() != null &&
                    e.getMessage().startsWith("No supported documents found")) {
                throw new TikaException("TIKA-418: RuntimeException while getting content" +
                        " for thmx and xps file types", e);
            } else {
                throw new TikaException("Error creating OOXML extractor", e);
            }
        } catch (OpenXML4JException | XmlException e) {
            throw new TikaException("Error creating OOXML extractor", e);
        } catch (RuntimeSAXException e) {
            throw(SAXException) e.getCause();
        } finally {
            if (tmpRepairedCopy != null) {
                if (pkg != null) {
                    try {
                        pkg.close();
                    } catch (IOException e) {
                        LOG.warn("problem closing pkg file");
                    }
                }
                boolean deleted = tmpRepairedCopy.delete();
                if (!deleted) {
                    LOG.warn("failed to delete tmp (repair) file: " +
                            tmpRepairedCopy.getAbsolutePath());
                }
            }
        }
    }

    private static POIXMLTextExtractor trySXWPF(OPCPackage pkg)
            throws TikaException, XmlException, OpenXML4JException, IOException {
        PackageRelationshipCollection packageRelationshipCollection = pkg.getRelationshipsByType(
                "http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument");
        if (packageRelationshipCollection.size() == 0) {
            packageRelationshipCollection = pkg.getRelationshipsByType(
                    "http://purl.oclc.org/ooxml/officeDocument/relationships/officeDocument");
        }

        if (packageRelationshipCollection.size() == 0) {
            return null;
        }
        PackagePart corePart = pkg.getPart(packageRelationshipCollection.getRelationship(0));
        if (corePart == null) {
            throw new TikaException("Couldn't find core part.");
        }
        String targetContentType = corePart.getContentType();
        for (XWPFRelation relation : XWPFWordExtractor.SUPPORTED_TYPES) {
            if (targetContentType.equals(relation.getContentType())) {
                return new XWPFEventBasedWordExtractor(pkg);
            }
        }
        return null;
    }

    private static POIXMLTextExtractor tryXSLF(OPCPackage pkg, boolean eventBased)
            throws TikaException, XmlException, OpenXML4JException, IOException {

        PackageRelationshipCollection packageRelationshipCollection = pkg.getRelationshipsByType(
                "http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument");
        if (packageRelationshipCollection.size() == 0) {
            packageRelationshipCollection = pkg.getRelationshipsByType(
                    "http://purl.oclc.org/ooxml/officeDocument/relationships/officeDocument");
        }

        if (packageRelationshipCollection.size() == 0) {
            return null;
        }
        PackagePart corePart = pkg.getPart(packageRelationshipCollection.getRelationship(0));
        if (corePart == null) {
            throw new TikaException("Couldn't find core part");
        }
        String targetContentType = corePart.getContentType();

        XSLFRelation[] xslfRelations =
                org.apache.poi.xslf.extractor.XSLFPowerPointExtractor.SUPPORTED_TYPES;

        for (XSLFRelation xslfRelation : xslfRelations) {
            if (xslfRelation.getContentType().equals(targetContentType)) {
                if (eventBased) {
                    return new XSLFEventBasedPowerPointExtractor(pkg);
                } else {
                    return new XSLFPowerPointExtractor(new XSLFSlideShow(pkg));
                }
            }
        }

        if (XSLFRelation.THEME_MANAGER.getContentType().equals(targetContentType)) {
            if (eventBased) {
                return new XSLFEventBasedPowerPointExtractor(pkg);
            } else {
                return new XSLFPowerPointExtractor(new XSLFSlideShow(pkg));
            }
        }
        return null;
    }


}
