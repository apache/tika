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
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
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
import org.apache.commons.io.IOUtils;
import org.apache.tika.detect.AbstractDetector;
import org.apache.tika.detect.Detector;
import org.apache.tika.exception.TikaException;
import org.apache.tika.io.TemporaryResources;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.iwork.IWorkPackageParser;
import org.apache.tika.parser.iwork.IWorkPackageParser.IWORKDocumentType;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * A detector that works on Zip documents and other archive and compression
 * formats to figure out exactly what the file is.
 */
public class ZipContainerDetector extends AbstractDetector {

    /** Serial version UID */
    private static final long serialVersionUID = 2891763938430295453L;
    
    private final Detector opcDetector;
    
    public ZipContainerDetector() {
        this.opcDetector = createDetectorProxy("org.apache.tika.parser.opc.OPCDetector");
    }

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

    private MediaType detectZipFormat(TikaInputStream tis) {
        try {
            ZipFile zip = new ZipFile(tis.getFile()); // TODO: hasFile()?
            try {
                MediaType type = detectOpenDocument(zip);
                if (type == null) {
                    type = detectOPCBased(zip, tis);
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

    private MediaType detectOPCBased(ZipFile zip, TikaInputStream stream) {
        try {
            if (zip.getEntry("_rels/.rels") != null
                    || zip.getEntry("[Content_Types].xml") != null) {
                MediaType type = this.opcDetector.detect(stream, null);
                if (type != null) return type;
                
                // We don't know what it is, sorry
                return null;
            } else {
                return null;
            }
        } catch (IOException e) {
            return null;
        } catch (RuntimeException e) {
            return null;
        }
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
}
