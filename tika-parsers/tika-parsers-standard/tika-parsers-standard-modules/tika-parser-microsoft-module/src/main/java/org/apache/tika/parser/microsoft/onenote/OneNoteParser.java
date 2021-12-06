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
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import org.apache.tika.exception.TikaException;
import org.apache.tika.io.TemporaryResources;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.Property;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.AbstractParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.microsoft.onenote.fsshttpb.MSOneStorePackage;
import org.apache.tika.parser.microsoft.onenote.fsshttpb.MSOneStoreParser;
import org.apache.tika.parser.microsoft.onenote.fsshttpb.streamobj.basic.AlternativePackaging;
import org.apache.tika.sax.XHTMLContentHandler;

/**
 * OneNote tika parser capable of parsing Microsoft OneNote files.
 * <p>
 * Based on the Microsoft specs MS-ONE and MS-ONESTORE.
 */
public class OneNoteParser extends AbstractParser {

    private static final Map<MediaType, List<String>> typesMap = new HashMap<>();
    /**
     * Serial version UID
     */
    private static final long serialVersionUID = -5504243905998074168L;
    private static final Set<MediaType> SUPPORTED_TYPES =
            Collections.unmodifiableSet(typesMap.keySet());

    static {
        // All types should be 4 bytes long, space padded as needed
        typesMap.put(MediaType.application("onenote; format=one"),
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
                OneNoteDirectFileResource oneNoteDirectFileResource =
                     new OneNoteDirectFileResource(tikaInputStream.getFile())) {
            XHTMLContentHandler xhtml = new XHTMLContentHandler(handler, metadata);
            xhtml.startDocument();
            temporaryResources.addResource(oneNoteDirectFileResource);
            OneNoteDocument oneNoteDocument =
                    createOneNoteDocumentFromDirectFileResource(oneNoteDirectFileResource);

            OneNoteHeader header = oneNoteDocument.header;

            if (header.isMsOneStoreFormat()) {
                metadata.set("buildNumberCreated",
                        "0x" + Long.toHexString(oneNoteDocument.header.buildNumberCreated));
                metadata.set("buildNumberLastWroteToFile",
                        "0x" + Long.toHexString(oneNoteDocument.header.buildNumberLastWroteToFile));
                metadata.set("buildNumberNewestWritten",
                        "0x" + Long.toHexString(oneNoteDocument.header.buildNumberNewestWritten));
                metadata.set("buildNumberOldestWritten",
                        "0x" + Long.toHexString(oneNoteDocument.header.buildNumberOldestWritten));
                metadata.set("cbExpectedFileLength",
                        "0x" + Long.toHexString(oneNoteDocument.header.cbExpectedFileLength));
                metadata.set("cbFreeSpaceInFreeChunkList",
                        "0x" + Long.toHexString(oneNoteDocument.header.cbFreeSpaceInFreeChunkList));
                metadata.set("cbLegacyExpectedFileLength",
                        "0x" + Long.toHexString(oneNoteDocument.header.cbLegacyExpectedFileLength));
                metadata.set("cbLegacyFreeSpaceInFreeChunkList", "0x" +
                        Long.toHexString(oneNoteDocument.header.cbLegacyFreeSpaceInFreeChunkList));
                metadata.set("crcName", "0x" + Long.toHexString(oneNoteDocument.header.crcName));
                metadata.set("cTransactionsInLog",
                        "0x" + Long.toHexString(oneNoteDocument.header.cTransactionsInLog));
                metadata.set("ffvLastCodeThatWroteToThisFile", "0x" +
                        Long.toHexString(oneNoteDocument.header.ffvLastCodeThatWroteToThisFile));
                metadata.set("ffvNewestCodeThatHasWrittenToThisFile", "0x" + Long.toHexString(
                        oneNoteDocument.header.ffvNewestCodeThatHasWrittenToThisFile));
                metadata.set("ffvOldestCodeThatMayReadThisFile", "0x" +
                        Long.toHexString(oneNoteDocument.header.ffvOldestCodeThatMayReadThisFile));
                metadata.set("ffvOldestCodeThatHasWrittenToThisFile", "0x" + Long.toHexString(
                        oneNoteDocument.header.ffvOldestCodeThatHasWrittenToThisFile));
                metadata.set("grfDebugLogFlags",
                        "0x" + Long.toHexString(oneNoteDocument.header.grfDebugLogFlags));
                metadata.set("nFileVersionGeneration",
                        "0x" + Long.toHexString(oneNoteDocument.header.nFileVersionGeneration));
                metadata.set("rgbPlaceholder",
                        "0x" + Long.toHexString(oneNoteDocument.header.rgbPlaceholder));

                Pair<Long, ExtendedGUID> roleAndContext = Pair.of(1L, ExtendedGUID.nil());
                OneNoteTreeWalker oneNoteTreeWalker =
                        new OneNoteTreeWalker(options, oneNoteDocument, oneNoteDirectFileResource,
                                xhtml, metadata, context, roleAndContext);

                oneNoteTreeWalker.walkTree();

                if (!oneNoteTreeWalker.getAuthors().isEmpty()) {
                    metadata.set(Property.externalTextBag("authors"),
                            oneNoteTreeWalker.getAuthors().toArray(new String[]{}));
                }
                if (!oneNoteTreeWalker.getMostRecentAuthors().isEmpty()) {
                    metadata.set(Property.externalTextBag("mostRecentAuthors"),
                            oneNoteTreeWalker.getMostRecentAuthors().toArray(new String[]{}));
                }
                if (!oneNoteTreeWalker.getOriginalAuthors().isEmpty()) {
                    metadata.set(Property.externalTextBag("originalAuthors"),
                            oneNoteTreeWalker.getOriginalAuthors().toArray(new String[]{}));
                }
                if (!Instant.MAX.equals(
                        Instant.ofEpochMilli(oneNoteTreeWalker.getCreationTimestamp()))) {
                    metadata.set("creationTimestamp",
                            String.valueOf(oneNoteTreeWalker.getCreationTimestamp()));
                }
                if (!Instant.MIN.equals(oneNoteTreeWalker.getLastModifiedTimestamp())) {
                    metadata.set("lastModifiedTimestamp", String.valueOf(
                            oneNoteTreeWalker.getLastModifiedTimestamp().toEpochMilli()));
                }
                if (oneNoteTreeWalker.getLastModified() > Long.MIN_VALUE) {
                    metadata.set("lastModified",
                            String.valueOf(oneNoteTreeWalker.getLastModified()));
                }
            } else if (header.isLegacyOrAlternativePackaging()) {
                try {
                    AlternativePackaging alternatePackageOneStoreFile = new AlternativePackaging();
                    alternatePackageOneStoreFile.DoDeserializeFromByteArray(oneStoreFileBytes, 0);

                    MSOneStoreParser onenoteParser = new MSOneStoreParser();
                    MSOneStorePackage pkg =
                            onenoteParser.Parse(alternatePackageOneStoreFile.dataElementPackage);

                    pkg.walkTree(options, metadata, xhtml);
                } catch (Exception e) {
                    OneNoteLegacyDumpStrings dumpStrings =
                            new OneNoteLegacyDumpStrings(oneNoteDirectFileResource, xhtml);
                    dumpStrings.dump();
                }
            } else {
                throw new TikaException("Invalid OneStore document - could not parse headers");
            }
            xhtml.endDocument();
        }


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
     * the data pointers and metadata.
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
            oneNotePtr.reposition(oneNoteDocument.header.fcrFileNodeListRoot);
            FileNodePtr curPath = new FileNodePtr();
            oneNotePtr.deserializeFileNodeList(oneNoteDocument.root, curPath);
        }
        return oneNoteDocument;
    }
}
