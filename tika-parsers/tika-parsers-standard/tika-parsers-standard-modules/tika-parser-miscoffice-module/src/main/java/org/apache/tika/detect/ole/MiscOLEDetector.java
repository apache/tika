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
package org.apache.tika.detect.ole;

import static org.apache.tika.mime.MediaType.application;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import org.apache.poi.poifs.filesystem.DirectoryEntry;
import org.apache.poi.poifs.filesystem.DirectoryNode;
import org.apache.poi.poifs.filesystem.Entry;
import org.apache.poi.poifs.filesystem.POIFSFileSystem;

import org.apache.tika.config.TikaComponent;
import org.apache.tika.detect.Detector;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.ParseContext;
/**
 * TODO: refactor this copy/paste from POIFSContainerDetector
 */

/**
 * A detector that works on a POIFS OLE2 document
 * to figure out exactly what the file is.
 * This should work for all OLE2 documents, whether
 * they are ones supported by POI or not.
 */
@TikaComponent
public class MiscOLEDetector implements Detector {

    /**
     * The OLE base file format
     */
    public static final MediaType OLE = application("x-tika-msoffice");


    /**
     * Hangul Word Processor (Korean)
     */
    public static final MediaType HWP = application("x-hwp-v5");

    /**
     * Base QuattroPro mime
     */
    public static final MediaType QUATTROPRO = application("x-quattro-pro");

    /**
     * Internal detection of the specific kind of OLE2 document, based on the
     * names of the top level streams within the file.
     *
     * @deprecated Use {@link #detect(Set, DirectoryEntry)} and pass the root
     * entry of the filesystem whose type is to be detected, as a
     * second argument.
     */
    @Deprecated
    protected static MediaType detect(Set<String> names) {
        return detect(names, null);
    }

    /**
     * Internal detection of the specific kind of OLE2 document, based on the
     * names of the top-level streams within the file. In some cases the
     * detection may need access to the root {@link DirectoryEntry} of that file
     * for best results. The entry can be given as a second, optional argument.
     *
     * @param names
     * @param root
     * @return
     */
    protected static MediaType detect(Set<String> names, DirectoryEntry root) {
        if (names == null || names.isEmpty()) {
            return OLE;
        } else if (names.contains("\u0005HwpSummaryInformation")) {
            // Hangul Word Processor v5+ (previous aren't OLE2-based)
            return HWP;
        } else if (names.contains("PerfectOffice_MAIN")) {
            if (names.contains("SlideShow")) {
                return MediaType.application("x-corelpresentations"); // .shw
            } else if (names.contains("PerfectOffice_OBJECTS")) {
                return new MediaType(QUATTROPRO, "version", "7-8"); // .wb?
            }
        } else if (names.contains("NativeContent_MAIN")) {
            return new MediaType(QUATTROPRO, "version", "9"); // .qpw
            // Couldn't detect a more specific type
        }
        return OLE;
    }

    private static Set<String> getTopLevelNames(DirectoryNode root) {
        Set<String> names = new HashSet<>();
        for (Entry entry : root) {
            names.add(entry.getName());
        }
        return names;
    }

    private Set<String> getTopLevelNames(TikaInputStream stream) throws IOException {
        // Force the document stream to a (possibly temporary) file
        // so we don't modify the current position of the stream.
        Path file = stream.getPath();

        if (file == null) {
            return Collections.emptySet();
        }

        try {
            POIFSFileSystem fs = new POIFSFileSystem(file.toFile(), true);

            // Optimize a possible later parsing process by keeping
            // a reference to the already opened POI file system
            stream.setOpenContainer(fs);

            return getTopLevelNames(fs.getRoot());
        } catch (IOException e) {
            // Parse error in POI, so we don't know the file type
            return Collections.emptySet();
        } catch (RuntimeException e) {
            // Another problem in POI
            return Collections.emptySet();
        }
    }

    @Override
    public MediaType detect(TikaInputStream tis, Metadata metadata, ParseContext parseContext) throws IOException {
        // Check if we have access to the document
        if (tis == null) {
            return MediaType.OCTET_STREAM;
        }

        // If this is a TikaInputStream wrapping an already
        // parsed NPOIFileSystem/DirectoryNode, just get the
        // names from the root:
        Set<String> names = null;
        Object container = tis.getOpenContainer();
        if (container instanceof POIFSFileSystem) {
            names = getTopLevelNames(((POIFSFileSystem) container).getRoot());
        } else if (container instanceof DirectoryNode) {
            names = getTopLevelNames((DirectoryNode) container);
        }

        if (names == null) {
            // Check if the document starts with the OLE header
            tis.mark(8);
            try {
                if (tis.read() != 0xd0 || tis.read() != 0xcf || tis.read() != 0x11 ||
                        tis.read() != 0xe0 || tis.read() != 0xa1 || tis.read() != 0xb1 ||
                        tis.read() != 0x1a || tis.read() != 0xe1) {
                    return MediaType.OCTET_STREAM;
                }
            } catch (IOException e) {
                return MediaType.OCTET_STREAM;
            } finally {
                tis.reset();
            }
        }

        // Look for known top level entry names to detect the document type
        if (names == null) {
            names = getTopLevelNames(tis);
        }

        // Detect based on the names (as available)
        if (tis.getOpenContainer() != null &&
                tis.getOpenContainer() instanceof POIFSFileSystem) {
            return detect(names, ((POIFSFileSystem) tis.getOpenContainer()).getRoot());
        } else {
            return detect(names, null);
        }
    }
}
