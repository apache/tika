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
package org.apache.tika.detect;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;

import javax.xml.namespace.QName;

import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipFile;
import org.apache.poi.extractor.ExtractorFactory;
import org.apache.poi.openxml4j.exceptions.InvalidFormatException;
import org.apache.poi.openxml4j.opc.OPCPackage;
import org.apache.poi.openxml4j.opc.PackagePart;
import org.apache.poi.openxml4j.opc.PackageRelationshipCollection;
import org.apache.tika.io.IOUtils;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;

/**
 * A detector that works on a Zip document
 *  to figure out exactly what the file is
 */
public class ZipContainerDetector implements Detector {

    public MediaType detect(InputStream input, Metadata metadata)
            throws IOException {
        // Check if we have access to the document
        if (input == null) {
            return MediaType.OCTET_STREAM;
        }

        // Check if the document starts with the Zip header
        input.mark(4);
        try {
            if (input.read() != 'P' || input.read() != 'K'
                    || input.read() != 3 || input.read() != 4) {
                return MediaType.OCTET_STREAM;
            }
        } finally {
            input.reset();
        }

        // We can only detect the exact type when given a TikaInputStream
        if (!TikaInputStream.isTikaInputStream(input)) {
            return MediaType.APPLICATION_ZIP;
        }

        try {
            File file = TikaInputStream.get(input).getFile();
            ZipFile zip = new ZipFile(file);

            MediaType type = detectOpenDocument(zip);
            if (type == null) {
                type = detectOfficeOpenXML(zip, TikaInputStream.get(input));
            }
            if (type == null) {
                type = detectIWork(zip);
            }
            if (type == null && zip.getEntry("META-INF/MANIFEST.MF") != null) {
                type = MediaType.application("java-archive");
            }
            if (type == null) {
                type = MediaType.APPLICATION_ZIP;
            }
            return type;
        } catch (IOException e) {
            return MediaType.APPLICATION_ZIP;
        }
    }

    private MediaType detectOpenDocument(ZipFile zip) {
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

    private MediaType detectOfficeOpenXML(ZipFile zip, TikaInputStream stream) {
        try {
            if (zip.getEntry("_rels/.rels") != null
                    || zip.getEntry("[Content_Types].xml") != null) {
                // Use POI to open and investigate it for us
                OPCPackage pkg = OPCPackage.open(stream.getFile().getPath());
                stream.setOpenContainer(pkg);

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

                // Build the MediaType object and return
                return MediaType.parse(docType);
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

    private MediaType detectIWork(ZipFile zip) {
        if (zip.getEntry("buildVersionHistory.plist") != null) {
            // Locate the appropriate index file entry, and reads from that
            // the root element of the document. That is used to the identify
            // the correct type of the keynote container.
            MediaType type = detectIWork(zip, "index.apxl");
            if (type == null) {
                type = detectIWork(zip, "index.xml");
            }
            if (type == null) {
                type = detectIWork(zip, "presentation.apxl");
            }
            if (type == null) {
                // Not sure, fallback to the container type
                return MediaType.application("vnd.apple.iwork");
            }
            return type;
        } else {
            return null;
        }
    }

    private MediaType detectIWork(ZipFile zip, String name) {
        try {
            ZipArchiveEntry entry = zip.getEntry(name);
            if (entry == null) {
                return null;
            }

            InputStream stream = zip.getInputStream(entry);
            try {
                QName qname =
                    new XmlRootExtractor().extractRootElement(stream);
                String uri = qname.getNamespaceURI();
                String local = qname.getLocalPart();
                if ("http://developer.apple.com/namespaces/ls".equals(uri)
                        && "document".equals(local)) {
                    return MediaType.application("vnd.apple.numbers");
                } else if ("http://developer.apple.com/namespaces/sl".equals(uri)
                        && "document".equals(local)) {
                    return MediaType.application("vnd.apple.pages");
                } else if ("http://developer.apple.com/namespaces/keynote2".equals(uri)
                        && "presentation".equals(local)) {
                    return MediaType.application("vnd.apple.keynote");
                } else {
                    return null;
                }
            } finally {
                stream.close();
            }
        } catch (IOException e) {
            return null;
        }
    }

}