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

import org.apache.commons.compress.archivers.ArchiveException;
import org.apache.commons.compress.archivers.ArchiveStreamFactory;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveInputStream;
import org.apache.commons.compress.archivers.zip.ZipFile;
import org.apache.commons.compress.compressors.CompressorException;
import org.apache.commons.compress.compressors.CompressorStreamFactory;
import org.apache.commons.io.IOUtils;
import org.apache.poi.openxml4j.exceptions.InvalidFormatException;
import org.apache.poi.openxml4j.opc.OPCPackage;
import org.apache.poi.openxml4j.opc.PackagePart;
import org.apache.poi.openxml4j.opc.PackageRelationshipCollection;
import org.apache.poi.openxml4j.opc.PackageRelationshipTypes;
import org.apache.poi.openxml4j.util.ZipEntrySource;
import org.apache.poi.openxml4j.util.ZipFileZipEntrySource;
import org.apache.poi.xslf.usermodel.XSLFRelation;
import org.apache.poi.xssf.usermodel.XSSFRelation;
import org.apache.poi.xwpf.usermodel.XWPFRelation;
import org.apache.tika.detect.Detector;
import org.apache.tika.exception.TikaException;
import org.apache.tika.io.TemporaryResources;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.iwork.IWorkPackageParser;
import org.apache.tika.parser.iwork.IWorkPackageParser.IWORKDocumentType;
import org.apache.tika.parser.iwork.iwana.IWork13PackageParser;
import org.apache.tika.utils.XMLReaderUtils;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * A detector that works on Zip documents and other archive and compression
 * formats to figure out exactly what the file is.
 */
public class ZipContainerDetector implements Detector {

    //Regrettably, some tiff files can be incorrectly identified
    //as tar files.  We need this ugly workaround to rule out TIFF.
    //If commons-compress ever chooses to take over TIFF detection
    //we can remove all of this. See TIKA-2591.
    private final static MediaType TIFF = MediaType.image("tiff");
    private final static byte[][] TIFF_SIGNATURES = new byte[3][];
    static {
        TIFF_SIGNATURES[0] = new byte[]{'M','M',0x00,0x2a};
        TIFF_SIGNATURES[1] = new byte[]{'I','I',0x2a, 0x00};
        TIFF_SIGNATURES[2] = new byte[]{'M','M', 0x00, 0x2b};
    }

    private static final Pattern MACRO_TEMPLATE_PATTERN = Pattern.compile("macroenabledtemplate$", Pattern.CASE_INSENSITIVE);

    // TODO Remove this constant once we upgrade to POI 3.12 beta 2, then use PackageRelationshipTypes 
    private static final String VISIO_DOCUMENT =
            "http://schemas.microsoft.com/visio/2010/relationships/document";
    // TODO Remove this constant once we upgrade to POI 3.12 beta 2, then use PackageRelationshipTypes 
    private static final String STRICT_CORE_DOCUMENT = 
            "http://purl.oclc.org/ooxml/officeDocument/relationships/officeDocument";

    private static final String XPS_DOCUMENT =
            "http://schemas.microsoft.com/xps/2005/06/fixedrepresentation";

    private static final MediaType TIKA_OOXML = MediaType.application("x-tika-ooxml");
    private static final MediaType DOCX =
            MediaType.application("vnd.openxmlformats-officedocument.wordprocessingml.document");
    private static final MediaType DOCM =
            MediaType.application("vnd.ms-word.document.macroEnabled.12");
    private static final MediaType DOTX =
            MediaType.application("vnd.ms-word.document.macroEnabled.12");
    private static final MediaType PPTX =
            MediaType.application("vnd.openxmlformats-officedocument.presentationml.presentation");
    private static final MediaType PPTM =
            MediaType.application("vnd.ms-powerpoint.presentation.macroEnabled.12");
    private static final MediaType POTX =
            MediaType.application("vnd.openxmlformats-officedocument.presentationml.template");
    private static final MediaType XLSX =
            MediaType.application("vnd.openxmlformats-officedocument.spreadsheetml.sheet");
    private static final MediaType XLSM =
            MediaType.application("vnd.ms-excel.sheet.macroEnabled.12");

    private static final Set<String> OOXML_HINTS = fillSet(
            "word/document.xml",
            "_rels/.rels",
            "[Content_Types].xml",
            "ppt/presentation.xml",
            "ppt/slides/slide1.xml",
            "xl/workbook.xml",
            "xl/sharedStrings.xml",
            "xl/worksheets/sheet1.xml"
    );

    static Set<String> fillSet(String ... args) {
        Set<String> tmp = new HashSet<>();
        for (String arg : args) {
            tmp.add(arg);
        }
        return Collections.unmodifiableSet(tmp);
    }
    /** Serial version UID */
    private static final long serialVersionUID = 2891763938430295453L;

    public MediaType detect(InputStream input, Metadata metadata)
            throws IOException {
        // Check if we have access to the document
        if (input == null) {
            return MediaType.OCTET_STREAM;
        }

        TemporaryResources tmp = new TemporaryResources();
        try {
            TikaInputStream tis = TikaInputStream.get(input, tmp);

            byte[] prefix = new byte[1024]; // enough for all known formats
            int length = tis.peek(prefix);

            MediaType type = detectArchiveFormat(prefix, length);

            if (type == TIFF) {
                return TIFF;
            } else if (PackageParser.isZipArchive(type)
                        && TikaInputStream.isTikaInputStream(input)) {
                return detectZipFormat(tis);
            } else if (!type.equals(MediaType.OCTET_STREAM)) {
                return type;
            } else {
                return detectCompressorFormat(prefix, length);
            }
        } finally {
            try {
                tmp.dispose();
            } catch (TikaException e) {
                // ignore
            }
        }
    }

    private static MediaType detectCompressorFormat(byte[] prefix, int length) {
        try {
            String type = CompressorStreamFactory.detect(new ByteArrayInputStream(prefix, 0, length));
            return CompressorParser.getMediaType(type);
        } catch (CompressorException e) {
            return MediaType.OCTET_STREAM;
        }
    }

    private static boolean isTiff(byte[] prefix) {
        for (byte[] sig : TIFF_SIGNATURES) {
            if(arrayStartWith(sig, prefix)) {
                return true;
            }
        }
        return false;
    }

    private static boolean arrayStartWith(byte[] needle, byte[] haystack) {
        if (haystack.length < needle.length) {
            return false;
        }
        for (int i = 0; i < needle.length; i++) {
            if (haystack[i] != needle[i]) {
                return false;
            }
        }
        return true;
    }

    private static MediaType detectArchiveFormat(byte[] prefix, int length) {
        if (isTiff(prefix)) {
            return TIFF;
        }
        try {
            String name = ArchiveStreamFactory.detect(new ByteArrayInputStream(prefix, 0, length));
            return PackageParser.getMediaType(name);
        } catch (ArchiveException e) {
            return MediaType.OCTET_STREAM;
        }
    }

    private static MediaType detectZipFormat(TikaInputStream tis) {
        try {

            //try opc first because opening a package
            //will not necessarily throw an exception for
            //truncated files.
            MediaType type = detectOPCBased(tis);
            if (type != null) {
                return type;
            }
            ZipFile zip = new ZipFile(tis.getFile()); // TODO: hasFile()?
            try {
                type = detectOpenDocument(zip);

                if (type == null) {
                    type = detectIWork13(zip);
                }
                if (type == null) {
                    type = detectIWork(zip);
                }
                if (type == null) {
                    type = detectJar(zip);
                }
                if (type == null) {
                    type = detectKmz(zip);
                }
                if (type == null) {
                    type = detectIpa(zip);
                }
                if (type != null) {
                    return type;
                }
            } finally {
                // TODO: shouldn't we record the open
                // container so it can be later
                // reused...?
                // tis.setOpenContainer(zip);
                try {
                    zip.close();
                } catch (IOException e) {
                    // ignore
                }
            }
        } catch (IOException e) {
            // ignore
        }
        // Fallback: it's still a zip file, we just don't know what kind of one
        return MediaType.APPLICATION_ZIP;
    }

    /**
     * OpenDocument files, along with EPub files and ASiC ones, have a 
     *  mimetype entry in the root of their Zip file. This entry contains
     *  the mimetype of the overall file, stored as a single string.  
     */
    private static MediaType detectOpenDocument(ZipFile zip) {
        try {
            ZipArchiveEntry mimetype = zip.getEntry("mimetype");
            if (mimetype != null) {
                try (InputStream stream = zip.getInputStream(mimetype)) {
                    return MediaType.parse(IOUtils.toString(stream, UTF_8));
                }
            } else {
                return null;
            }
        } catch (IOException e) {
            return null;
        }
    }

    private static MediaType detectOPCBased(TikaInputStream stream) {

        ZipEntrySource zipEntrySource = null;
        try {
            zipEntrySource = new ZipFileZipEntrySource(new ZipFile(stream.getFile()));
        } catch (IOException e) {
            return tryStreamingDetection(stream);
        }

        //if (zip.getEntry("_rels/.rels") != null
        //  || zip.getEntry("[Content_Types].xml") != null) {
        // Use POI to open and investigate it for us
        //Unfortunately, POI can throw a RuntimeException...so we
        //have to catch that.
        OPCPackage pkg = null;
        try {
            pkg = OPCPackage.open(zipEntrySource);
        } catch (SecurityException e) {
            closeQuietly(zipEntrySource);
            //TIKA-2571
            throw e;
        } catch (InvalidFormatException|RuntimeException e) {
            closeQuietly(zipEntrySource);
            return null;
        }

        MediaType type = null;
        try {

            // Is at an OOXML format?
            type = detectOfficeOpenXML(pkg);
            if (type == null) {
                // Is it XPS format?
                type = detectXPSOPC(pkg);
            }
            if (type == null) {
                // Is it an AutoCAD format?
                type = detectAutoCADOPC(pkg);
            }

        } catch (SecurityException e) {
            closeQuietly(zipEntrySource);
            //TIKA-2571
            throw e;
        } catch (RuntimeException e) {
            closeQuietly(zipEntrySource);
            return null;
        }
        //only set the open container if we made it here
        stream.setOpenContainer(pkg);
        // We don't know what it is, sorry
        return type;
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
    /**
     * Detects the type of an OfficeOpenXML (OOXML) file from
     *  opened Package 
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
        if(docType.toLowerCase(Locale.ROOT).endsWith("macroenabled")) {
            docType = docType.toLowerCase(Locale.ROOT) + ".12";
        }

        if(docType.toLowerCase(Locale.ROOT).endsWith("macroenabledtemplate")) {
            docType = MACRO_TEMPLATE_PATTERN.matcher(docType).replaceAll("macroenabled.12");
        }

        // Build the MediaType object and return
        return MediaType.parse(docType);
    }
    /**
     * Detects Open XML Paper Specification (XPS)
     */
    public static MediaType detectXPSOPC(OPCPackage pkg) {
        PackageRelationshipCollection xps = 
                pkg.getRelationshipsByType("http://schemas.microsoft.com/xps/2005/06/fixedrepresentation");
        if (xps.size() == 1) {
            return MediaType.application("vnd.ms-xpsdocument");
        } else {
            // Non-XPS Package received
            return null;
        }
    }
    /**
     * Detects AutoCAD formats that live in OPC packaging
     */
    private static MediaType detectAutoCADOPC(OPCPackage pkg) {
        PackageRelationshipCollection dwfxSeq = 
                pkg.getRelationshipsByType("http://schemas.autodesk.com/dwfx/2007/relationships/documentsequence");
        if (dwfxSeq.size() == 1) {
            return MediaType.parse("model/vnd.dwfx+xps");
        } else {
            // Non-AutoCAD Package received
            return null;
        }
    }

    private static MediaType detectIWork13(ZipFile zip) {
        if (zip.getEntry(IWork13PackageParser.IWORK13_COMMON_ENTRY) != null) {
            return IWork13PackageParser.IWork13DocumentType.detect(zip);
        }
        return null;
    }

    private static MediaType detectIWork(ZipFile zip) {
        if (zip.getEntry(IWorkPackageParser.IWORK_COMMON_ENTRY) != null) {
            // Locate the appropriate index file entry, and reads from that
            // the root element of the document. That is used to the identify
            // the correct type of the keynote container.
            for (String entryName : IWorkPackageParser.IWORK_CONTENT_ENTRIES) {
               IWORKDocumentType type = IWORKDocumentType.detectType(zip.getEntry(entryName), zip); 
               if (type != null) {
                  return type.getType();
               }
            }
            
            // Not sure, fallback to the container type
            return MediaType.application("vnd.apple.iwork");
        } else {
            return null;
        }
    }
    
    private static MediaType detectJar(ZipFile zip) {
       if (zip.getEntry("META-INF/MANIFEST.MF") != null) {
          // It's a Jar file, or something based on Jar
          
          // Is it an Android APK?
          if (zip.getEntry("AndroidManifest.xml") != null) {
             return MediaType.application("vnd.android.package-archive");
          }
          
          // Check for WAR and EAR
          if (zip.getEntry("WEB-INF/") != null) {
             return MediaType.application("x-tika-java-web-archive");
          }
          if (zip.getEntry("META-INF/application.xml") != null) {
             return MediaType.application("x-tika-java-enterprise-archive");
          }
          
          // Looks like a regular Jar Archive
          return MediaType.application("java-archive");
       } else {
          // Some Android APKs miss the default Manifest
          if (zip.getEntry("AndroidManifest.xml") != null) {
             return MediaType.application("vnd.android.package-archive");
          }
          
          return null;
       }
    }

    private static MediaType detectKmz(ZipFile zip) {
        boolean kmlFound = false;

        Enumeration<ZipArchiveEntry> entries = zip.getEntries();
        while (entries.hasMoreElements()) {
            ZipArchiveEntry entry = entries.nextElement();
            String name = entry.getName();
            if (!entry.isDirectory()
                    && name.indexOf('/') == -1 && name.indexOf('\\') == -1) {
                if (name.endsWith(".kml") && !kmlFound) {
                    kmlFound = true;
                } else {
                    return null;
                }
            }
        }

        if (kmlFound) {
            return MediaType.application("vnd.google-earth.kmz");
        } else {
            return null;
        }
    }

    /**
     * To be considered as an IPA file, it needs to match all of these
     */
    private static HashSet<Pattern> ipaEntryPatterns = new HashSet<Pattern>() {
        private static final long serialVersionUID = 6545295886322115362L;
        {
           add(Pattern.compile("^Payload/$"));
           add(Pattern.compile("^Payload/.*\\.app/$"));
           add(Pattern.compile("^Payload/.*\\.app/_CodeSignature/$"));
           add(Pattern.compile("^Payload/.*\\.app/_CodeSignature/CodeResources$"));
           add(Pattern.compile("^Payload/.*\\.app/Info\\.plist$"));
           add(Pattern.compile("^Payload/.*\\.app/PkgInfo$"));
    }};
    @SuppressWarnings("unchecked")
    private static MediaType detectIpa(ZipFile zip) {
        // Note - consider generalising this logic, if another format needs many regexp matching
        Set<Pattern> tmpPatterns = (Set<Pattern>)ipaEntryPatterns.clone();
        
        Enumeration<ZipArchiveEntry> entries = zip.getEntries();
        while (entries.hasMoreElements()) {
            ZipArchiveEntry entry = entries.nextElement();
            String name = entry.getName();
            
            Iterator<Pattern> ip = tmpPatterns.iterator();
            while (ip.hasNext()) {
                if (ip.next().matcher(name).matches()) {
                    ip.remove();
                }
            }
            if (tmpPatterns.isEmpty()) {
                // We've found everything we need to find
                return MediaType.application("x-itunes-ipa");
            }
        }
        
        // If we get here, not all required entries were found
        return null;
    }

    private static MediaType tryStreamingDetection(TikaInputStream stream) {
        Set<String> entryNames = new HashSet<>();
        try (InputStream is = new FileInputStream(stream.getFile())) {
            ZipArchiveInputStream zipArchiveInputStream = new ZipArchiveInputStream(is);
            ZipArchiveEntry zae = zipArchiveInputStream.getNextZipEntry();
            while (zae != null) {
                if (zae.isDirectory()) {
                    zae = zipArchiveInputStream.getNextZipEntry();
                    continue;
                }
                entryNames.add(zae.getName());
                //we could also parse _rel/.rels, but if
                // there isn't a valid content_types, then POI
                //will throw an exception...Better to backoff to PKG
                //than correctly identify a truncated
                if (zae.getName().equals("[Content_Types].xml")) {
                    MediaType mt = parseContentTypes(zipArchiveInputStream);
                    if (mt != null) {
                        return mt;
                    }
                    return TIKA_OOXML;
                }
                zae = zipArchiveInputStream.getNextZipEntry();
            }
        } catch (SecurityException e) {
            throw e;
        } catch (Exception e) {
            //swallow
        }
        int hits = 0;
        for (String s : OOXML_HINTS) {
            if (entryNames.contains(s)) {
                hits++;
            }
        }
        if (hits > 2) {
            return TIKA_OOXML;
        }
        return MediaType.APPLICATION_ZIP;
    }

    private static MediaType parseContentTypes(InputStream is) {
        ContentTypeHandler contentTypeHandler = new ContentTypeHandler();
        try {
            XMLReaderUtils.parseSAX(is, contentTypeHandler, new ParseContext());
        } catch (SecurityException e) {
            throw e;
        } catch (Exception e) {

        }
        return contentTypeHandler.mediaType;
    }


    private static class ContentTypeHandler extends DefaultHandler {
        static Map<String, MediaType> CONTENT_TYPES = new ConcurrentHashMap<>();
        static {
            CONTENT_TYPES.put(XWPFRelation.DOCUMENT.getContentType(), DOCX);
            CONTENT_TYPES.put(XWPFRelation.MACRO_DOCUMENT.getContentType(), DOCM);
            CONTENT_TYPES.put(XWPFRelation.TEMPLATE.getContentType(), DOTX);

            CONTENT_TYPES.put(XSSFRelation.WORKBOOK.getContentType(), XLSX);
            CONTENT_TYPES.put(XSSFRelation.MACROS_WORKBOOK.getContentType(), XLSM);
            CONTENT_TYPES.put(XSLFRelation.PRESENTATIONML.getContentType(), PPTX);
            CONTENT_TYPES.put(XSLFRelation.PRESENTATION_MACRO.getContentType(), PPTM);
            CONTENT_TYPES.put(XSLFRelation.PRESENTATIONML_TEMPLATE.getContentType(), POTX);
        }

        private MediaType mediaType = null;

        @Override
        public void startElement(String uri, String localName,
                                 String name, Attributes attrs) throws SAXException {
            for (int i = 0; i < attrs.getLength(); i++) {
                String attrName = attrs.getLocalName(i);
                if (attrName.equals("ContentType")) {
                    String contentType = attrs.getValue(i);
                    if (CONTENT_TYPES.containsKey(contentType)) {
                        mediaType = CONTENT_TYPES.get(contentType);
                        throw new StoppingEarlyException();
                    }

                }
            }
        }
    }

    private static class StoppingEarlyException extends SAXException {

    }

}
