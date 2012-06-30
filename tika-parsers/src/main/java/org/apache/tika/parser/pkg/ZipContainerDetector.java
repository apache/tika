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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Enumeration;
import java.util.regex.Pattern;

import org.apache.commons.compress.archivers.ArchiveException;
import org.apache.commons.compress.archivers.ArchiveInputStream;
import org.apache.commons.compress.archivers.ArchiveStreamFactory;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipFile;
import org.apache.commons.compress.compressors.CompressorException;
import org.apache.commons.compress.compressors.CompressorInputStream;
import org.apache.commons.compress.compressors.CompressorStreamFactory;
import org.apache.poi.extractor.ExtractorFactory;
import org.apache.poi.openxml4j.exceptions.InvalidFormatException;
import org.apache.poi.openxml4j.opc.OPCPackage;
import org.apache.poi.openxml4j.opc.PackageAccess;
import org.apache.poi.openxml4j.opc.PackagePart;
import org.apache.poi.openxml4j.opc.PackageRelationshipCollection;
import org.apache.tika.detect.Detector;
import org.apache.tika.exception.TikaException;
import org.apache.tika.io.IOUtils;
import org.apache.tika.io.TemporaryResources;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.iwork.IWorkPackageParser;
import org.apache.tika.parser.iwork.IWorkPackageParser.IWORKDocumentType;

/**
 * A detector that works on Zip documents and other archive and compression
 * formats to figure out exactly what the file is.
 */
public class ZipContainerDetector implements Detector {
    private static final Pattern MACRO_TEMPLATE_PATTERN = Pattern.compile("macroenabledtemplate$", Pattern.CASE_INSENSITIVE);

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
            if (PackageParser.isZipArchive(type)
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
            CompressorStreamFactory factory = new CompressorStreamFactory();
            CompressorInputStream cis = factory.createCompressorInputStream(
                    new ByteArrayInputStream(prefix, 0, length));
            try {
                return CompressorParser.getMediaType(cis);
            } finally {
                IOUtils.closeQuietly(cis);
            }
        } catch (CompressorException e) {
            return MediaType.OCTET_STREAM;
        }
    }

    private static MediaType detectArchiveFormat(byte[] prefix, int length) {
        try {
            ArchiveStreamFactory factory = new ArchiveStreamFactory();
            ArchiveInputStream ais = factory.createArchiveInputStream(
                    new ByteArrayInputStream(prefix, 0, length));
            try {
                if ((ais instanceof TarArchiveInputStream)
                        && !TarArchiveInputStream.matches(prefix, length)) {
                    // ArchiveStreamFactory is too relaxed, see COMPRESS-117
                    return MediaType.OCTET_STREAM;
                } else {
                    return PackageParser.getMediaType(ais);
                }
            } finally {
                IOUtils.closeQuietly(ais);
            }
        } catch (ArchiveException e) {
            return MediaType.OCTET_STREAM;
        }
    }

    private static MediaType detectZipFormat(TikaInputStream tis) {
        try {
            ZipFile zip = new ZipFile(tis.getFile()); // TODO: hasFile()?
            try {
                MediaType type = detectOpenDocument(zip);
                if (type == null) {
                    type = detectOfficeOpenXML(zip, tis);
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
     * OpenDocument files, along with EPub files, have a mimetype
     *  entry in the root of their Zip file. This entry contains the
     *  mimetype of the overall file, stored as a single string.  
     */
    private static MediaType detectOpenDocument(ZipFile zip) {
        try {
            ZipArchiveEntry mimetype = zip.getEntry("mimetype");
            if (mimetype != null) {
                InputStream stream = zip.getInputStream(mimetype);
                try {
                    return MediaType.parse(IOUtils.toString(stream, "UTF-8"));
                } finally {
                    stream.close();
                }
            } else {
                return null;
            }
        } catch (IOException e) {
            return null;
        }
    }

    private static MediaType detectOfficeOpenXML(ZipFile zip, TikaInputStream stream) {
        try {
            if (zip.getEntry("_rels/.rels") != null
                    || zip.getEntry("[Content_Types].xml") != null) {
                // Use POI to open and investigate it for us
                OPCPackage pkg = OPCPackage.open(stream.getFile().getPath(), PackageAccess.READ);
                stream.setOpenContainer(pkg);

                // Detect based on the open OPC Package
                return detectOfficeOpenXML(pkg);
            } else {
                return null;
            }
        } catch (IOException e) {
            return null;
        } catch (RuntimeException e) {
            return null;
        } catch (InvalidFormatException e) {
            return null;
        }
    }
    /**
     * Detects the type of an OfficeOpenXML (OOXML) file from
     *  opened Package 
     */
    public static MediaType detectOfficeOpenXML(OPCPackage pkg) {
        PackageRelationshipCollection core = 
           pkg.getRelationshipsByType(ExtractorFactory.CORE_DOCUMENT_REL);
        if (core.size() != 1) {
            // Invalid OOXML Package received
            return null;
        }

        // Get the type of the core document part
        PackagePart corePart = pkg.getPart(core.getRelationship(0));
        String coreType = corePart.getContentType();

        // Turn that into the type of the overall document
        String docType = coreType.substring(0, coreType.lastIndexOf('.'));

        // The Macro Enabled formats are a little special
        if(docType.toLowerCase().endsWith("macroenabled")) {
            docType = docType.toLowerCase() + ".12";
        }

        if(docType.toLowerCase().endsWith("macroenabledtemplate")) {
            docType = MACRO_TEMPLATE_PATTERN.matcher(docType).replaceAll("macroenabled.12");
        }

        // Build the MediaType object and return
        return MediaType.parse(docType);
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

}
