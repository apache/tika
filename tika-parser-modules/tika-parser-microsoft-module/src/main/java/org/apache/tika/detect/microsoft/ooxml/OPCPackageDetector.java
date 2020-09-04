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
package org.apache.tika.detect.microsoft.ooxml;

import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipFile;
import org.apache.poi.openxml4j.exceptions.InvalidFormatException;
import org.apache.poi.openxml4j.opc.OPCPackage;
import org.apache.poi.openxml4j.opc.PackagePart;
import org.apache.poi.openxml4j.opc.PackageRelationshipCollection;
import org.apache.poi.openxml4j.opc.PackageRelationshipTypes;
import org.apache.poi.openxml4j.util.ZipEntrySource;
import org.apache.poi.openxml4j.util.ZipFileZipEntrySource;
import org.apache.poi.xdgf.usermodel.XDGFRelation;
import org.apache.poi.xslf.usermodel.XSLFRelation;
import org.apache.poi.xssf.usermodel.XSSFRelation;
import org.apache.poi.xwpf.usermodel.XWPFRelation;
import org.apache.tika.detect.zip.StreamingDetectContext;
import org.apache.tika.detect.zip.ZipContainerDetector;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.OfflineContentHandler;
import org.apache.tika.sax.StoppingEarlyException;
import org.apache.tika.utils.XMLReaderUtils;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

public class OPCPackageDetector implements ZipContainerDetector {


    private static final Pattern MACRO_TEMPLATE_PATTERN = Pattern.compile("macroenabledtemplate$", Pattern.CASE_INSENSITIVE);

    static final MediaType TIKA_OOXML = MediaType.application("x-tika-ooxml");
    static final MediaType DOCX =
            MediaType.application("vnd.openxmlformats-officedocument.wordprocessingml.document");
    static final MediaType DOCM =
            MediaType.application("vnd.ms-word.document.macroEnabled.12");
    static final MediaType DOTM =
            MediaType.application("vnd.ms-word.template.macroenabled.12");
    static final MediaType DOTX =
            MediaType.application("vnd.openxmlformats-officedocument.wordprocessingml.template");
    static final MediaType PPTX =
            MediaType.application("vnd.openxmlformats-officedocument.presentationml.presentation");
    static final MediaType PPSM =
            MediaType.application("vnd.ms-powerpoint.slideshow.macroEnabled.12");
    static final MediaType PPSX =
            MediaType.application("vnd.openxmlformats-officedocument.presentationml.slideshow");
    static final MediaType PPTM =
            MediaType.application("vnd.ms-powerpoint.presentation.macroEnabled.12");
    static final MediaType POTM =
            MediaType.application("vnd.ms-powerpoint.template.macroenabled.12");
    static final MediaType POTX =
            MediaType.application("vnd.openxmlformats-officedocument.presentationml.template");
    static final MediaType THMX =
            MediaType.application("vnd.openxmlformats-officedocument");
    static final MediaType XLSB =
            MediaType.application("vnd.ms-excel.sheet.binary.macroenabled.12");
    static final MediaType XLSX =
            MediaType.application("vnd.openxmlformats-officedocument.spreadsheetml.sheet");
    static final MediaType XLSM =
            MediaType.application("vnd.ms-excel.sheet.macroEnabled.12");
    static final MediaType XLTM =
            MediaType.application("vnd.ms-excel.template.macroenabled.12");
    static final MediaType XLTX =
            MediaType.application("vnd.openxmlformats-officedocument.spreadsheetml.template");
    static final MediaType XLAM =
            MediaType.application("vnd.ms-excel.addin.macroEnabled.12");
    static final MediaType XPS =
            MediaType.application("vnd.ms-xpsdocument");


    static final Set<String> OOXML_HINTS = fillSet(
            "word/document.xml",
            "_rels/.rels",
            "[Content_Types].xml",
            "ppt/presentation.xml",
            "ppt/slides/slide1.xml",
            "xl/workbook.xml",
            "xl/sharedStrings.xml",
            "xl/worksheets/sheet1.xml"
    );

    private static Set<String> fillSet(String ... args) {
        Set<String> tmp = new HashSet<>();
        for (String arg : args) {
            tmp.add(arg);
        }
        return Collections.unmodifiableSet(tmp);
    }

    static Map<String, MediaType> OOXML_CONTENT_TYPES = new ConcurrentHashMap<>();

    static {
        OOXML_CONTENT_TYPES.put(XWPFRelation.DOCUMENT.getContentType(), DOCX);
        OOXML_CONTENT_TYPES.put(XWPFRelation.MACRO_DOCUMENT.getContentType(), DOCM);
        OOXML_CONTENT_TYPES.put(XWPFRelation.MACRO_TEMPLATE_DOCUMENT.getContentType(), DOTM);
        OOXML_CONTENT_TYPES.put(XWPFRelation.TEMPLATE.getContentType(), DOTX);

        OOXML_CONTENT_TYPES.put(XSSFRelation.WORKBOOK.getContentType(), XLSX);
        OOXML_CONTENT_TYPES.put(XSSFRelation.MACROS_WORKBOOK.getContentType(), XLSM);
        OOXML_CONTENT_TYPES.put(XSSFRelation.XLSB_BINARY_WORKBOOK.getContentType(), XLSB);
        OOXML_CONTENT_TYPES.put(XSSFRelation.TEMPLATE_WORKBOOK.getContentType(), XLTX);
        OOXML_CONTENT_TYPES.put(XSSFRelation.MACRO_TEMPLATE_WORKBOOK.getContentType(), XLTM);
        OOXML_CONTENT_TYPES.put(XSSFRelation.MACRO_ADDIN_WORKBOOK.getContentType(), XLAM);

        OOXML_CONTENT_TYPES.put(XSLFRelation.MAIN.getContentType(), PPTX);
        OOXML_CONTENT_TYPES.put(XSLFRelation.MACRO.getContentType(), PPSM);
        OOXML_CONTENT_TYPES.put(XSLFRelation.MACRO_TEMPLATE.getContentType(), POTM);
        OOXML_CONTENT_TYPES.put(XSLFRelation.PRESENTATIONML_TEMPLATE.getContentType(), PPTM);
        OOXML_CONTENT_TYPES.put(XSLFRelation.PRESENTATIONML.getContentType(), PPSX);
        OOXML_CONTENT_TYPES.put(XSLFRelation.PRESENTATION_MACRO.getContentType(), PPTM);
        OOXML_CONTENT_TYPES.put(XSLFRelation.PRESENTATIONML_TEMPLATE.getContentType(), POTX);
        OOXML_CONTENT_TYPES.put(XSLFRelation.THEME_MANAGER.getContentType(), THMX);

        OOXML_CONTENT_TYPES.put("application/vnd.ms-visio.drawing.macroEnabled.main+xml",
                MediaType.application("vnd.ms-visio.drawing.macroEnabled.12"));
        OOXML_CONTENT_TYPES.put(XDGFRelation.DOCUMENT.getContentType(), MediaType.application("vnd.ms-visio.drawing"));
        OOXML_CONTENT_TYPES.put("application/vnd.ms-visio.stencil.macroEnabled.main+xml",
                MediaType.application("vnd.ms-visio.stencil.macroenabled.12"));
        OOXML_CONTENT_TYPES.put("application/vnd.ms-visio.stencil.main+xml",
                MediaType.application("vnd.ms-visio.stencil"));
        OOXML_CONTENT_TYPES.put("application/vnd.ms-visio.template.macroEnabled.main+xml",
                MediaType.application("vnd.ms-visio.template.macroenabled.12"));
        OOXML_CONTENT_TYPES.put("application/vnd.ms-visio.template.main+xml",
                MediaType.application("vnd.ms-visio.template"));

        OOXML_CONTENT_TYPES.put("application/vnd.ms-package.xps-fixeddocumentsequence+xml", XPS);

    }


    // TODO Remove this constant once we upgrade to POI 3.12 beta 2, then use PackageRelationshipTypes
    private static final String VISIO_DOCUMENT =
            "http://schemas.microsoft.com/visio/2010/relationships/document";
    // TODO Remove this constant once we upgrade to POI 3.12 beta 2, then use PackageRelationshipTypes
    private static final String STRICT_CORE_DOCUMENT =
            "http://purl.oclc.org/ooxml/officeDocument/relationships/officeDocument";

    private static final String XPS_DOCUMENT =
            "http://schemas.microsoft.com/xps/2005/06/fixedrepresentation";

    private static final String STAR_OFFICE_6_WRITER = "application/vnd.sun.xml.writer";


    @Override
    public MediaType detect(ZipFile zipFile, TikaInputStream stream) throws IOException {
        //as of 4.x, POI throws an exception for non-POI OPC file types
        //unless we change POI, we can't rely on POI for non-POI files
        ZipEntrySource zipEntrySource = new ZipFileZipEntrySource(zipFile);

        // Use POI to open and investigate it for us
        //Unfortunately, POI can throw a RuntimeException...so we
        //have to catch that.
        OPCPackage pkg = null;
        MediaType type = null;
        try {
            pkg = OPCPackage.open(zipEntrySource);
            type = detectOfficeOpenXML(pkg);

        } catch (SecurityException e) {
            closeQuietly(zipEntrySource);
            closeQuietly(zipFile);
            //TIKA-2571
            throw e;
        } catch (InvalidFormatException | RuntimeException e) {
            closeQuietly(zipEntrySource);
            closeQuietly(zipFile);
            return null;
        }
        //only set the open container if we made it here
        stream.setOpenContainer(pkg);
        return type;
    }


    /**
     * Detects the type of an OfficeOpenXML (OOXML) file from
     * opened Package
     */
    public static MediaType detectOfficeOpenXML(OPCPackage pkg) {
        // Check for the normal Office core document
        PackageRelationshipCollection core =
                pkg.getRelationshipsByType(PackageRelationshipTypes.CORE_DOCUMENT);
        // Otherwise check for some other Office core document types
        if (core.size() == 0) {
            core = pkg.getRelationshipsByType(STRICT_CORE_DOCUMENT);
        }
        if (core.size() == 0) {
            core = pkg.getRelationshipsByType(VISIO_DOCUMENT);
        }
        if (core.size() == 0) {
            core = pkg.getRelationshipsByType(XPS_DOCUMENT);
            if (core.size() == 1) {
                return MediaType.application("vnd.ms-xpsdocument");
            }
        }

        if (core.size() == 0) {
            core = pkg.getRelationshipsByType("http://schemas.autodesk.com/dwfx/2007/relationships/documentsequence");
            if (core.size() == 1) {
                return MediaType.parse("model/vnd.dwfx+xps");
            }
        }
        // If we didn't find a single core document of any type, skip detection
        if (core.size() != 1) {
            // Invalid OOXML Package received
            return null;
        }

        // Get the type of the core document part
        PackagePart corePart = pkg.getPart(core.getRelationship(0));
        String coreType = corePart.getContentType();

        if (coreType.contains(".xps")) {
            return MediaType.application("vnd.ms-package.xps");
        }
        // Turn that into the type of the overall document
        String docType = coreType.substring(0, coreType.lastIndexOf('.'));

        // The Macro Enabled formats are a little special
        if (docType.toLowerCase(Locale.ROOT).endsWith("macroenabled")) {
            docType = docType.toLowerCase(Locale.ROOT) + ".12";
        }

        if (docType.toLowerCase(Locale.ROOT).endsWith("macroenabledtemplate")) {
            docType = MACRO_TEMPLATE_PATTERN.matcher(docType).replaceAll("macroenabled.12");
        }

        // Build the MediaType object and return
        return MediaType.parse(docType);
    }

    private static void closeQuietly(ZipFile zipFile) {
        if (zipFile == null) {
            return;
        }
        try {
            zipFile.close();
        } catch (IOException e) {

        }
    }

    private static void closeQuietly(ZipEntrySource zipEntrySource) {
        if (zipEntrySource == null) {
            return;
        }
        try {
            zipEntrySource.close();
        } catch (IOException e) {
            //swallow
        }
    }

    @Override
    public MediaType streamingDetectUpdate(ZipArchiveEntry zae, InputStream zis, StreamingDetectContext detectContext) {
        String name = zae.getName();
        if (name.equals("[Content_Types].xml")) {
            MediaType mt = parseOOXMLContentTypes(zis);
            if (mt != null) {
                return mt;
            }
            return TIKA_OOXML;
        }

        if (OOXML_HINTS.contains(name)) {
            OOXMLHintCounter cnt = detectContext.get(OOXMLHintCounter.class);
            if (cnt == null) {
                cnt = new OOXMLHintCounter();
                detectContext.set(OOXMLHintCounter.class, cnt);
            }
            cnt.increment();
            if (cnt.getCount() > 2) {
                return TIKA_OOXML;
            }
        }
        return null;
    }

    @Override
    public MediaType streamingDetectFinal(StreamingDetectContext detectContext) {
        return null;
    }

    public static Set<String> parseOOXMLRels(InputStream is) {
        RelsHandler relsHandler = new RelsHandler();
        try {
            XMLReaderUtils.parseSAX(is, relsHandler, new ParseContext());
        } catch (SecurityException e) {
            throw e;
        } catch (Exception e) {
            //swallow
        }
        return relsHandler.rels;
    }

    private static class RelsHandler extends DefaultHandler {
        Set<String> rels = new HashSet<>();
        private MediaType mediaType = null;
        @Override
        public void startElement(String uri, String localName,
                                 String name, Attributes attrs) throws SAXException {
            for (int i = 0; i < attrs.getLength(); i++) {
                String attrName = attrs.getLocalName(i);
                if (attrName.equals("Type")) {
                    String contentType = attrs.getValue(i);
                    rels.add(contentType);
                    if (OOXML_CONTENT_TYPES.containsKey(contentType)) {
                        mediaType = OOXML_CONTENT_TYPES.get(contentType);
                    }
                }
            }
        }
    }

    private static MediaType parseOOXMLContentTypes(InputStream is) {
        ContentTypeHandler contentTypeHandler = new ContentTypeHandler();
        try {
            XMLReaderUtils.parseSAX(is,
                    new OfflineContentHandler(contentTypeHandler),
                    new ParseContext());
        } catch (SecurityException e) {
            throw e;
        } catch (Exception e) {
            //swallow
        }
        return contentTypeHandler.mediaType;
    }


    private static class ContentTypeHandler extends DefaultHandler {

        private MediaType mediaType = null;

        @Override
        public void startElement(String uri, String localName,
                                 String name, Attributes attrs) throws SAXException {
            for (int i = 0; i < attrs.getLength(); i++) {
                String attrName = attrs.getLocalName(i);
                if (attrName.equals("ContentType")) {
                    String contentType = attrs.getValue(i);
                    if (OOXML_CONTENT_TYPES.containsKey(contentType)) {
                        mediaType = OOXML_CONTENT_TYPES.get(contentType);
                        throw new StoppingEarlyException();
                    }

                }
            }
        }
    }

    private static class OOXMLHintCounter {
        private int cnt = 0;

        private void increment() {
            cnt++;
        }

        private int getCount() {
            return cnt;
        }
    }
}
