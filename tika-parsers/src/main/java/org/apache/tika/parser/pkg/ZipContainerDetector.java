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
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

import org.apache.commons.compress.archivers.ArchiveException;
import org.apache.commons.compress.archivers.ArchiveStreamFactory;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
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
import org.apache.tika.config.Field;
import org.apache.tika.detect.Detector;
import org.apache.tika.io.BoundedInputStream;
import org.apache.tika.io.LookaheadInputStream;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.iwork.IWorkPackageParser;
import org.apache.tika.parser.iwork.IWorkPackageParser.IWORKDocumentType;
import org.apache.tika.parser.iwork.iwana.IWork13PackageParser;
import org.apache.tika.parser.iwork.iwana.IWork18PackageParser;

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

    private static final String OPEN_XPS_DOCUMENT =
            "http://schemas.openxps.org/oxps/v1.0/fixedrepresentation";

    private static final String STAR_OFFICE_6_WRITER = "application/vnd.sun.xml.writer";
    /** Serial version UID */
    private static final long serialVersionUID = 2891763938430295453L;

    //this has to be > 100,000 to handle some of the iworks files
    //in our unit tests
    int markLimit = 16 * 1024 * 1024;

    private StreamingZipContainerDetector streamingZipContainerDetector
            = new StreamingZipContainerDetector(markLimit);

    @Override
    public MediaType detect(InputStream input, Metadata metadata)
            throws IOException {
        // Check if we have access to the document
        if (input == null) {
            return MediaType.OCTET_STREAM;
        }

        byte[] prefix = new byte[1024]; // enough for all known archive formats
        input.mark(1024);
        int length = -1;
        try {
            length = IOUtils.read(input, prefix);
        } finally {
            input.reset();
        }

        MediaType type = detectArchiveFormat(prefix, length);

        if (type == TIFF) {
            return TIFF;
        } else if (PackageParser.isZipArchive(type)) {

            if (TikaInputStream.isTikaInputStream(input)) {
                TikaInputStream tis = TikaInputStream.cast(input);
                if (markLimit < 0) {
                    tis.getFile();
                }
                if (tis.hasFile()) {
                    return detectZipFormatOnFile(tis);
                }
            }
            return streamingZipContainerDetector.detect(input, metadata);
        } else if (!type.equals(MediaType.OCTET_STREAM)) {
            return type;
        } else {
            return detectCompressorFormat(prefix, length);
        }
    }

    /**
     * If this is less than 0, the file will be spooled to disk,
     * and detection will run on the full file.
     * If this is greater than 0, the {@link StreamingZipContainerDetector}
     * will be called only up to the markLimit.
     *
     * @param markLimit mark limit for streaming detection
     */
    @Field
    public void setMarkLimit(int markLimit) {
        this.markLimit = markLimit;
        this.streamingZipContainerDetector = new StreamingZipContainerDetector(markLimit);
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

    /**
     * This will call TikaInputStream's getFile(). If there are no exceptions,
     * it will place the ZipFile in TikaInputStream's openContainer and leave it
     * open.
     * @param tis
     * @return
     */
    private static MediaType detectZipFormatOnFile(TikaInputStream tis) {
        try {

            ZipFile zip = new ZipFile(tis.getFile()); // TODO: hasFile()?
            MediaType type = null;
            try {
                type = detectOpenDocument(zip);

                if (type == null) {
                    type = detectIWork18(zip);
                }
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
                if (type == null) {
                    type = detectStarOfficeX(zip);
                }
                if (type != null) {
                    return type;
                }
            } finally {
                tis.setOpenContainer(zip);
            }
            //finally, test for opc based
            //if it is not an opc based file, poi throws an exception
            //and we close the zip
            //if it is opc based, we put the pkg in TikaInputStream's open container
            if (zip.getEntry("_rels/.rels") != null
                    || zip.getEntry("[Content_Types].xml") != null) {
                type = detectOPCBased(zip, tis);
                if (type != null) {
                    return type;
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
            if (mimetype != null && mimetype.getSize() > 0) {
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

    //If this is not an OPCBased file, POI throws an exception and we close the zipFile.
    private static MediaType detectOPCBased(ZipFile zipFile, TikaInputStream stream) {
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
            IOUtils.closeQuietly(zipFile);
            //TIKA-2571
            throw e;
        } catch (InvalidFormatException|RuntimeException e) {
            closeQuietly(zipEntrySource);
            IOUtils.closeQuietly(zipFile);
            return null;
        }
        //only set the open container if we made it here
        stream.setOpenContainer(pkg);
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
        if (core.size() == 0) {
            core = pkg.getRelationshipsByType(XPS_DOCUMENT);
            if (core.size() == 1) {
                return MediaType.application("vnd.ms-xpsdocument");
            }
            core = pkg.getRelationshipsByType(OPEN_XPS_DOCUMENT);
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

    private static MediaType detectIWork18(ZipFile zip) {
        return IWork18PackageParser.IWork18DocumentType.detect(zip);
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


    private static MediaType detectStarOfficeX(ZipFile zip) throws IOException {
        ZipArchiveEntry zae = zip.getEntry("META-INF/manifest.xml");
        if (zae == null) {
            return null;
        }
        try (InputStream is = zip.getInputStream(zae)) {
            return ZipContainerDetectorBase.detectStarOfficeX(is);
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


}
