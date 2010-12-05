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

import static org.apache.tika.mime.MediaType.application;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import org.apache.poi.poifs.filesystem.Entry;
import org.apache.poi.poifs.filesystem.POIFSFileSystem;
import org.apache.tika.io.CloseShieldInputStream;
import org.apache.tika.io.TaggedInputStream;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;

/**
 * A detector that works on a POIFS OLE2 document
 *  to figure out exactly what the file is.
 * This should work for all OLE2 documents, whether
 *  they are ones supported by POI or not.
 */
public class POIFSContainerDetector implements Detector {

    /** The OLE base file format */
    public static final MediaType OLE = application("x-tika-msoffice");

    /** Microsoft Excel */
    public static final MediaType XLS = application("vnd.ms-excel");

    /** Microsoft Word */
    public static final MediaType DOC = application("msword");

    /** Microsoft PowerPoint */
    public static final MediaType PPT = application("vnd.ms-powerpoint");

    /** Microsoft Publisher */
    public static final MediaType PUB = application("x-mspublisher");

    /** Microsoft Visio */
    public static final MediaType VSD = application("vnd.visio");

    /** Microsoft Works */
    public static final MediaType WPS = application("vnd.ms-works");

    /** Microsoft Outlook */
    public static final MediaType MSG = application("vnd.ms-outlook");

    public MediaType detect(InputStream input, Metadata metadata)
             throws IOException {
        // Check if we have access to the document
        if (input == null) {
            return MediaType.OCTET_STREAM;
        }

        // Check if the document starts with the OLE header
        input.mark(8);
        try {
            if (input.read() != 0xd0 || input.read() != 0xcf
                    || input.read() != 0x11 || input.read() != 0xe0
                    || input.read() != 0xa1 || input.read() != 0xb1
                    || input.read() != 0x1a || input.read() != 0xe1) {
                return MediaType.OCTET_STREAM;
            }
        } finally {
            input.reset();
        }

        // We can only detect the exact type when given a TikaInputStream
        if (!TikaInputStream.isTikaInputStream(input)) {
            return OLE;
        }

        // Look for known top level entry names to detect the document type
        Set<String> names = getTopLevelNames(TikaInputStream.get(input));
        if (names.contains("Workbook")) {
            return XLS;
        } else if (names.contains("EncryptedPackage")) {
            return OLE;
        } else if (names.contains("WordDocument")) {
            return DOC;
        } else if (names.contains("Quill")) {
            return PUB;
        } else if (names.contains("PowerPoint Document")) {
            return PPT;
        } else if (names.contains("VisioDocument")) {
            return VSD;
        } else if (names.contains("CONTENTS")) {
            return WPS;
        } else if (names.contains("\u0001Ole10Native")) {
            return OLE;
        } else if (names.contains("PerfectOffice_MAIN")) {
            if (names.contains("SlideShow")) {
                return MediaType.application("x-corelpresentations"); // .shw
            } else if (names.contains("PerfectOffice_OBJECTS")) {
                return MediaType.application("x-quattro-pro"); // .wb?
            } else {
                return OLE;
            }
        } else if (names.contains("NativeContent_MAIN")) {
            return MediaType.application("x-quattro-pro"); // .qpw
        } else {
            for (String name : names) {
                if (name.startsWith("__substg1.0_")) {
                    return MSG;
                }
            }
            return OLE;
        }
    }

    private static Set<String> getTopLevelNames(TikaInputStream stream)
            throws IOException {
        // Force the document stream to a (possibly temporary) file
        // so we don't modify the current position of the stream
        File file = stream.getFile();

        // Use a tagged stream to distinguish between real I/O problems
        // and parse errors thrown as IOExceptions by POI.
        TaggedInputStream tagged = new TaggedInputStream(
                new BufferedInputStream(new FileInputStream(file)));
        try {
            // POIFSFileSystem might try close the stream
            POIFSFileSystem fs =
                new POIFSFileSystem(new CloseShieldInputStream(tagged));

            // Optimize a possible later parsing process by keeping
            // a reference to the already opened POI file system
            stream.setOpenContainer(fs);

            Set<String> names = new HashSet<String>();
            for (Entry entry : fs.getRoot()) {
                names.add(entry.getName());
            }
            return names;
        } catch (IOException e) {
            // Was this a real I/O problem?
            tagged.throwIfCauseOf(e);
            // Parse error in POI, so we don't know the file type
            return Collections.emptySet();
        } catch (RuntimeException e) {
            // Another problem in POI
            return Collections.emptySet();
        } finally {
            tagged.close();
        }
    }

}
