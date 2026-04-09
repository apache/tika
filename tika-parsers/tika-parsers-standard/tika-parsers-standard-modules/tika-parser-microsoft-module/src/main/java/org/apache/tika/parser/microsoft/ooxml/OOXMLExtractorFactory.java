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
import java.util.Locale;
import java.util.Set;

import org.apache.poi.openxml4j.exceptions.OpenXML4JException;
import org.apache.poi.openxml4j.opc.OPCPackage;
import org.apache.poi.openxml4j.opc.PackageAccess;
import org.apache.poi.openxml4j.opc.PackagePart;
import org.apache.poi.openxml4j.opc.PackageRelationshipCollection;
import org.apache.poi.util.LocaleUtil;
import org.apache.poi.xslf.usermodel.XSLFRelation;
import org.apache.poi.xssf.usermodel.XSSFRelation;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFRelation;
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
import org.apache.tika.parser.microsoft.ooxml.xps.XPSExtractorDecorator;
import org.apache.tika.parser.microsoft.ooxml.xslf.XSLFEventBasedPowerPointExtractor;
import org.apache.tika.parser.microsoft.ooxml.xwpf.XWPFEventBasedWordExtractor;


/**
 * Figures out the correct {@link OOXMLExtractor} for the supplied document and
 * returns it.
 */
public class OOXMLExtractorFactory {

    private static final String OFFICE_DOCUMENT_REL =
            "http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument";
    private static final String STRICT_OFFICE_DOCUMENT_REL =
            "http://purl.oclc.org/ooxml/officeDocument/relationships/officeDocument";

    private static final XSLFRelation[] XSLF_RELATIONS = new XSLFRelation[]{
            XSLFRelation.MAIN, XSLFRelation.MACRO, XSLFRelation.MACRO_TEMPLATE,
            XSLFRelation.PRESENTATIONML,
            XSLFRelation.PRESENTATIONML_TEMPLATE, XSLFRelation.PRESENTATION_MACRO
    };

    private static final XSSFRelation[] XSSF_RELATIONS = new XSSFRelation[]{
            XSSFRelation.WORKBOOK, XSSFRelation.MACROS_WORKBOOK,
            XSSFRelation.TEMPLATE_WORKBOOK, XSSFRelation.MACRO_TEMPLATE_WORKBOOK,
            XSSFRelation.MACRO_ADDIN_WORKBOOK
    };

    public static void parse(TikaInputStream tis, ContentHandler baseHandler, Metadata metadata,
                             ParseContext context) throws IOException, SAXException, TikaException {
        Locale locale = context.get(Locale.class, LocaleUtil.getUserLocale());

        OPCPackage pkg = null;
        try {
            OOXMLExtractor extractor = null;

            if (tis.getOpenContainer() instanceof OPCPackageWrapper) {
                pkg = ((OPCPackageWrapper) tis.getOpenContainer()).getOPCPackage();
            } else {
                try {
                    pkg = OPCPackage.open(tis.getPath().toString(), PackageAccess.READ);
                } catch (RuntimeException e) {
                    throw new TikaException("Error opening OOXML file", e);
                }
                tis.setOpenContainer(new OPCPackageWrapper(pkg));
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
                EmptyParser.INSTANCE.parse(tis, baseHandler, metadata, context);
                return;
            }

            if (type == null || !OOXMLParser.SUPPORTED_TYPES.contains(type)) {
                type = OPCPackageDetector.detectOfficeOpenXML(pkg);
            }

            if (type == null || OOXMLParser.UNSUPPORTED_OOXML_TYPES.contains(type)) {
                EmptyParser.INSTANCE.parse(tis, baseHandler, metadata, context);
                return;
            }
            metadata.set(Metadata.CONTENT_TYPE, type.toString());

            // Detect format and create the appropriate extractor
            String coreContentType = getCorePartContentType(pkg);

            if (isVisioType(type)) {
                extractor = new VSDXExtractorDecorator(context, pkg);
            } else if (type.equals(OOXMLParser.XPS)) {
                extractor = new XPSExtractorDecorator(context, pkg);
            } else if (coreContentType != null) {
                // Try DOCX
                for (XWPFRelation relation : XWPFWordExtractor.SUPPORTED_TYPES) {
                    if (coreContentType.equals(relation.getContentType())) {
                        XWPFEventBasedWordExtractor wordExtractor =
                                new XWPFEventBasedWordExtractor(pkg);
                        extractor = new SXWPFWordExtractorDecorator(metadata, context,
                                wordExtractor);
                        metadata.add(TikaCoreProperties.TIKA_PARSED_BY,
                                XWPFEventBasedWordExtractor.class.getCanonicalName());
                        break;
                    }
                }

                // Try PPTX
                if (extractor == null) {
                    for (XSLFRelation relation : XSLF_RELATIONS) {
                        if (relation.getContentType().equals(coreContentType)) {
                            XSLFEventBasedPowerPointExtractor pptExtractor =
                                    new XSLFEventBasedPowerPointExtractor(pkg);
                            extractor = new SXSLFPowerPointExtractorDecorator(metadata, context,
                                    pptExtractor);
                            metadata.add(TikaCoreProperties.TIKA_PARSED_BY,
                                    XSLFEventBasedPowerPointExtractor.class.getCanonicalName());
                            break;
                        }
                    }
                    if (extractor == null &&
                            XSLFRelation.THEME_MANAGER.getContentType().equals(coreContentType)) {
                        XSLFEventBasedPowerPointExtractor pptExtractor =
                                new XSLFEventBasedPowerPointExtractor(pkg);
                        extractor = new SXSLFPowerPointExtractorDecorator(metadata, context,
                                pptExtractor);
                        metadata.add(TikaCoreProperties.TIKA_PARSED_BY,
                                XSLFEventBasedPowerPointExtractor.class.getCanonicalName());
                    }
                }

                // Try XLSB
                if (extractor == null &&
                        XSSFRelation.XLSB_BINARY_WORKBOOK.getContentType()
                                .equals(coreContentType)) {
                    extractor = new XSSFBExcelExtractorDecorator(context, pkg, locale);
                }

                // Try XLSX
                if (extractor == null) {
                    for (XSSFRelation relation : XSSF_RELATIONS) {
                        if (relation.getContentType().equals(coreContentType)) {
                            extractor = new XSSFExcelExtractorDecorator(context, pkg, locale);
                            break;
                        }
                    }
                }
            }

            if (extractor == null) {
                throw new TikaException(
                        "No OOXML extractor found for content type: " + coreContentType);
            }

            extractor.getMetadataExtractor().extract(metadata);
            extractor.getXHTML(baseHandler, metadata, context);
        } catch (IllegalArgumentException e) {
            if (e.getMessage() != null &&
                    e.getMessage().startsWith("No supported documents found")) {
                throw new TikaException("TIKA-418: RuntimeException while getting content" +
                        " for thmx and xps file types", e);
            } else {
                throw new TikaException("Error creating OOXML extractor", e);
            }
        } catch (OpenXML4JException e) {
            throw new TikaException("Error creating OOXML extractor", e);
        } catch (RuntimeSAXException e) {
            throw (SAXException) e.getCause();
        }
    }

    private static final Set<String> VISIO_SUBTYPES = Set.of(
            "vnd.ms-visio.drawing",
            "vnd.ms-visio.drawing.macroenabled.12",
            "vnd.ms-visio.stencil",
            "vnd.ms-visio.stencil.macroenabled.12",
            "vnd.ms-visio.template",
            "vnd.ms-visio.template.macroenabled.12"
    );

    private static boolean isVisioType(MediaType type) {
        return type != null && VISIO_SUBTYPES.contains(type.getSubtype());
    }

    private static String getCorePartContentType(OPCPackage pkg) {
        try {
            PackageRelationshipCollection rels =
                    pkg.getRelationshipsByType(OFFICE_DOCUMENT_REL);
            if (rels.size() == 0) {
                rels = pkg.getRelationshipsByType(STRICT_OFFICE_DOCUMENT_REL);
            }
            if (rels.size() == 0) {
                return null;
            }
            PackagePart corePart = pkg.getPart(rels.getRelationship(0));
            return corePart != null ? corePart.getContentType() : null;
        } catch (Exception e) {
            return null;
        }
    }
}
