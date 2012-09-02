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
package org.apache.tika.parser.microsoft;

import static org.apache.tika.mime.MediaType.application;

import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.FileChannel;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

import org.apache.poi.poifs.filesystem.DirectoryEntry;
import org.apache.poi.poifs.filesystem.DirectoryNode;
import org.apache.poi.poifs.filesystem.DocumentInputStream;
import org.apache.poi.poifs.filesystem.DocumentNode;
import org.apache.poi.poifs.filesystem.Entry;
import org.apache.poi.poifs.filesystem.NPOIFSFileSystem;
import org.apache.tika.detect.Detector;
import org.apache.tika.io.IOUtils;
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

    /** Serial version UID */
    private static final long serialVersionUID = -3028021741663605293L;
    
    /** An ASCII String "StarImpress" */
    private static final byte [] STAR_IMPRESS = new byte [] {
        0x53, 0x74, 0x61, 0x72, 0x49, 0x6d, 0x70, 0x72, 0x65, 0x73, 0x73
    };
    
    /** An ASCII String "StarDraw" */
    private static final byte [] STAR_DRAW = new byte [] {
        0x53, 0x74, 0x61, 0x72, 0x44, 0x72, 0x61, 0x77
    };
    
    /** An ASCII String "Quill96" for Works Files */
    private static final byte [] WORKS_QUILL96 = new byte[] {
        0x51, 0x75, 0x69, 0x6c, 0x6c, 0x39, 0x36
    };

    /** The OLE base file format */
    public static final MediaType OLE = application("x-tika-msoffice");
    
    /** The protected OOXML base file format */
    public static final MediaType OOXML_PROTECTED = application("x-tika-ooxml-protected");
    
    /** General embedded document type within an OLE2 container */
    public static final MediaType GENERAL_EMBEDDED = application("x-tika-msoffice-embedded");
    
    /** An OLE10 Native embedded document within another OLE2 document */
    public static final MediaType OLE10_NATIVE =
            new MediaType(GENERAL_EMBEDDED, "format", "ole10_native");
    
    /** Some other kind of embedded document, in a CompObj container within another OLE2 document */
    public static final MediaType COMP_OBJ =
            new MediaType(GENERAL_EMBEDDED, "format", "comp_obj");

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
    
    /** Microsoft Works Spreadsheet 7.0 */
    public static final MediaType XLR = application("x-tika-msworks-spreadsheet");

    /** Microsoft Outlook */
    public static final MediaType MSG = application("vnd.ms-outlook");
    
    /** Microsoft Project */
    public static final MediaType MPP = application("vnd.ms-project");
    
    /** StarOffice Calc */
    public static final MediaType SDC = application("vnd.stardivision.calc");
    
    /** StarOffice Draw */
    public static final MediaType SDA = application("vnd.stardivision.draw");
    
    /** StarOffice Impress */
    public static final MediaType SDD = application("vnd.stardivision.impress");
    
    /** StarOffice Writer */
    public static final MediaType SDW = application("vnd.stardivision.writer");

    /** Regexp for matching the MPP Project Data stream */
    private static final Pattern mppDataMatch = Pattern.compile("\\s\\s\\s\\d+");

    public MediaType detect(InputStream input, Metadata metadata)
             throws IOException {
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
            if (container instanceof NPOIFSFileSystem) {
                names = getTopLevelNames(((NPOIFSFileSystem) container).getRoot());
            } else if (container instanceof DirectoryNode) {
                names = getTopLevelNames((DirectoryNode) container);
            }
        }

        if (names == null) {
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
        }

        // We can only detect the exact type when given a TikaInputStream
        if (names == null && tis != null) {
            // Look for known top level entry names to detect the document type
            names = getTopLevelNames(tis);
        }
        
        // Detect based on the names (as available)
        if (tis != null && 
            tis.getOpenContainer() != null && 
            tis.getOpenContainer() instanceof NPOIFSFileSystem) {
            return detect(names, ((NPOIFSFileSystem)tis.getOpenContainer()).getRoot());
        } else {
            return detect(names, null);
        }
    }

    /**
     * Internal detection of the specific kind of OLE2 document, based on the
     * names of the top level streams within the file.
     * 
     * @deprecated Use {@link #detect(Set, DirectoryEntry)} and pass the root
     *             entry of the filesystem whose type is to be detected, as a
     *             second argument.
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
        if (names != null) {
            if (names.contains("StarCalcDocument")) {
                // Star Office Calc
                return SDC;
            } else if (names.contains("StarWriterDocument")) {
                return SDW;
            } else if (names.contains("StarDrawDocument3")) {
                if (root == null) {
                    /*
                     * This is either StarOfficeDraw or StarOfficeImpress, we have
                     * to consult the CompObj to distinguish them, if this method is
                     * called in "legacy mode", without the root, just return
                     * x-tika-msoffice. The one-argument method is only for backward
                     * compatibility, if someone calls old API he/she can get the
                     * old result.
                     */
                    return OLE;
                } else {
                    return processCompObjFormatType(root);
                }
            } else if (names.contains("WksSSWorkBook")) {
                // This check has to be before names.contains("Workbook")
                // Works 7.0 spreadsheet files contain both
                // we want to avoid classifying this as Excel
                return XLR; 
            } else if (names.contains("Workbook")) {
                return XLS;
            } else if (names.contains("EncryptedPackage") && 
                    names.contains("EncryptionInfo") &&
                    names.contains("\u0006DataSpaces")) {
                // This is a protected OOXML document, which is an OLE2 file
                //  with an Encrypted Stream which holds the OOXML data
                // Without decrypting the stream, we can't tell what kind of
                //  OOXML file we have. Return a general OOXML Protected type,
                //  and hope the name based detection can guess the rest! 
                return OOXML_PROTECTED;
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
            } else if (names.contains("\u0001Ole10Native")) {
                return OLE10_NATIVE;
            } else if (names.contains("MatOST")) {
                // this occurs on older Works Word Processor files (versions 3.0 and 4.0)
                return WPS;
            } else if (names.contains("CONTENTS") && names.contains("SPELLING")) {
                // Newer Works files
                return WPS;
            } else if (names.contains("Contents") && names.contains("\u0003ObjInfo")) {
                return COMP_OBJ;
            } else if (names.contains("CONTENTS") && names.contains("\u0001CompObj")) {
               // CompObj is a general kind of OLE2 embedding, but this may be an old Works file
               // If we have the Directory, check
               if (root != null) {
                  MediaType type = processCompObjFormatType(root);
                  if (type == WPS) {
                     return WPS;
                  } else {
                     // Assume it's a general CompObj embedded resource
                     return COMP_OBJ;
                  }
               } else {
                  // Assume it's a general CompObj embedded resource
                  return COMP_OBJ;
               }
            } else if (names.contains("CONTENTS")) {
               // CONTENTS without SPELLING nor CompObj normally means some sort
               //  of embedded non-office file inside an OLE2 document
               // This is most commonly triggered on nested directories
               return OLE;
            } else if (names.contains("\u0001CompObj") &&
                  (names.contains("Props") || names.contains("Props9") || names.contains("Props12"))) {
               // Could be Project, look for common name patterns
               for (String name : names) {
                  if (mppDataMatch.matcher(name).matches()) {
                     return MPP;
                  }
               }
            } else if (names.contains("PerfectOffice_MAIN")) {
                if (names.contains("SlideShow")) {
                    return MediaType.application("x-corelpresentations"); // .shw
                } else if (names.contains("PerfectOffice_OBJECTS")) {
                    return MediaType.application("x-quattro-pro"); // .wb?
                }
            } else if (names.contains("NativeContent_MAIN")) {
                return MediaType.application("x-quattro-pro"); // .qpw
            } else {
                for (String name : names) {
                    if (name.startsWith("__substg1.0_")) {
                        return MSG;
                    }
                }
            }
        }

        // Couldn't detect a more specific type
        return OLE;
    }

    /**
     * Is this one of the kinds of formats which uses CompObj to
     *  store all of their data, eg Star Draw, Star Impress or
     *  (older) Works?
     * If not, it's likely an embedded resource
     */
    private static MediaType processCompObjFormatType(DirectoryEntry root) {
        try {
            Entry e = root.getEntry("\u0001CompObj");
            if (e != null && e.isDocumentEntry()) {
                DocumentNode dn = (DocumentNode)e;
                DocumentInputStream stream = new DocumentInputStream(dn);
                byte [] bytes = IOUtils.toByteArray(stream);
                /*
                 * This array contains a string with a normal ASCII name of the
                 * application used to create this file. We want to search for that
                 * name.
                 */
                if ( arrayContains(bytes, STAR_DRAW) ) {
                    return SDA;
                } else if (arrayContains(bytes, STAR_IMPRESS)) {
                    return SDD;
                } else if (arrayContains(bytes, WORKS_QUILL96)) {
                   return WPS;
                }
            } 
        } catch (Exception e) {
            /*
             * "root.getEntry" can throw FileNotFoundException. The code inside
             * "if" can throw IOExceptions. Theoretically. Practically no
             * exceptions will likely ever appear.
             * 
             * Swallow all of them. If any occur, we just assume that we can't
             * distinguish between Draw and Impress and return something safe:
             * x-tika-msoffice
             */
        }
        return OLE;
    }
    
    // poor man's search for byte arrays, replace with some library call if
    // you know one without adding new dependencies
    private static boolean arrayContains(byte [] larger, byte [] smaller) {
        int largerCounter = 0;
        int smallerCounter = 0;
        while (largerCounter < larger.length) {
            if (larger[largerCounter] == smaller[smallerCounter]) {
                largerCounter++;
                smallerCounter++;
                if (smallerCounter == smaller.length) {
                    return true;
                }
            } else {
                largerCounter = largerCounter - smallerCounter + 1;
                smallerCounter=0;
            }
        }
        return false;
    }

    private static Set<String> getTopLevelNames(TikaInputStream stream)
            throws IOException {
        // Force the document stream to a (possibly temporary) file
        // so we don't modify the current position of the stream
        FileChannel channel = stream.getFileChannel();

        try {
            NPOIFSFileSystem fs = new NPOIFSFileSystem(channel);

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

    private static Set<String> getTopLevelNames(DirectoryNode root) {
        Set<String> names = new HashSet<String>();
        for (Entry entry : root) {
            names.add(entry.getName());
        }
        return names;
    }
}
