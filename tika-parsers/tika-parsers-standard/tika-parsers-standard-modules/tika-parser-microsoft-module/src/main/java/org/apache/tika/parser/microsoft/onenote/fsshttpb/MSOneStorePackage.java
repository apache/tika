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

package org.apache.tika.parser.microsoft.onenote.fsshttpb;

import static org.apache.tika.parser.microsoft.onenote.OneNoteParser.ONE_NOTE_PREFIX;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.Month;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.xml.sax.SAXException;

import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.Property;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.parser.microsoft.onenote.OneNotePropertyEnum;
import org.apache.tika.parser.microsoft.onenote.OneNoteTreeWalkerOptions;
import org.apache.tika.parser.microsoft.onenote.fsshttpb.property.EightBytesOfData;
import org.apache.tika.parser.microsoft.onenote.fsshttpb.property.FourBytesOfData;
import org.apache.tika.parser.microsoft.onenote.fsshttpb.property.IProperty;
import org.apache.tika.parser.microsoft.onenote.fsshttpb.property.PrtFourBytesOfLengthFollowedByData;
import org.apache.tika.parser.microsoft.onenote.fsshttpb.streamobj.CellManifestDataElementData;
import org.apache.tika.parser.microsoft.onenote.fsshttpb.streamobj.PropertySet;
import org.apache.tika.parser.microsoft.onenote.fsshttpb.streamobj.RevisionManifestDataElementData;
import org.apache.tika.parser.microsoft.onenote.fsshttpb.streamobj.RevisionStoreObject;
import org.apache.tika.parser.microsoft.onenote.fsshttpb.streamobj.RevisionStoreObjectGroup;
import org.apache.tika.parser.microsoft.onenote.fsshttpb.streamobj.StorageIndexCellMapping;
import org.apache.tika.parser.microsoft.onenote.fsshttpb.streamobj.StorageIndexDataElementData;
import org.apache.tika.parser.microsoft.onenote.fsshttpb.streamobj.StorageIndexRevisionMapping;
import org.apache.tika.parser.microsoft.onenote.fsshttpb.streamobj.StorageManifestDataElementData;
import org.apache.tika.parser.microsoft.onenote.fsshttpb.streamobj.basic.CellID;
import org.apache.tika.parser.microsoft.onenote.fsshttpb.streamobj.basic.ExGuid;
import org.apache.tika.parser.microsoft.onenote.fsshttpb.streamobj.basic.HeaderCell;
import org.apache.tika.parser.microsoft.onenote.fsshttpb.streamobj.basic.PropertyID;
import org.apache.tika.parser.microsoft.onenote.fsshttpb.streamobj.basic.PropertyType;
import org.apache.tika.parser.microsoft.onenote.fsshttpb.unsigned.Unsigned;
import org.apache.tika.parser.microsoft.onenote.fsshttpb.util.BitConverter;
import org.apache.tika.sax.XHTMLContentHandler;

public class MSOneStorePackage {
    /**
     * See spec MS-ONE - 2.3.1 - TIME32 - epoch of jan 1 1980 UTC.
     * So we create this offset used to calculate number of seconds between this and the Instant
     * .EPOCH.
     */
    private static final long TIME32_EPOCH_DIFF_1980;
    /**
     * See spec MS-DTYP - 2.3.3 - DATETIME dates are based on epoch of jan 1 1601 UTC.
     * So we create this offset used to calculate number of seconds between this and the Instant
     * .EPOCH.
     */
    private static final long DATETIME_EPOCH_DIFF_1601;
    private static final Pattern HYPERLINK_PATTERN =
            Pattern.compile("\uFDDFHYPERLINK\\s+\"([^\"]+)\"([^\"]+)$");
    private static final String P = "p";

    static {
        LocalDateTime time32Epoch1980 = LocalDateTime.of(1980, Month.JANUARY, 1, 0, 0);
        Instant instant = time32Epoch1980.atZone(ZoneOffset.UTC).toInstant();
        TIME32_EPOCH_DIFF_1980 = (instant.toEpochMilli() - Instant.EPOCH.toEpochMilli()) / 1000;
    }

    static {
        LocalDateTime time32Epoch1601 = LocalDateTime.of(1601, Month.JANUARY, 1, 0, 0);
        Instant instant = time32Epoch1601.atZone(ZoneOffset.UTC).toInstant();
        DATETIME_EPOCH_DIFF_1601 = (instant.toEpochMilli() - Instant.EPOCH.toEpochMilli()) / 1000;
    }

    private final Set<String> authors = new HashSet<>();
    private final Set<String> mostRecentAuthors = new HashSet<>();
    private final Set<String> originalAuthors = new HashSet<>();
    public StorageIndexDataElementData storageIndex;
    public StorageManifestDataElementData storageManifest;
    public CellManifestDataElementData headerCellCellManifest;
    public RevisionManifestDataElementData headerCellRevisionManifest;
    public List<RevisionManifestDataElementData> revisionManifests;
    public List<CellManifestDataElementData> cellManifests;
    public HeaderCell headerCell;
    public List<RevisionStoreObjectGroup> dataRoot;
    public List<RevisionStoreObjectGroup> OtherFileNodeList;
    private boolean mostRecentAuthorProp = false;
    private boolean originalAuthorProp = false;
    private Instant lastModifiedTimestamp = Instant.MIN;
    private long creationTimestamp = Long.MAX_VALUE;
    private long lastModified = Long.MIN_VALUE;

    public MSOneStorePackage() {
        this.revisionManifests = new ArrayList<>();
        this.cellManifests = new ArrayList<>();
        this.OtherFileNodeList = new ArrayList<>();
    }

    /**
     * This method is used to find the Storage Index Cell Mapping matches the Cell ID.
     *
     * @param cellID Specify the Cell ID.
     * @return Return the specific Storage Index Cell Mapping.
     */
    public StorageIndexCellMapping findStorageIndexCellMapping(CellID cellID) {
        StorageIndexCellMapping storageIndexCellMapping = null;
        if (this.storageIndex != null) {
            storageIndexCellMapping = this.storageIndex.storageIndexCellMappingList.stream()
                    .filter(s -> s.cellID.equals(cellID)).findFirst()
                    .orElse(new StorageIndexCellMapping());
        }
        return storageIndexCellMapping;
    }

    /**
     * This method is used to find the Storage Index Revision Mapping that matches the Revision Mapping Extended GUID.
     *
     * @param revisionExtendedGUID Specify the Revision Mapping Extended GUID.
     * @return Return the instance of Storage Index Revision Mapping.
     */
    public StorageIndexRevisionMapping findStorageIndexRevisionMapping(
            ExGuid revisionExtendedGUID) {
        StorageIndexRevisionMapping instance = null;
        if (this.storageIndex != null) {
            instance = this.storageIndex.storageIndexRevisionMappingList.stream()
                    .filter(r -> r.revisionExGuid.equals(revisionExtendedGUID)).findFirst()
                    .orElse(new StorageIndexRevisionMapping());
        }

        return instance;
    }

    /**
     * Is this property a binary property?
     *
     * @param property The property.
     * @return Is it binary?
     */
    private boolean propertyIsBinary(OneNotePropertyEnum property) {
        return property == OneNotePropertyEnum.RgOutlineIndentDistance ||
                property == OneNotePropertyEnum.NotebookManagementEntityGuid ||
                property == OneNotePropertyEnum.RichEditTextUnicode;
    }

    public void walkTree(OneNoteTreeWalkerOptions options, Metadata metadata,
                         XHTMLContentHandler xhtml)
            throws SAXException, TikaException, IOException {
        for (RevisionStoreObjectGroup revisionStoreObjectGroup : OtherFileNodeList) {
            for (RevisionStoreObject revisionStoreObject : revisionStoreObjectGroup.objects) {
                PropertySet propertySet =
                        revisionStoreObject.propertySet.objectSpaceObjectPropSet.body;
                for (int i = 0; i < propertySet.rgData.size(); ++i) {
                    IProperty property = propertySet.rgData.get(i);
                    PropertyID propertyID = propertySet.rgPrids[i];
                    PropertyType propertyType = PropertyType.fromIntVal(propertyID.type);
                    OneNotePropertyEnum oneNotePropertyEnum =
                            OneNotePropertyEnum.of(Unsigned.uint(propertyID.value).longValue());
                    if (oneNotePropertyEnum == OneNotePropertyEnum.LastModifiedTimeStamp) {
                        long fullval = getScalar(property);
                        Instant instant = Instant.ofEpochSecond(
                                fullval / 10000000 + DATETIME_EPOCH_DIFF_1601);
                        if (instant.isAfter(lastModifiedTimestamp)) {
                            lastModifiedTimestamp = instant;
                        }
                        metadata.set(ONE_NOTE_PREFIX + "lastModifiedTimestamp",
                                String.valueOf(lastModifiedTimestamp.toEpochMilli()));
                    } else if (oneNotePropertyEnum == OneNotePropertyEnum.CreationTimeStamp) {
                        // add the TIME32_EPOCH_DIFF_1980 because OneNote TIME32 epoch time is per 1980, not
                        // 1970
                        long scalar = getScalar(property);
                        long creationTs = scalar + TIME32_EPOCH_DIFF_1980;
                        if (creationTs < creationTimestamp) {
                            creationTimestamp = creationTs;
                        }
                        metadata.set(ONE_NOTE_PREFIX + "creationTimestamp", String.valueOf(creationTimestamp));
                    } else if (oneNotePropertyEnum == OneNotePropertyEnum.LastModifiedTime) {
                        // add the TIME32_EPOCH_DIFF_1980 because OneNote TIME32 epoch time is per 1980, not
                        // 1970
                        long scalar = getScalar(property);
                        long lastMod = scalar + TIME32_EPOCH_DIFF_1980;
                        if (lastMod > lastModified) {
                            lastModified = lastMod;
                        }
                        metadata.set(TikaCoreProperties.MODIFIED, String.valueOf(lastModified));
                    } else if (oneNotePropertyEnum == OneNotePropertyEnum.Author) {
                        String author =
                                new String(((PrtFourBytesOfLengthFollowedByData) property).data,
                                        StandardCharsets.UTF_8);
                        if (mostRecentAuthorProp) {
                            mostRecentAuthors.add(author);
                        } else if (originalAuthorProp) {
                            originalAuthors.add(author);
                        } else {
                            authors.add(author);
                        }
                    } else if (oneNotePropertyEnum == OneNotePropertyEnum.AuthorMostRecent) {
                        mostRecentAuthorProp = true;
                    } else if (oneNotePropertyEnum == OneNotePropertyEnum.AuthorOriginal) {
                        originalAuthorProp = true;
                    } else if (propertyType == PropertyType.FourBytesOfLengthFollowedByData) {
                        boolean isBinary = propertyIsBinary(oneNotePropertyEnum);
                        PrtFourBytesOfLengthFollowedByData dataProperty =
                                (PrtFourBytesOfLengthFollowedByData) property;
                        if ((dataProperty.data.length & 1) == 0 &&
                                oneNotePropertyEnum != OneNotePropertyEnum.TextExtendedAscii &&
                                !isBinary) {
                            if (options.getUtf16PropertiesToPrint().contains(oneNotePropertyEnum)) {
                                xhtml.startElement(P);
                                xhtml.characters(
                                        new String(dataProperty.data, StandardCharsets.UTF_16LE));
                                xhtml.endElement(P);
                            }
                        } else if (oneNotePropertyEnum == OneNotePropertyEnum.TextExtendedAscii) {
                            xhtml.startElement(P);
                            xhtml.characters(
                                    new String(dataProperty.data, StandardCharsets.US_ASCII));
                            xhtml.endElement(P);
                        } else if (!isBinary) {
                            if (options.getUtf16PropertiesToPrint().contains(oneNotePropertyEnum)) {
                                xhtml.startElement(P);
                                xhtml.characters(
                                        new String(dataProperty.data, StandardCharsets.UTF_16LE));
                                xhtml.endElement(P);
                            }
                        } else {
                            if (oneNotePropertyEnum == OneNotePropertyEnum.RichEditTextUnicode) {
                                handleRichEditTextUnicode(dataProperty.data, xhtml);
                            } else {
                                //TODO -- these seem to be somewhat broken font files and other
                                //odds and ends...what are they and how should we process them?
                                //handleEmbedded(content.size());
                            }
                        }
                    }
                }
            }
        }
        if (!authors.isEmpty()) {
            metadata.set(TikaCoreProperties.CREATOR, authors.toArray(new String[]{}));
        }
        if (!mostRecentAuthors.isEmpty()) {
            metadata.set(Property.externalTextBag(ONE_NOTE_PREFIX + "mostRecentAuthors"),
                    mostRecentAuthors.toArray(new String[]{}));
        }
        if (!originalAuthors.isEmpty()) {
            metadata.set(Property.externalTextBag(ONE_NOTE_PREFIX + "originalAuthors"),
                    originalAuthors.toArray(new String[]{}));
        }
    }


    private void handleRichEditTextUnicode(byte[] arr, XHTMLContentHandler xhtml)
            throws SAXException, IOException, TikaException {
        // look for the first null
        int firstNull = 0;
        for (int i = 0; i < arr.length - 1; i += 2) {
            if (arr[i] == 0 && arr[i + 1] == 0) {
                firstNull = Math.max(i, 0);
                break;
            }
        }

        if (firstNull == 0) {
            return;
        }
        String txt = new String(arr, 0, firstNull, StandardCharsets.UTF_16LE);
        Matcher m = HYPERLINK_PATTERN.matcher(txt);
        if (m.find()) {
            xhtml.startElement("a", "href", m.group(1));
            xhtml.characters(m.group(2));
            xhtml.endElement("a");
        } else {
            xhtml.startElement(P);
            xhtml.characters(txt);
            xhtml.endElement(P);
        }
    }

    private long getScalar(IProperty property) throws TikaException, IOException {
        if (property instanceof FourBytesOfData) {
            FourBytesOfData fourBytesOfDataProp = (FourBytesOfData) property;
            return BitConverter.toUInt32(fourBytesOfDataProp.data, 0);
        } else if (property instanceof EightBytesOfData) {
            EightBytesOfData fourBytesOfDataProp = (EightBytesOfData) property;
            return BitConverter.toInt64(fourBytesOfDataProp.data, 0);
        }
        throw new TikaException("Could not parse scalar of type " + property.getClass());
    }
}
