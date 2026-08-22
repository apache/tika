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
package org.apache.tika.parser.microsoft.onenote;

import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import org.apache.tika.exception.TikaException;
import org.apache.tika.exception.WriteLimitReachedException;
import org.apache.tika.io.TemporaryResources;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.Property;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.parser.microsoft.onenote.fsshttpb.MSOneStorePackage;
import org.apache.tika.parser.microsoft.onenote.fsshttpb.MSOneStoreParser;
import org.apache.tika.parser.microsoft.onenote.fsshttpb.streamobj.basic.AlternativePackaging;
import org.apache.tika.sax.XHTMLContentHandler;

/**
 * OneNote tika parser capable of parsing Microsoft OneNote files.
 * <p>
 * Based on the Microsoft specs MS-ONE and MS-ONESTORE.
 */
public class OneNoteParser implements Parser {

    public static final String ONE_NOTE_PREFIX = "onenote:";
    private static final Logger LOG = LoggerFactory.getLogger(OneNoteParser.class);
    private static final Map<MediaType, List<String>> TYPES_MAP = new HashMap<>();
    /**
     * Serial version UID
     */
    private static final long serialVersionUID = -5504243905998074168L;
    private static final Set<MediaType> SUPPORTED_TYPES =
            Collections.unmodifiableSet(TYPES_MAP.keySet());

    static {
        // All types should be 4 bytes long, space padded as needed
        TYPES_MAP.put(MediaType.application("onenote; format=one"),
                Collections.singletonList("ONE "));
        // TODO - add onetoc and other onenote mime types
    }

    private final OneNoteTreeWalkerOptions options = new OneNoteTreeWalkerOptions();

    @Override
    public Set<MediaType> getSupportedTypes(ParseContext context) {
        return SUPPORTED_TYPES;
    }

    @Override
    public void parse(InputStream stream, ContentHandler handler, Metadata metadata,
                      ParseContext context) throws IOException, SAXException, TikaException {
        byte[] oneStoreFileBytes = IOUtils.toByteArray(stream);

        try (TemporaryResources temporaryResources = new TemporaryResources();
                TikaInputStream tikaInputStream = TikaInputStream.get(oneStoreFileBytes);
                OneNoteDirectFileResource oneNoteDirectFileResource = new OneNoteDirectFileResource(
                        tikaInputStream.getFile())) {
            XHTMLContentHandler xhtml = new XHTMLContentHandler(handler, metadata);
            xhtml.startDocument();
            temporaryResources.addResource(oneNoteDirectFileResource);
            OneNoteDocument oneNoteDocument =
                    createOneNoteDocumentFromDirectFileResource(oneNoteDirectFileResource);

            OneNoteHeader header = oneNoteDocument.header;

            if (header.isMsOneStoreFormat()) {
                metadata.set(ONE_NOTE_PREFIX + "buildNumberCreated",
                        "0x" + Long.toHexString(oneNoteDocument.header.buildNumberCreated));
                metadata.set(ONE_NOTE_PREFIX + "buildNumberLastWroteToFile",
                        "0x" + Long.toHexString(oneNoteDocument.header.buildNumberLastWroteToFile));
                metadata.set(ONE_NOTE_PREFIX + "buildNumberNewestWritten",
                        "0x" + Long.toHexString(oneNoteDocument.header.buildNumberNewestWritten));
                metadata.set(ONE_NOTE_PREFIX + "buildNumberOldestWritten",
                        "0x" + Long.toHexString(oneNoteDocument.header.buildNumberOldestWritten));
                metadata.set(ONE_NOTE_PREFIX + "cbExpectedFileLength",
                        "0x" + Long.toHexString(oneNoteDocument.header.cbExpectedFileLength));
                metadata.set(ONE_NOTE_PREFIX + "cbFreeSpaceInFreeChunkList",
                        "0x" + Long.toHexString(oneNoteDocument.header.cbFreeSpaceInFreeChunkList));
                metadata.set(ONE_NOTE_PREFIX + "cbLegacyExpectedFileLength",
                        "0x" + Long.toHexString(oneNoteDocument.header.cbLegacyExpectedFileLength));
                metadata.set(ONE_NOTE_PREFIX + "cbLegacyFreeSpaceInFreeChunkList", "0x" +
                        Long.toHexString(oneNoteDocument.header.cbLegacyFreeSpaceInFreeChunkList));
                metadata.set(ONE_NOTE_PREFIX + "crcName", "0x" + Long.toHexString(oneNoteDocument.header.crcName));
                metadata.set(ONE_NOTE_PREFIX + "cTransactionsInLog",
                        "0x" + Long.toHexString(oneNoteDocument.header.cTransactionsInLog));
                metadata.set(ONE_NOTE_PREFIX + "ffvLastCodeThatWroteToThisFile", "0x" +
                        Long.toHexString(oneNoteDocument.header.ffvLastCodeThatWroteToThisFile));
                metadata.set(ONE_NOTE_PREFIX + "ffvNewestCodeThatHasWrittenToThisFile", "0x" + Long.toHexString(
                        oneNoteDocument.header.ffvNewestCodeThatHasWrittenToThisFile));
                metadata.set(ONE_NOTE_PREFIX + "ffvOldestCodeThatMayReadThisFile", "0x" +
                        Long.toHexString(oneNoteDocument.header.ffvOldestCodeThatMayReadThisFile));
                metadata.set(ONE_NOTE_PREFIX + "ffvOldestCodeThatHasWrittenToThisFile", "0x" + Long.toHexString(
                        oneNoteDocument.header.ffvOldestCodeThatHasWrittenToThisFile));
                metadata.set(ONE_NOTE_PREFIX + "grfDebugLogFlags",
                        "0x" + Long.toHexString(oneNoteDocument.header.grfDebugLogFlags));
                metadata.set(ONE_NOTE_PREFIX + "nFileVersionGeneration",
                        "0x" + Long.toHexString(oneNoteDocument.header.nFileVersionGeneration));
                metadata.set(ONE_NOTE_PREFIX + "rgbPlaceholder",
                        "0x" + Long.toHexString(oneNoteDocument.header.rgbPlaceholder));

                Exception structureFailure = oneNoteDocument.structureParseException;
                boolean walked = false;
                if (structureFailure == null) {
                    try {
                        Pair<Long, ExtendedGUID> roleAndContext = Pair.of(1L, ExtendedGUID.nil());
                        OneNoteTreeWalker oneNoteTreeWalker =
                                new OneNoteTreeWalker(options, oneNoteDocument,
                                        oneNoteDirectFileResource, xhtml, metadata, context,
                                        roleAndContext);

                        oneNoteTreeWalker.walkTree();

                        if (!oneNoteTreeWalker.getAuthors().isEmpty()) {
                            metadata.set(TikaCoreProperties.CREATOR,
                                    sortedValues(oneNoteTreeWalker.getAuthors()));
                        }
                        if (!oneNoteTreeWalker.getMostRecentAuthors().isEmpty()) {
                            metadata.set(
                                    Property.externalTextBag(ONE_NOTE_PREFIX + "mostRecentAuthors"),
                                    sortedValues(oneNoteTreeWalker.getMostRecentAuthors()));
                        }
                        if (!oneNoteTreeWalker.getOriginalAuthors().isEmpty()) {
                            metadata.set(
                                    Property.externalTextBag(ONE_NOTE_PREFIX + "originalAuthors"),
                                    sortedValues(oneNoteTreeWalker.getOriginalAuthors()));
                        }
                        if (!Instant.MAX.equals(
                                Instant.ofEpochMilli(oneNoteTreeWalker.getCreationTimestamp()))) {
                            metadata.set(ONE_NOTE_PREFIX + "creationTimestamp",
                                    String.valueOf(oneNoteTreeWalker.getCreationTimestamp()));
                        }
                        if (!Instant.MIN.equals(oneNoteTreeWalker.getLastModifiedTimestamp())) {
                            metadata.set(ONE_NOTE_PREFIX + "lastModifiedTimestamp", String.valueOf(
                                    oneNoteTreeWalker.getLastModifiedTimestamp().toEpochMilli()));
                        }
                        if (oneNoteTreeWalker.getLastModified() > Long.MIN_VALUE) {
                            metadata.set(TikaCoreProperties.MODIFIED,
                                    String.valueOf(oneNoteTreeWalker.getLastModified()));
                        }
                        walked = true;
                    } catch (Exception e) {
                        rethrowIfLimitReached(e);
                        structureFailure = e;
                    }
                }
                if (!walked) {
                    legacyFallbackDump("OneNote parse failed; falling back to legacy text dump: " +
                                    failureMessage(structureFailure), structureFailure, metadata,
                            xhtml, oneNoteDirectFileResource);
                }
            } else if (header.isLegacyOrAlternativePackaging()) {
                MSOneStorePackage pkg = null;
                try {
                    AlternativePackaging alternatePackageOneStoreFile = new AlternativePackaging();
                    //enable streaming deserialization
                    alternatePackageOneStoreFile.doDeserializeFromByteArray(oneStoreFileBytes, 0);

                    MSOneStoreParser onenoteParser = new MSOneStoreParser();
                    pkg = onenoteParser.parse(alternatePackageOneStoreFile.dataElementPackage);

                    pkg.walkTree(options, metadata, xhtml, context);
                } catch (Exception e) {
                    rethrowIfLimitReached(e);
                    legacyFallbackDump(
                            "OneNote FSSHTTPB parse failed; falling back to legacy text dump: " +
                                    failureMessage(e), e, metadata, xhtml,
                            oneNoteDirectFileResource);
                    pkg = null;
                }
                legacyFallbackIfNoContent(pkg, metadata, xhtml, oneNoteDirectFileResource);
            } else {
                throw new TikaException("Invalid OneStore document - could not parse headers");
            }
            xhtml.endDocument();
        }


    }

    private static String[] sortedValues(Set<String> values) {
        String[] sorted = values.toArray(new String[0]);
        Arrays.sort(sorted);
        return sorted;
    }

    private static void rethrowIfLimitReached(Exception e) throws TikaException, SAXException {
        WriteLimitReachedException.throwIfWriteLimitReached(e);
    }

    private static String failureMessage(Exception e) {
        return e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
    }

    // the walk completed but every page dangled - without this a degraded
    // file would yield empty output where the dump still finds its text
    static void legacyFallbackIfNoContent(MSOneStorePackage pkg, Metadata metadata,
                                          XHTMLContentHandler xhtml,
                                          OneNoteDirectFileResource oneNoteDirectFileResource)
            throws TikaException, SAXException {
        if (pkg != null && !pkg.hasEmittedContent()) {
            legacyFallbackDump("OneNote FSSHTTPB parse produced no content; " +
                            "falling back to legacy text dump", null, metadata, xhtml,
                    oneNoteDirectFileResource);
        }
    }

    private static void legacyFallbackDump(String warning, Exception cause, Metadata metadata,
                                           XHTMLContentHandler xhtml,
                                           OneNoteDirectFileResource oneNoteDirectFileResource)
            throws TikaException, SAXException {
        LOG.warn(warning);
        if (cause != null) {
            LOG.debug("OneNote parse failure", cause);
        }
        metadata.add(TikaCoreProperties.TIKA_META_EXCEPTION_WARNING, warning);
        new OneNoteLegacyDumpStrings(oneNoteDirectFileResource, xhtml).dump();
    }

    /**
     * Create a OneNoteDocument object.
     * <p>
     * This won't actually have the binary data of the sections, but it's more of a
     * metadata structure that contains
     * the general structure of the container and contains offset positions of where to find the
     * binary data we care about.
     * <p>
     * OneNote files are of format:
     * <p>
     * The header (section 2.3.1 in MS-ONESTORE) is the first 1024 bytes of the file. It contains
     * references to the other structures in the
     * file as well as metadata about the file.
     * The free chunk list (section 2.3.2 in MS-ONESTORE) defines where there are free spaces in
     * the file where data can be written.
     * The transaction log (section 2.3.3 in MS-ONESTORE) stores the state and length of each
     * file node list (section 2.4 in MS-ONESTORE)
     * in the file.
     * The hashed chunk list (section 2.3.4 in MS-ONESTORE) stores read-only objects in the file
     * that can be referenced by multiple
     * revisions (section 2.1.8 in MS-ONESTORE).
     * The root file node list (section 2.1.14 in MS-ONESTORE) is the file node list that is the
     * root of the tree of all file node lists in
     * the file.
     * <p>
     * In this method we first parse the header.
     * <p>
     * After parsing the header, this results in header.fcrFileNodeListRoot that points to the first
     *
     * @param oneNoteDirectFileResource A random access file resource used as the source of the
     *                                  content.
     * @return A parsed one note document. This document does not contain any of the binary data,
     * rather it just contains
     * the data pointers and metadata. A failure while parsing the root file node list is not
     * thrown; it is recorded in the returned document's {@code structureParseException}.
     * @throws IOException Will throw IOException in typical IO issue situations.
     */
    public OneNoteDocument createOneNoteDocumentFromDirectFileResource(
            OneNoteDirectFileResource oneNoteDirectFileResource) throws IOException, TikaException {
        OneNoteDocument oneNoteDocument = new OneNoteDocument();
        OneNotePtr oneNotePtr = new OneNotePtr(oneNoteDocument, oneNoteDirectFileResource);
        // First parse out the header.
        oneNoteDocument.header = oneNotePtr.deserializeHeader();

        if (oneNoteDocument.header.isMsOneStoreFormat()) {
            // Now that we parsed the header, the "root file node list"
            try {
                oneNotePtr.reposition(oneNoteDocument.header.fcrFileNodeListRoot);
                FileNodePtr curPath = new FileNodePtr();
                oneNotePtr.deserializeFileNodeList(oneNoteDocument.root, curPath);
            } catch (TikaException | IOException | RuntimeException e) {
                // a truncated or malformed root list is recorded, not thrown, so the
                // caller can fall back to the legacy string dump
                oneNoteDocument.structureParseException = e;
            }
        }
        return oneNoteDocument;
    }
}
