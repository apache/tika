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
import java.io.InputStream;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import org.apache.poi.poifs.filesystem.DirectoryEntry;
import org.apache.poi.poifs.filesystem.DirectoryNode;
import org.apache.poi.poifs.filesystem.Entry;
import org.apache.poi.poifs.filesystem.POIFSFileSystem;

import org.apache.tika.config.Field;
import org.apache.tika.detect.Detector;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;
/**
 * TODO: refactor this copy/paste from POIFSContainerDetector
 */

/**
 * A detector that works on a POIFS OLE2 document
 * to figure out exactly what the file is.
 * This should work for all OLE2 documents, whether
 * they are ones supported by POI or not.
 */
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


    @Field
    private int markLimit = 16 * 1024 * 1024;

    /**
     * Internal detection of the specific kind of OLE2 document, based on the
     * names of the top level streams within the file.
     *
     * @deprecated Use {@link #detect(Set, DirectoryEntry)} and pass the root
     * entry of the filesystem whose type is to be detected, as a
     * second argument.
     */
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
        if (names == null || names.size() == 0) {
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

    /**
     * If a TikaInputStream is passed in to {@link #detect(InputStream, Metadata)},
     * and there is not an underlying file, this detector will spool up to {@link #markLimit}
     * to disk.  If the stream was read in entirety (e.g. the spooled file is not truncated),
     * this detector will open the file with POI and perform detection.
     * If the spooled file is truncated, the detector will return {@link #OLE} (or
     * {@link MediaType#OCTET_STREAM} if there's no OLE header).
     * <p>
     * As of Tika 1.21, this detector respects the legacy behavior of not performing detection
     * on a non-TikaInputStream.
     *
     * @param markLimit
     */
    public void setMarkLimit(int markLimit) {
        this.markLimit = markLimit;
    }

    private Set<String> getTopLevelNames(TikaInputStream stream) throws IOException {
        // Force the document stream to a (possibly temporary) file
        // so we don't modify the current position of the stream.
        //If the markLimit is < 0, this will spool the entire file
        //to disk if there is not an underlying file.
        Path file = stream.getPath(markLimit);

        //if the stream was longer than markLimit, don't detect
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

    public MediaType detect(InputStream input, Metadata metadata) throws IOException {
        // Check if we have access to the document
        if (input == null) {
            return MediaType.OCTET_STREAM;
        }

        // If this is a TikaInputStream wrapping an already
        // parsed NPOIFileSystem/DirectoryNode, just get the
        // names from the root:
        TikaInputStream tis = TikaInputStream.cast(input);
        Set<String> names = null;
        if (tis != null) {
            Object container = tis.getOpenContainer();
            if (container instanceof POIFSFileSystem) {
                names = getTopLevelNames(((POIFSFileSystem) container).getRoot());
            } else if (container instanceof DirectoryNode) {
                names = getTopLevelNames((DirectoryNode) container);
            }
        }

        if (names == null) {
            // Check if the document starts with the OLE header
            input.mark(8);
            try {
                if (input.read() != 0xd0 || input.read() != 0xcf || input.read() != 0x11 ||
                        input.read() != 0xe0 || input.read() != 0xa1 || input.read() != 0xb1 ||
                        input.read() != 0x1a || input.read() != 0xe1) {
                    return MediaType.OCTET_STREAM;
                }
            } catch (IOException e) {
                return MediaType.OCTET_STREAM;
            } finally {
                input.reset();
            }
        }

        // We can only detect the exact type when given a TikaInputStream
        if (names == null && tis != null) {
            // Look for known top level entry names to detect the document type
            names = getTopLevelNames(tis);
        }

        // Detect based on the names (as available)
        if (tis != null && tis.getOpenContainer() != null &&
                tis.getOpenContainer() instanceof POIFSFileSystem) {
            return detect(names, ((POIFSFileSystem) tis.getOpenContainer()).getRoot());
        } else {
            return detect(names, null);
        }
    }
}
