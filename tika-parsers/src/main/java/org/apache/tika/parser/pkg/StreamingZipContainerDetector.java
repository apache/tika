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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.commons.compress.archivers.zip.UnsupportedZipFeatureException;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveInputStream;
import org.apache.commons.io.IOUtils;
import org.apache.poi.xdgf.usermodel.XDGFRelation;
import org.apache.poi.xslf.usermodel.XSLFRelation;
import org.apache.poi.xssf.usermodel.XSSFRelation;
import org.apache.poi.xwpf.usermodel.XWPFRelation;
import org.apache.tika.detect.Detector;
import org.apache.tika.io.BoundedInputStream;
import org.apache.tika.io.CloseShieldInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.iwork.IWorkPackageParser;
import org.apache.tika.parser.iwork.iwana.IWork13PackageParser;
import org.apache.tika.parser.iwork.iwana.IWork18PackageParser;
import org.apache.tika.sax.OfflineContentHandler;
import org.apache.tika.utils.XMLReaderUtils;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

public class StreamingZipContainerDetector extends ZipContainerDetectorBase implements Detector {

    private static final int MAX_MIME_TYPE = 1024;
    private static final int MAX_MANIFEST = 20 * 1024 * 1024;

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

    private final int markLimit;

    public StreamingZipContainerDetector(int markLimit) {
        this.markLimit = markLimit;
    }
    /**
     *
     * @param is the inputstream is wrapped in a boundedInputStream to guarantee
     *           this doesn't stream beyond {@link #markLimit}
     * @return
     */
    @Override
    public MediaType detect(InputStream is, Metadata metadata) throws IOException {
        BoundedInputStream boundedInputStream = new BoundedInputStream(markLimit, is);
        boundedInputStream.mark(markLimit);
        try {
            return _detect(boundedInputStream, metadata, false);
        } finally {
            boundedInputStream.reset();
        }
    }

    private MediaType _detect(InputStream is, Metadata metadata, boolean allowStoredEntries)
            throws IOException {
        Set<String> fileNames = new HashSet<>();
        Set<String> directoryNames = new HashSet<>();
        MediaType mt = MediaType.APPLICATION_ZIP;

        try (ZipArchiveInputStream zipArchiveInputStream =
                     new ZipArchiveInputStream(new CloseShieldInputStream(is),
                             "UTF8", false, allowStoredEntries)) {
            ZipArchiveEntry zae = zipArchiveInputStream.getNextZipEntry();
            mt = processZAE(zae, zipArchiveInputStream, directoryNames, fileNames);
        } catch (UnsupportedZipFeatureException zfe) {
            if (allowStoredEntries == false &&
                    zfe.getFeature() == UnsupportedZipFeatureException.Feature.DATA_DESCRIPTOR) {
                is.reset();
                mt = _detect(is, metadata, true);
            }
        } catch (SecurityException e) {
            throw e;
        } catch (EOFException e) {
            //truncated zip -- swallow
        } catch (IOException e) {
            //another option for a truncated zip
        }

        if (mt != MediaType.APPLICATION_ZIP) {
            return mt;
        }
        //entrynames is the union of directory names and file names
        Set<String> entryNames = new HashSet<>(fileNames);
        entryNames.addAll(directoryNames);
        mt = detectKmz(fileNames);
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

    private MediaType processZAE(ZipArchiveEntry zae, ZipArchiveInputStream zipArchiveInputStream,
                            Set<String> directoryNames, Set<String> fileNames) throws IOException {
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
                //can't rely on zae.getSize to determine if there is any
                //content here. :(
                ByteArrayOutputStream bos = new ByteArrayOutputStream();
                BoundedInputStream bis = new BoundedInputStream(MAX_MIME_TYPE, zipArchiveInputStream);
                IOUtils.copy(bis, bos);
                //do anything with an inputstream > MAX_MIME_TYPE?
                if (bos.toByteArray().length > 0)  {
                    //odt -- TODO -- check that the results are valid
                    return MediaType.parse(new String(bos.toByteArray(), UTF_8));
                }
            } else if (name.equals("META-INF/manifest.xml")) {
                //for an unknown reason, passing in the zipArchiveInputStream
                //"as is" can cause the iteration of the entries to stop early
                //without exception or warning.  So, copy the full stream, then
                //process.  TIKA-3061
                ByteArrayOutputStream bos = new ByteArrayOutputStream();
                BoundedInputStream bis = new BoundedInputStream(MAX_MANIFEST, zipArchiveInputStream);
                IOUtils.copy(bis, bos);
                //TODO: do something if the full stream hasn't been read?
                MediaType mt = detectStarOfficeX(new ByteArrayInputStream(bos.toByteArray()));
                if (mt != null) {
                    return mt;
                }
            }
            MediaType mt = IWork18PackageParser.IWork18DocumentType.detectIfPossible(zae);
            if (mt != null) {
                return mt;
            }
            mt = IWork13PackageParser.IWork13DocumentType.detectIfPossible(zae);
            if (mt != null) {
                return mt;
            }
            zae = zipArchiveInputStream.getNextZipEntry();
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

    public static MediaType parseOOXMLContentTypes(InputStream is) {
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


}
