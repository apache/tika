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
import java.nio.channels.Channels;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import org.apache.tika.annotation.TikaComponent;
import org.apache.tika.config.ConfigDeserializer;
import org.apache.tika.config.JsonConfig;
import org.apache.tika.config.ParseContextConfig;
import org.apache.tika.config.RequestConfigurable;
import org.apache.tika.exception.EmbeddedLimitReachedException;
import org.apache.tika.exception.EncryptedDocumentException;
import org.apache.tika.exception.TikaException;
import org.apache.tika.exception.TikaMemoryLimitException;
import org.apache.tika.exception.WriteLimitReachedException;
import org.apache.tika.extractor.EmbeddedDocumentUtil;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.OneNote;
import org.apache.tika.metadata.Property;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.parser.microsoft.onenote.fsshttpb.MSOneStorePackage;
import org.apache.tika.parser.microsoft.onenote.fsshttpb.MSOneStoreParser;
import org.apache.tika.parser.microsoft.onenote.fsshttpb.streamobj.basic.AlternativePackaging;
import org.apache.tika.sax.XHTMLContentHandler;

/** Microsoft OneNote parser, based on the MS-ONE and MS-ONESTORE specs. */
@TikaComponent
@RequestConfigurable
public class OneNoteParser implements Parser {

    public static final String ONE_NOTE_PREFIX = "onenote:";
    // MS-ONESTORE 2.3.1
    private static final int HEADER_SIZE = 1024;
    private static final Logger LOG = LoggerFactory.getLogger(OneNoteParser.class);
    private static final Map<MediaType, List<String>> TYPES_MAP = new HashMap<>();
    private static final long serialVersionUID = -5504243905998074168L;
    private static final Set<MediaType> SUPPORTED_TYPES =
            Collections.unmodifiableSet(TYPES_MAP.keySet());

    static {
        // All types should be 4 bytes long, space padded as needed
        TYPES_MAP.put(MediaType.application("onenote; format=one"),
                Collections.singletonList("ONE "));
        TYPES_MAP.put(MediaType.application("onenote; format=onetoc2"),
                Collections.singletonList("TOC2"));
    }

    private final OneNoteParserConfig defaultConfig;

    public OneNoteParser() {
        this(new OneNoteParserConfig());
    }

    public OneNoteParser(OneNoteParserConfig config) {
        this.defaultConfig = config;
    }

    public OneNoteParser(JsonConfig jsonConfig) {
        this(ConfigDeserializer.buildConfig(jsonConfig, OneNoteParserConfig.class));
    }

    public OneNoteParserConfig getDefaultConfig() {
        return defaultConfig;
    }

    @Override
    public Set<MediaType> getSupportedTypes(ParseContext context) {
        return SUPPORTED_TYPES;
    }

    @Override
    public void parse(TikaInputStream tis, ContentHandler handler, Metadata metadata,
                      ParseContext context) throws IOException, SAXException, TikaException {

        OneNoteParserConfig options = ParseContextConfig.getConfig(context, "one-note-parser",
                OneNoteParserConfig.class, defaultConfig);
        try (OneNoteDirectFileResource oneNoteDirectFileResource = new OneNoteDirectFileResource(
                        tis.getFile())) {
            XHTMLContentHandler xhtml = new XHTMLContentHandler(handler, metadata, context);
            xhtml.startDocument();
            OneNoteDocument oneNoteDocument =
                    createOneNoteDocumentFromDirectFileResource(oneNoteDirectFileResource);

            OneNoteHeader header = oneNoteDocument.header;

            if (header.isMsOneStoreFormat()) {
                setHex(metadata, OneNote.BUILD_NUMBER_CREATED, header.buildNumberCreated);
                setHex(metadata, OneNote.BUILD_NUMBER_LAST_WROTE_TO_FILE,
                        header.buildNumberLastWroteToFile);
                setHex(metadata, OneNote.BUILD_NUMBER_NEWEST_WRITTEN,
                        header.buildNumberNewestWritten);
                setHex(metadata, OneNote.BUILD_NUMBER_OLDEST_WRITTEN,
                        header.buildNumberOldestWritten);
                setHex(metadata, OneNote.CB_EXPECTED_FILE_LENGTH, header.cbExpectedFileLength);
                setHex(metadata, OneNote.CB_FREE_SPACE_IN_FREE_CHUNK_LIST,
                        header.cbFreeSpaceInFreeChunkList);
                setHex(metadata, OneNote.CB_LEGACY_EXPECTED_FILE_LENGTH,
                        header.cbLegacyExpectedFileLength);
                setHex(metadata, OneNote.CB_LEGACY_FREE_SPACE_IN_FREE_CHUNK_LIST,
                        header.cbLegacyFreeSpaceInFreeChunkList);
                setHex(metadata, OneNote.CRC_NAME, header.crcName);
                setHex(metadata, OneNote.C_TRANSACTIONS_IN_LOG, header.cTransactionsInLog);
                setHex(metadata, OneNote.FFV_LAST_CODE_THAT_WROTE_TO_THIS_FILE,
                        header.ffvLastCodeThatWroteToThisFile);
                setHex(metadata, OneNote.FFV_NEWEST_CODE_THAT_HAS_WRITTEN_TO_THIS_FILE,
                        header.ffvNewestCodeThatHasWrittenToThisFile);
                setHex(metadata, OneNote.FFV_OLDEST_CODE_THAT_MAY_READ_THIS_FILE,
                        header.ffvOldestCodeThatMayReadThisFile);
                setHex(metadata, OneNote.FFV_OLDEST_CODE_THAT_HAS_WRITTEN_TO_THIS_FILE,
                        header.ffvOldestCodeThatHasWrittenToThisFile);
                setHex(metadata, OneNote.GRF_DEBUG_LOG_FLAGS, header.grfDebugLogFlags);
                setHex(metadata, OneNote.N_FILE_VERSION_GENERATION, header.nFileVersionGeneration);
                setHex(metadata, OneNote.RGB_PLACEHOLDER, header.rgbPlaceholder);
                emitSectionFileGuid(header, metadata);

                if (oneNoteDocument.transactionLogWarning != null) {
                    metadata.add(TikaCoreProperties.TIKA_META_EXCEPTION_WARNING,
                            oneNoteDocument.transactionLogWarning);
                }
                Exception structureFailure = null;
                try {
                    deserializeRootFileNodeList(oneNoteDocument);
                } catch (EncryptedDocumentException | TikaMemoryLimitException e) {
                    throw e;
                } catch (TikaException | IOException e) {
                    // a truncated or malformed root list: the dump can still recover text
                    structureFailure = e;
                }
                boolean walked = false;
                boolean outputComplete = false;
                if (structureFailure == null) {
                    OneNoteTreeWalker oneNoteTreeWalker = new OneNoteTreeWalker(options,
                            oneNoteDocument, oneNoteDirectFileResource, xhtml, metadata, context,
                            Pair.of(1L, ExtendedGUID.nil()));
                    try {
                        oneNoteTreeWalker.walkTree();
                        walked = true;
                    } catch (TikaException | IOException e) {
                        rethrowIfLimitReached(e);
                        if (oneNoteTreeWalker.hasEmittedContent()) {
                            // keep what was written; a dump would repeat it
                            recordWalkFailure(e, metadata, context);
                            walked = true;
                        } else {
                            structureFailure = e;
                        }
                    }
                    if (walked) {
                        setWalkMetadata(oneNoteTreeWalker, metadata);
                        // nothing written because the rest was excluded is the right output;
                        // the dump would write the excluded text
                        outputComplete = oneNoteTreeWalker.hasEmittedContent() ||
                                oneNoteTreeWalker.hasWithheldContent();
                    }
                }
                if (!walked) {
                    legacyFallbackDump("OneNote parse failed; falling back to legacy text dump",
                            structureFailure, metadata, xhtml, oneNoteDirectFileResource,
                            context);
                } else if (!outputComplete) {
                    // a walk that found no text, e.g. a property layout the walker does not
                    // know, still has text the dump can recover (including TOC section names)
                    legacyFallbackDump("OneNote parse produced no content; " +
                                    "falling back to legacy text dump", null, metadata, xhtml,
                            oneNoteDirectFileResource, context);
                }
            } else if (header.isLegacyOrAlternativePackaging()) {
                byte[] bytes;
                try (InputStream is = Channels.newInputStream(tis.getSeekableByteChannel())) {
                    bytes = is.readAllBytes();
                }
                MSOneStorePackage pkg = null;
                Exception parseFailure = null;
                try {
                    AlternativePackaging alternatePackageOneStoreFile = new AlternativePackaging();
                    alternatePackageOneStoreFile.doDeserializeFromByteArray(bytes, 0);
                    pkg = new MSOneStoreParser().parse(
                            alternatePackageOneStoreFile.dataElementPackage);
                } catch (TikaMemoryLimitException e) {
                    throw e;
                } catch (TikaException | IOException e) {
                    parseFailure = e;
                }
                if (pkg == null) {
                    legacyFallbackDump(
                            "OneNote FSSHTTPB parse failed; falling back to legacy text dump",
                            parseFailure, metadata, xhtml, oneNoteDirectFileResource, context);
                } else {
                    if (pkg.isEncrypted()) {
                        throw new EncryptedDocumentException(
                                "OneNote section is password protected");
                    }
                    pkg.setEmbeddedByteLimit(bytes.length);
                    pkg.setTextByteLimit(2L * bytes.length);
                    boolean walked = true;
                    try {
                        pkg.walkTree(options, metadata, xhtml, context);
                    } catch (TikaException | IOException e) {
                        rethrowIfLimitReached(e);
                        walked = false;
                        if (pkg.hasEmittedContent()) {
                            recordWalkFailure(e, metadata, context);
                        } else {
                            legacyFallbackDump("OneNote FSSHTTPB parse failed; " +
                                            "falling back to legacy text dump", e, metadata, xhtml,
                                    oneNoteDirectFileResource, context);
                        }
                    }
                    if (walked) {
                        legacyFallbackIfNoContent(pkg, metadata, xhtml,
                                oneNoteDirectFileResource, context);
                    }
                }
            } else if (isSection(header)) {
                // a OneNote 2003 section: a .one header with a legacy file version and an
                // undocumented layout
                legacyFallbackDump("OneNote 2003 section; falling back to legacy text dump",
                        null, metadata, xhtml, oneNoteDirectFileResource, context);
            } else {
                throw new TikaException("Invalid OneStore document - could not parse headers");
            }
            xhtml.endDocument();
        }


    }

    static void emitSectionFileGuid(OneNoteHeader header, Metadata metadata) {
        GUID guidFile = header.getGuidFile();
        if (guidFile != null && !guidFile.equals(GUID.nil())) {
            metadata.set(OneNote.SECTION_GUID, guidFile.toString());
        }
    }

    private static String[] sortedValues(Set<String> values) {
        String[] sorted = values.toArray(new String[0]);
        Arrays.sort(sorted);
        return sorted;
    }

    private static final String SECTION_FILE_TYPE = "{7B5C52E4-D88C-4DA7-AEB1-5378D02996D3}";

    static boolean isSection(OneNoteHeader header) {
        GUID fileType = header.getGuidFileType();
        return fileType != null && SECTION_FILE_TYPE.equalsIgnoreCase(fileType.toString());
    }

    // limits are the caller's to see; only malformed structure falls back to the dump
    private static void rethrowIfLimitReached(Exception e) throws TikaException, SAXException {
        WriteLimitReachedException.throwIfWriteLimitReached(e);
        if (e instanceof EmbeddedLimitReachedException) {
            throw (EmbeddedLimitReachedException) e;
        }
        if (e instanceof TikaMemoryLimitException) {
            throw (TikaMemoryLimitException) e;
        }
    }

    // the walk completed but every page dangled - without this a degraded
    // file would yield empty output where the dump still finds its text
    static void legacyFallbackIfNoContent(MSOneStorePackage pkg, Metadata metadata,
                                          XHTMLContentHandler xhtml,
                                          OneNoteDirectFileResource oneNoteDirectFileResource,
                                          ParseContext context)
            throws TikaException, SAXException {
        if (pkg != null && !pkg.hasEmittedContent() && !pkg.hasWithheldContent()) {
            legacyFallbackDump("OneNote FSSHTTPB parse produced no content; " +
                            "falling back to legacy text dump", null, metadata, xhtml,
                    oneNoteDirectFileResource, context);
        }
    }

    private static void setHex(Metadata metadata, Property property, long value) {
        metadata.set(property, "0x" + Long.toHexString(value));
    }

    static void setWalkMetadata(OneNoteTreeWalker walker, Metadata metadata) {
        // the original authors are the creators of the content, as in FSSHTTPB sections
        Set<String> creators = new HashSet<>(walker.getAuthors());
        creators.addAll(walker.getOriginalAuthors());
        if (!creators.isEmpty()) {
            metadata.set(TikaCoreProperties.CREATOR, sortedValues(creators));
        }
        if (!walker.getMostRecentAuthors().isEmpty()) {
            metadata.set(OneNote.MOST_RECENT_AUTHORS, sortedValues(walker.getMostRecentAuthors()));
        }
        if (!walker.getOriginalAuthors().isEmpty()) {
            metadata.set(OneNote.ORIGINAL_AUTHORS, sortedValues(walker.getOriginalAuthors()));
        }
        if (walker.getCreationTimestamp() != Long.MAX_VALUE) {
            metadata.set(OneNote.CREATION_TIMESTAMP, String.valueOf(walker.getCreationTimestamp()));
        }
        if (!Instant.MIN.equals(walker.getLastModifiedTimestamp())) {
            metadata.set(OneNote.LAST_MODIFIED_TIMESTAMP,
                    String.valueOf(walker.getLastModifiedTimestamp().toEpochMilli()));
        }
        if (walker.getLastModified() > Long.MIN_VALUE) {
            metadata.set(TikaCoreProperties.MODIFIED, String.valueOf(walker.getLastModified()));
        }
    }

    static void recordWalkFailure(Exception e, Metadata metadata, ParseContext context) {
        String warning = "OneNote parse stopped early; output is partial";
        LOG.warn(warning, e);
        metadata.add(TikaCoreProperties.TIKA_META_EXCEPTION_WARNING, warning);
        EmbeddedDocumentUtil.recordException(e, metadata, context);
    }

    private static void legacyFallbackDump(String warning, Exception cause, Metadata metadata,
                                           XHTMLContentHandler xhtml,
                                           OneNoteDirectFileResource oneNoteDirectFileResource,
                                           ParseContext context)
            throws TikaException, SAXException {
        LOG.warn(warning);
        metadata.add(TikaCoreProperties.TIKA_META_EXCEPTION_WARNING, warning);
        if (cause != null) {
            LOG.debug("OneNote parse failure", cause);
            // the exception text is recorded as ExceptionReporting allows, not in the warning
            EmbeddedDocumentUtil.recordException(cause, metadata, context);
        }
        new OneNoteLegacyDumpStrings(oneNoteDirectFileResource, xhtml).dump();
    }

    /**
     * Reads the header (MS-ONESTORE 2.3.1) and transaction log (2.3.3). A malformed log is
     * recorded in the document's {@code transactionLogWarning}; the root file node list is read
     * by {@link #deserializeRootFileNodeList}.
     */
    public OneNoteDocument createOneNoteDocumentFromDirectFileResource(
            OneNoteDirectFileResource oneNoteDirectFileResource) throws IOException, TikaException {
        OneNoteDocument oneNoteDocument = new OneNoteDocument();
        OneNotePtr oneNotePtr = new OneNotePtr(oneNoteDocument, oneNoteDirectFileResource);
        if (oneNoteDirectFileResource.size() < HEADER_SIZE) {
            throw new TikaException("Truncated OneNote file: " + oneNoteDirectFileResource.size() +
                    " bytes, shorter than its " + HEADER_SIZE + "-byte header");
        }
        oneNoteDocument.header = oneNotePtr.deserializeHeader();

        if (oneNoteDocument.header.isMsOneStoreFormat()) {
            try {
                oneNotePtr.deserializeTransactionLog(oneNoteDocument.header.fcrTransactionLog,
                        oneNoteDocument.header.cTransactionsInLog,
                        oneNoteDocument.committedFileNodeCounts);
            } catch (TikaException | IOException e) {
                // later lists end at a terminator or the end of their fragments
                LOG.debug("Could not read the OneNote transaction log", e);
                oneNoteDocument.transactionLogWarning =
                        "OneNote transaction log is malformed: " + e.getMessage();
            }
        }
        return oneNoteDocument;
    }

    /** Reads the root file node list (MS-ONESTORE 2.1.14) and the lists it references. */
    void deserializeRootFileNodeList(OneNoteDocument oneNoteDocument)
            throws IOException, TikaException {
        // shares the document's budgets with the property-set reader
        OneNotePtr oneNotePtr = new OneNotePtr(oneNoteDocument.propertySetReader);
        oneNotePtr.reposition(oneNoteDocument.header.fcrFileNodeListRoot);
        oneNotePtr.deserializeFileNodeList(oneNoteDocument.root, new FileNodePtr());
    }
}
