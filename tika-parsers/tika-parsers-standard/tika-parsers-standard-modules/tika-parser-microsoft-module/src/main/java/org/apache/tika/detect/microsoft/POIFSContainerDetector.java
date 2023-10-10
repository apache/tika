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
package org.apache.tika.detect.microsoft;

import static org.apache.tika.mime.MediaType.application;
import static org.apache.tika.mime.MediaType.image;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

import org.apache.commons.io.IOUtils;
import org.apache.poi.hssf.model.InternalWorkbook;
import org.apache.poi.poifs.filesystem.DirectoryEntry;
import org.apache.poi.poifs.filesystem.DirectoryNode;
import org.apache.poi.poifs.filesystem.DocumentInputStream;
import org.apache.poi.poifs.filesystem.DocumentNode;
import org.apache.poi.poifs.filesystem.Entry;
import org.apache.poi.poifs.filesystem.POIFSFileSystem;

import org.apache.tika.config.Field;
import org.apache.tika.detect.Detector;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.microsoft.OfficeParser;

/**
 * A detector that works on a POIFS OLE2 document
 * to figure out exactly what the file is.
 * This should work for all OLE2 documents, whether
 * they are ones supported by POI or not.
 */
public class POIFSContainerDetector implements Detector {

    /**
     * The OLE base file format
     */
    public static final MediaType OLE = application("x-tika-msoffice");
    /**
     * The protected OOXML base file format
     */
    public static final MediaType OOXML_PROTECTED = application("x-tika-ooxml-protected");

    /**
     * TIKA-3666 MSOffice or other file encrypted with DRM in an OLE container
     */
    public static final MediaType DRM_ENCRYPTED = application("x-tika-ole-drm-encrypted");

    /**
     * General embedded document type within an OLE2 container
     */
    public static final MediaType GENERAL_EMBEDDED = application("x-tika-msoffice-embedded");
    /**
     * An OLE10 Native embedded document within another OLE2 document
     */
    public static final MediaType OLE10_NATIVE =
            new MediaType(GENERAL_EMBEDDED, "format", "ole10_native");
    /**
     * Some other kind of embedded document, in a CompObj container within another OLE2 document
     */
    public static final MediaType COMP_OBJ = new MediaType(GENERAL_EMBEDDED, "format", "comp_obj");
    /**
     * Graph/Charts embedded in PowerPoint and Excel
     */
    public static final MediaType MS_GRAPH_CHART = application("vnd.ms-graph");

    /**
     * Equation embedded in Office docs
     */
    public static final MediaType MS_EQUATION = application("vnd.ms-equation");

    public static final String OCX_NAME = "\u0003OCXNAME";

    /**
     * Microsoft Excel
     */
    public static final MediaType XLS = application("vnd.ms-excel");
    /**
     * Microsoft Word
     */
    public static final MediaType DOC = application("msword");
    /**
     * Microsoft PowerPoint
     */
    public static final MediaType PPT = application("vnd.ms-powerpoint");
    /**
     * Microsoft Publisher
     */
    public static final MediaType PUB = application("x-mspublisher");
    /**
     * Microsoft Visio
     */
    public static final MediaType VSD = application("vnd.visio");
    /**
     * Microsoft Works
     */
    public static final MediaType WPS = application("vnd.ms-works");
    /**
     * Microsoft Works Spreadsheet 7.0
     */
    public static final MediaType XLR = application("x-tika-msworks-spreadsheet");
    /**
     * Microsoft Outlook
     */
    public static final MediaType MSG = application("vnd.ms-outlook");
    /**
     * Microsoft Project
     */
    public static final MediaType MPP = application("vnd.ms-project");
    /**
     * StarOffice Calc
     */
    public static final MediaType SDC = application("vnd.stardivision.calc");
    /**
     * StarOffice Draw
     */
    public static final MediaType SDA = application("vnd.stardivision.draw");
    /**
     * StarOffice Impress
     */
    public static final MediaType SDD = application("vnd.stardivision.impress");
    /**
     * StarOffice Writer
     */
    public static final MediaType SDW = application("vnd.stardivision.writer");
    /**
     * SolidWorks CAD file
     */
    public static final MediaType SLDWORKS = application("sldworks");

    public static final MediaType ESRI_LAYER = application("x-esri-layer");

    public static final MediaType DGN_8 = image("vnd.dgn;version=8");
    /**
     * Serial version UID
     */
    private static final long serialVersionUID = -3028021741663605293L;

    //We need to have uppercase for finding/comparison, but we want to maintain
    //the most common general casing for these items

    private static final String ENCRYPTED_PACKAGE = "EncryptedPackage".toUpperCase(Locale.ROOT);

    private static final String ENCRYPTED_INFO = "EncryptionInfo".toUpperCase(Locale.ROOT);

    private static final String SW_DOC_CONTENT_MGR = "SwDocContentMgr".toUpperCase(Locale.ROOT);

    private static final String SW_DOC_MGR_TEMP_STORAGE = "SwDocMgrTempStorage".toUpperCase(Locale.ROOT);

    private static final String STAR_CALC_DOCUMENT = "StarCalcDocument".toUpperCase(Locale.ROOT);

    private static final String STAR_WRITER_DOCUMENT = "StarWriterDocument".toUpperCase(Locale.ROOT);

    private static final String STAR_DRAW_DOCUMENT_3 = "StarDrawDocument3".toUpperCase(Locale.ROOT);

    private static final String WKS_SSWORK_BOOK = "WksSSWorkBook".toUpperCase(Locale.ROOT);

    private static final String DATA_SPACES = "\u0006DataSpaces".toUpperCase(Locale.ROOT);

    private static final String DRM_ENCRYPTED_DATA_SPACE = "DRMEncryptedDataSpace".toUpperCase(Locale.ROOT);

    private static final String DRM_DATA_SPACE = "\tDRMDataSpace".toUpperCase(Locale.ROOT);

    private static final String WORD_DOCUMENT = "WordDocument".toUpperCase(Locale.ROOT);

    private static final String QUILL = "Quill".toUpperCase(Locale.ROOT);

    private static final String POWERPOINT_DOCUMENT = "PowerPoint Document".toUpperCase(Locale.ROOT);

    private static final String VISIO_DOCUMENT = "VisioDocument".toUpperCase(Locale.ROOT);

    private static final String OLE10_NATIVE_STRING = "\u0001Ole10Native".toUpperCase(Locale.ROOT);

    private static final String MAT_OST = "MatOST".toUpperCase(Locale.ROOT);

    private static final String CONTENTS = "CONTENTS".toUpperCase(Locale.ROOT);

    private static final String SPELLING = "SPELLING".toUpperCase(Locale.ROOT);

    private static final String OBJ_INFO = "\u0003ObjInfo".toUpperCase(Locale.ROOT);

    private static final String COMP_OBJ_STRING = "\u0001CompObj".toUpperCase(Locale.ROOT);

    private static final String PROPS = "Props".toUpperCase(Locale.ROOT);

    private static final String PROPS_9 = "Props9".toUpperCase(Locale.ROOT);

    private static final String PROPS_12 = "Props12".toUpperCase(Locale.ROOT);

    private static final String EQUATION_NATIVE = "Equation Native".toUpperCase(Locale.ROOT);

    private static final String LAYER = "Layer".toUpperCase(Locale.ROOT);

    private static final String DGN_MF = "Dgn~Mf".toUpperCase(Locale.ROOT);

    private static final String DGN_S = "Dgn~S".toUpperCase(Locale.ROOT);
    private static final String DGN_H = "Dgn~H".toUpperCase(Locale.ROOT);

    private static final String SUBSTG_1 = "__substg1.0_".toUpperCase(Locale.ROOT);

    /**
     * An ASCII String "StarImpress"
     */
    private static final byte[] STAR_IMPRESS = "StarImpress".getBytes(StandardCharsets.US_ASCII);

    /**
     * An ASCII String "StarDraw"
     */
    private static final byte[] STAR_DRAW = "StarDraw".getBytes(StandardCharsets.US_ASCII);

    /**
     * An ASCII String "Quill96" for Works Files
     */
    private static final byte[] WORKS_QUILL96 = "Quill96".getBytes(StandardCharsets.US_ASCII);

    /**
     * An ASCII String "MSGraph.Chart" for embedded MSGraph files
     * The full designator includes a version, e.g. MSGraph.Chart.8
     */
    private static final byte[] MS_GRAPH_CHART_BYTES =
            "MSGraph.Chart".getBytes(StandardCharsets.US_ASCII);

    /**
     * Regexp for matching the MPP Project Data stream
     */
    private static final Pattern mppDataMatch = Pattern.compile("\\s\\s\\s\\d+");

    @Field
    private int markLimit = 128 * 1024 * 1024;

    /**
     * Internal detection of the specific kind of OLE2 document, based on the
     * names of the top-level streams within the file. In some cases the
     * detection may need access to the root {@link DirectoryEntry} of that file
     * for best results. The entry can be given as a second, optional argument.
     *
     * <p/>
     * Following
     *
     * <a href="https://learn.microsoft.com/en-us/openspecs/windows_protocols/ms-cfb/60fe8611-66c3-496b-b70d-a504c94c9ace">2.6.1 of MS-CFB </a>,
     * The detection is performed on case insensitive entry names.
     *
     * @param anyCaseNames
     * @param root
     * @return
     */
    public static MediaType detect(Set<String> anyCaseNames, DirectoryEntry root) {
        if (anyCaseNames == null || anyCaseNames.size() == 0) {
            return OLE;
        }

        Set<String> ucNames = upperCase(anyCaseNames);
        MediaType mediaType = checkEncrypted(ucNames, root);
        if (mediaType != null) {
            return mediaType;
        }

        for (String workbookEntryName : InternalWorkbook.WORKBOOK_DIR_ENTRY_NAMES) {
            if (ucNames.contains(workbookEntryName)) {
                MediaType tmp = processCompObjFormatType(root);
                if (tmp.equals(MS_GRAPH_CHART)) {
                    return MS_GRAPH_CHART;
                }
                return XLS;
            }
        }
        if (ucNames.contains(SW_DOC_CONTENT_MGR) && ucNames.contains(SW_DOC_MGR_TEMP_STORAGE)) {
            return SLDWORKS;
        } else if (ucNames.contains(STAR_CALC_DOCUMENT)) {
            // Star Office Calc
            return SDC;
        } else if (ucNames.contains(STAR_WRITER_DOCUMENT)) {
            return SDW;
        } else if (ucNames.contains(STAR_DRAW_DOCUMENT_3)) {
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
        } else if (ucNames.contains(WKS_SSWORK_BOOK)) {
            // This check has to be before names.contains("Workbook")
            // Works 7.0 spreadsheet files contain both
            // we want to avoid classifying this as Excel
            return XLR;
        } else if (ucNames.contains("BOOK")) {
            // Excel 95 or older, we won't be able to parse this....
            return XLS;
        } else if (ucNames.contains(WORD_DOCUMENT)) {
            return DOC;
        } else if (ucNames.contains(QUILL)) {
            return PUB;
        } else if (ucNames.contains(POWERPOINT_DOCUMENT)) {
            return PPT;
        } else if (ucNames.contains(VISIO_DOCUMENT)) {
            return VSD;
        } else if (ucNames.contains(OLE10_NATIVE_STRING)) {
            return OLE10_NATIVE;
        } else if (ucNames.contains(MAT_OST)) {
            // this occurs on older Works Word Processor files (versions 3.0 and 4.0)
            return WPS;
        } else if (ucNames.contains(CONTENTS) && ucNames.contains(SPELLING)) {
            // Newer Works files
            return WPS;
        } else if (ucNames.contains(EQUATION_NATIVE)) {
            return MS_EQUATION;
        } else if (ucNames.contains(OCX_NAME)) {
            //active x control should be parsed as OLE, not COMP_OBJ -- TIKA-4091
            //TODO -- create a mime for active x
            return OLE;
        } else if (ucNames.contains(CONTENTS) && ucNames.contains(OBJ_INFO)) {
            return COMP_OBJ;
        } else if (ucNames.contains(CONTENTS) && ucNames.contains(COMP_OBJ_STRING)) {
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
        } else if (ucNames.contains(CONTENTS)) {
            // CONTENTS without SPELLING nor CompObj normally means some sort
            //  of embedded non-office file inside an OLE2 document
            // This is most commonly triggered on nested directories
            return OLE;
        } else if (ucNames.contains(COMP_OBJ_STRING) &&
                (ucNames.contains(PROPS) || ucNames.contains(PROPS_9) ||
                        ucNames.contains(PROPS_12))) {
            // Could be Project, look for common name patterns
            for (String name : ucNames) {
                if (mppDataMatch.matcher(name).matches()) {
                    return MPP;
                }
            }
        } else if (ucNames.contains(LAYER)) {
            //in one test file, also saw LayerSmallImage and LayerLargeImage
            //maybe add those if we get false positives?
            //in other test files there was a single entry for "Layer"
            return ESRI_LAYER;
        } else if (ucNames.contains(DGN_MF) && ucNames.contains(DGN_S) &&
                ucNames.contains(DGN_H)) {
            return DGN_8;
        } else {
            for (String name : ucNames) {
                if (name.startsWith(SUBSTG_1)) {
                    return MSG;
                }
            }
        }


        // Couldn't detect a more specific type
        return OLE;
    }

    private static MediaType checkEncrypted(Set<String> ucNames, DirectoryEntry root) {
        //figure out if encrypted/pw protected first
        if (ucNames.contains(DATA_SPACES)) {
            //OLE2 drm encrypted -- TIKA-3666
            if (findRecursively(root, DRM_DATA_SPACE, 0, 10)) {
                return DRM_ENCRYPTED;
            }
        }

        if (ucNames.contains(ENCRYPTED_PACKAGE)) {
            if (ucNames.contains(ENCRYPTED_INFO)) {
                // This is a protected OOXML document, which is an OLE2 file
                //  with an Encrypted Stream which holds the OOXML data
                // Without decrypting the stream, we can't tell what kind of
                //  OOXML file we have. Return a general OOXML Protected type,
                //  and hope the name based detection can guess the rest!

                // This is the standard issue method of encryption for ooxml and
                // is supported by POI

                //Until Tika 1.23, we also required: && names.contains("\u0006DataSpaces")
                //See TIKA-2982
                return OOXML_PROTECTED;
            } else if (ucNames.contains(DATA_SPACES)) {
                //Try to look for the DRMEncrypted type (TIKA-3666); as of 5.2.0, this is not
                // supported by POI, but we should still detect it.

                //Do we also want to look for "DRMEncryptedTransform"?
                if (findRecursively(root, DRM_ENCRYPTED_DATA_SPACE, 0, 10)) {
                    return DRM_ENCRYPTED;
                }
            }
        }
        return null;
    }

    private static Set<String> upperCase(Set<String> names) {
        Set<String> uc = new HashSet<>(names.size());
        for (String s : names) {
            uc.add(s.toUpperCase(Locale.ROOT));
        }
        return uc;
    }

    /**
     *
     * @param entry entry to search
     * @param targetName Upper cased target name
     * @param depth current depth
     * @param maxDepth maximum allowed depth
     * @return
     */
    private static boolean findRecursively(Entry entry, String targetName, int depth,
                                           int maxDepth) {
        if (entry == null) {
            return false;
        }
        if (entry.getName().toUpperCase(Locale.ROOT).equals(targetName)) {
            return true;
        }
        if (depth >= maxDepth) {
            return false;
        }
        if (entry instanceof DirectoryEntry) {
            for (Iterator<Entry> it = ((DirectoryEntry)entry).getEntries(); it.hasNext(); ) {
                Entry child = it.next();
                if (findRecursively(child, targetName, depth + 1, maxDepth)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Is this one of the kinds of formats which uses CompObj to
     * store all of their data, eg Star Draw, Star Impress or
     * (older) Works?
     * If not, it's likely an embedded resource
     */
    private static MediaType processCompObjFormatType(DirectoryEntry root) {
        try {
            Entry entry = OfficeParser.getUCEntry(root, COMP_OBJ_STRING);
            if (entry != null && entry.isDocumentEntry()) {
                DocumentNode dn = (DocumentNode) entry;
                DocumentInputStream stream = new DocumentInputStream(dn);
                byte[] bytes = IOUtils.toByteArray(stream);
                /*
                 * This array contains a string with a normal ASCII name of the
                 * application used to create this file. We want to search for that
                 * name.
                 */
                if (arrayContains(bytes, MS_GRAPH_CHART_BYTES)) {
                    return MS_GRAPH_CHART;
                } else if (arrayContains(bytes, STAR_DRAW)) {
                    return SDA;
                } else if (arrayContains(bytes, STAR_IMPRESS)) {
                    return SDD;
                } else if (arrayContains(bytes, WORKS_QUILL96)) {
                    return WPS;
                }
            }
        } catch (SecurityException e) {
            throw e;
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
    private static boolean arrayContains(byte[] larger, byte[] smaller) {
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
                smallerCounter = 0;
            }
        }
        return false;
    }

    /**
     * These are the literal top level names in the root. These are not uppercased
     * @param root
     * @return
     */
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
