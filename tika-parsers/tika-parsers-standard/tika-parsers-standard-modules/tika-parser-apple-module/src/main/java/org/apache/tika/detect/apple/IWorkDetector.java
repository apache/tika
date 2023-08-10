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
package org.apache.tika.detect.apple;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashSet;
import java.util.Set;

import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipFile;

import org.apache.tika.detect.zip.StreamingDetectContext;
import org.apache.tika.detect.zip.ZipContainerDetector;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.iwork.IWorkPackageParser;
import org.apache.tika.parser.iwork.iwana.IWork13PackageParser;
import org.apache.tika.parser.iwork.iwana.IWork18PackageParser;

public class IWorkDetector implements ZipContainerDetector {


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
                IWorkPackageParser.IWORKDocumentType type = IWorkPackageParser.IWORKDocumentType
                        .detectType(zip.getEntry(entryName), zip);
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

    @Override
    public MediaType detect(ZipFile zipFile, TikaInputStream tis) throws IOException {
        MediaType mt = detectIWork18(zipFile);
        if (mt != null) {
            return mt;
        }
        mt = detectIWork13(zipFile);
        if (mt != null) {
            return mt;
        }
        return detectIWork(zipFile);
    }

    @Override
    public MediaType streamingDetectUpdate(ZipArchiveEntry zae, InputStream zis,
                                           StreamingDetectContext detectContext) {
        String name = zae.getName();
        EntryNames entryNames = detectContext.get(EntryNames.class);
        if (entryNames == null) {
            entryNames = new EntryNames();
            detectContext.set(EntryNames.class, entryNames);
        }
        entryNames.names.add(name);
        if (IWorkPackageParser.IWORK_CONTENT_ENTRIES.contains(name)) {
            IWorkPackageParser.IWORKDocumentType type =
                    IWorkPackageParser.IWORKDocumentType.detectType(zis);
            if (type != null) {
                return type.getType();
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
        return null;
    }

    @Override
    public MediaType streamingDetectFinal(StreamingDetectContext detectContext) {
        EntryNames entryNames = detectContext.get(EntryNames.class);
        if (entryNames == null) {
            return null;
        }
        // general iwork 13
        if (entryNames.names.contains(IWork13PackageParser.IWORK13_MAIN_ENTRY)) {
            return IWork13PackageParser.IWork13DocumentType.UNKNOWN13.getType();
        }
        //general iworks
        if (entryNames.names.contains(IWorkPackageParser.IWORK_COMMON_ENTRY)) {
            return MediaType.application("vnd.apple.iwork");
        }
        return null;
    }

    private static class EntryNames {
        Set<String> names = new HashSet<>();
    }
}
