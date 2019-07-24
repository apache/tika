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
package org.apache.tika.parser.pkg;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.InputStream;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveInputStream;
import org.apache.commons.io.IOUtils;
import org.apache.poi.xdgf.usermodel.XDGFRelation;
import org.apache.poi.xslf.usermodel.XSLFRelation;
import org.apache.poi.xssf.usermodel.XSSFRelation;
import org.apache.poi.xwpf.usermodel.XWPFRelation;
import org.apache.tika.detect.Detector;
import org.apache.tika.io.CloseShieldInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.iwork.IWorkPackageParser;
import org.apache.tika.sax.OfflineContentHandler;
import org.apache.tika.utils.XMLReaderUtils;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

public class StreamingZipContainerDetector extends ZipContainerDetectorBase implements Detector {

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

    /**
     *
     * @param is inputstream to read from. Callers must mark/reset the stream
     *           before/after this call to detect.  This call does not close the stream!
     *           Depending on the file type, this call to detect may read the entire stream.
     *           Make sure to use a {@link org.apache.tika.io.BoundedInputStream} or similar
     *           if you want to protect against reading the entire stream.
     * @return
     */
    @Override
    public MediaType detect(InputStream is, Metadata metadata) {

        Set<String> fileNames = new HashSet<>();
        Set<String> directoryNames = new HashSet<>();
        try (ZipArchiveInputStream zipArchiveInputStream =
                     new ZipArchiveInputStream(new CloseShieldInputStream(is))) {
            ZipArchiveEntry zae = zipArchiveInputStream.getNextZipEntry();
            while (zae != null) {
                String name = zae.getName();
                if (zae.isDirectory()) {
                    directoryNames.add(name);
                    zae = zipArchiveInputStream.getNextZipEntry();
                    continue;
                }
                fileNames.add(name);
                //we could also parse _rel/.rels, but if
                // there isn't a valid content_types, then POI
                //will throw an exception...Better to backoff to PKG
                //than correctly identify a truncated
                if (name.equals("[Content_Types].xml")) {
                    MediaType mt = parseOOXMLContentTypes(zipArchiveInputStream);
                    if (mt != null) {
                        return mt;
                    }
                    return TIKA_OOXML;
                } else if (IWorkPackageParser.IWORK_CONTENT_ENTRIES.contains(name)) {
                    IWorkPackageParser.IWORKDocumentType type = IWorkPackageParser.IWORKDocumentType.detectType(zipArchiveInputStream);
                    if (type != null) {
                        return type.getType();
                    }
                } else if (name.equals("mimetype")) {
                    //odt -- TODO -- bound the read and check that the results are
                    //valid
                    return MediaType.parse(IOUtils.toString(zipArchiveInputStream, UTF_8));
                }
                zae = zipArchiveInputStream.getNextZipEntry();
            }
        } catch (SecurityException e) {
            throw e;
        } catch (Exception e) {
            //swallow
        }
        //entrynames is the union of directory names and file names
        Set<String> entryNames = new HashSet<>(fileNames);
        entryNames.addAll(directoryNames);
        MediaType mt = detectKmz(fileNames);
        if (mt != null) {
            return mt;
        }
        mt = detectJar(entryNames);
        if (mt != null) {
            return mt;
        }
        mt = detectIpa(entryNames);
        if (mt != null) {
            return mt;
        }
        mt = detectIWorks(entryNames);
        if (mt != null) {
            return mt;
        }
        int hits = 0;
        for (String s : OOXML_HINTS) {
            if (entryNames.contains(s)) {
                if (++hits > 2) {
                    return TIKA_OOXML;
                }
            }
        }
        return MediaType.APPLICATION_ZIP;
    }

    private static MediaType detectIWorks(Set<String> entryNames) {
        //general iworks
        if (entryNames.contains(IWorkPackageParser.IWORK_COMMON_ENTRY)) {
            return MediaType.application("vnd.apple.iwork");
        }
        return null;
    }


    public static Set<String> parseOOXMLRels(InputStream is) {
        RelsHandler relsHandler = new RelsHandler();
        try {
            XMLReaderUtils.parseSAX(is, relsHandler, new ParseContext());
        } catch (SecurityException e) {
            throw e;
        } catch (Exception e) {

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

    public static MediaType parseOOXMLContentTypes(InputStream is) {
        ContentTypeHandler contentTypeHandler = new ContentTypeHandler();
        try {
            XMLReaderUtils.parseSAX(is,
                    new OfflineContentHandler(contentTypeHandler),
                    new ParseContext());
        } catch (SecurityException e) {
            throw e;
        } catch (Exception e) {

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

    private static class StoppingEarlyException extends SAXException {

    }
}
