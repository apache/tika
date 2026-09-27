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
package org.apache.tika.parser.executable;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.io.IOUtils;
import org.xml.sax.SAXException;

import org.apache.tika.exception.TikaException;
import org.apache.tika.extractor.EmbeddedDocumentExtractor;
import org.apache.tika.extractor.EmbeddedDocumentUtil;
import org.apache.tika.io.EndianUtils;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.HttpHeaders;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.XHTMLContentHandler;

/**
 * Extracts the icons of a PE file (EXE/DLL) from its resource section and
 * hands each icon group to the {@link EmbeddedDocumentExtractor} as a
 * standalone <code>.ico</code> file.
 * <p>
 * Windows stores an icon as a group resource ({@code RT_GROUP_ICON}) that
 * lists the individual images, which are stored as {@code RT_ICON} resources.
 * The {@code .ico} file format is nearly identical to the group resource;
 * the only difference is that the group refers to its images by resource id
 * whereas the file refers to them by file offset. This class rebuilds the
 * file from the two resource types.
 * <p>
 * The extractor reads the stream strictly forward, so it works on
 * non-seekable input. It is written to survive truncated or malicious
 * files: every offset is bounds checked, the resource tree depth and
 * entry count are capped and cycles in the tree are detected.
 */
class PEIconExtractor {

    static final String ICON_MIME_TYPE = "image/vnd.microsoft.icon";

    private static final int RT_ICON = 3;
    private static final int RT_GROUP_ICON = 14;

    private static final int IMAGE_DIRECTORY_ENTRY_RESOURCE = 2;
    private static final int PE32_MAGIC = 0x10b;
    private static final int PE32PLUS_MAGIC = 0x20b;
    private static final int SECTION_HEADER_SIZE = 40;
    private static final int RESOURCE_DIRECTORY_SIZE = 16;
    private static final int RESOURCE_DIRECTORY_ENTRY_SIZE = 8;
    private static final int RESOURCE_DATA_ENTRY_SIZE = 16;
    private static final int GRP_ICON_DIR_ENTRY_SIZE = 14;
    private static final int ICON_DIR_ENTRY_SIZE = 16;
    private static final long HIGH_BIT = 0x80000000L;

    // Sanity limits for hostile input
    private static final int MAX_SECTIONS = 96; // the PE spec's own limit
    private static final int MAX_RESOURCE_SECTION_SIZE = 64 * 1024 * 1024;
    private static final int MAX_RESOURCE_TREE_DEPTH = 3; // type / name / language
    private static final int MAX_RESOURCES = 10000;
    private static final int MAX_RESOURCE_NAME_LENGTH = 256;
    private static final int MAX_ICONS_PER_GROUP = 256;

    private PEIconExtractor() {
    }

    /**
     * Continues reading the PE file directly after the COFF file header and
     * emits every icon group as an embedded document.
     *
     * @param stream      the input positioned right after the 24 byte COFF header
     * @param sizeOptHdrs the SizeOfOptionalHeader field of the COFF header
     * @param numSections the NumberOfSections field of the COFF header
     */
    static void extract(TikaInputStream stream, int sizeOptHdrs, int numSections,
                        XHTMLContentHandler xhtml, ParseContext context)
            throws IOException, SAXException, TikaException {
        if (numSections <= 0 || numSections > MAX_SECTIONS) {
            return;
        }
        // The optional header holds the data directories, of which we
        // need the one pointing at the resource tree
        byte[] optHdr = new byte[sizeOptHdrs];
        IOUtils.readFully(stream, optHdr);
        int dataDirOffset;
        switch (sizeOptHdrs >= 2 ? EndianUtils.getUShortLE(optHdr, 0) : 0) {
            case PE32_MAGIC:
                dataDirOffset = 96;
                break;
            case PE32PLUS_MAGIC:
                dataDirOffset = 112;
                break;
            default:
                return;
        }
        int rsrcEntry = dataDirOffset + IMAGE_DIRECTORY_ENTRY_RESOURCE * 8;
        if (rsrcEntry + 8 > sizeOptHdrs) {
            return;
        }
        long numDataDirs = getUIntLE(optHdr, dataDirOffset - 4);
        if (numDataDirs <= IMAGE_DIRECTORY_ENTRY_RESOURCE) {
            return;
        }
        long rsrcRva = getUIntLE(optHdr, rsrcEntry);
        long rsrcSize = getUIntLE(optHdr, rsrcEntry + 4);
        if (rsrcRva == 0 || rsrcSize == 0) {
            return;
        }

        // The section table tells us where in the file the resource RVA lives
        byte[] sections = new byte[numSections * SECTION_HEADER_SIZE];
        IOUtils.readFully(stream, sections);
        long sectionVa = -1;
        long sectionRawPtr = -1;
        long sectionRawSize = -1;
        for (int i = 0; i < numSections; i++) {
            int off = i * SECTION_HEADER_SIZE;
            long va = getUIntLE(sections, off + 12);
            long rawSize = getUIntLE(sections, off + 16);
            long rawPtr = getUIntLE(sections, off + 20);
            if (rsrcRva >= va && rsrcRva < va + rawSize) {
                sectionVa = va;
                sectionRawPtr = rawPtr;
                sectionRawSize = rawSize;
                break;
            }
        }
        if (sectionVa < 0 || sectionRawSize > MAX_RESOURCE_SECTION_SIZE) {
            return;
        }

        // Everything read so far: DOS header up to and including the section table
        long position = stream.getPosition();
        if (sectionRawPtr < position) {
            return;
        }
        IOUtils.skipFully(stream, sectionRawPtr - position);
        // A truncated file simply yields a shorter section; the bounds checks
        // below deal with that
        byte[] rsrc = new byte[(int) sectionRawSize];
        int read = IOUtils.read(stream, rsrc);
        if (read < rsrc.length) {
            rsrc = Arrays.copyOf(rsrc, read);
        }

        Resources resources = new Resources(rsrc, sectionVa);
        readDirectory(resources, rsrcRva - sectionVa, 0, new HashSet<>(), null);
        emitIcons(resources, xhtml, context);
    }

    /**
     * Walks the three level resource tree (type / name / language) and
     * collects every icon and icon group.
     *
     * @param parent the entry that led to this directory, or null for the root
     */
    private static void readDirectory(Resources resources, long dirOffset, int depth,
                                      Set<Long> visited, Resource parent) throws TikaException {
        if (depth >= MAX_RESOURCE_TREE_DEPTH || !visited.add(dirOffset)) {
            return;
        }
        byte[] rsrc = resources.rsrc;
        int dir = toIndex(dirOffset, RESOURCE_DIRECTORY_SIZE, rsrc);
        if (dir < 0) {
            return;
        }
        int numEntries = EndianUtils.getUShortLE(rsrc, dir + 12) +
                EndianUtils.getUShortLE(rsrc, dir + 14);
        for (int i = 0; i < numEntries; i++) {
            int entry = toIndex(dirOffset + RESOURCE_DIRECTORY_SIZE +
                    (long) i * RESOURCE_DIRECTORY_ENTRY_SIZE, RESOURCE_DIRECTORY_ENTRY_SIZE, rsrc);
            if (entry < 0) {
                return;
            }
            long nameField = getUIntLE(rsrc, entry);
            long dataField = getUIntLE(rsrc, entry + 4);

            // Only icons are interesting; prune everything else at the type level
            if (depth == 0 && nameField != RT_ICON && nameField != RT_GROUP_ICON) {
                continue;
            }
            Resource current = parent == null ? new Resource() : new Resource(parent);
            if ((nameField & HIGH_BIT) != 0) {
                String name = readName(rsrc, nameField & ~HIGH_BIT);
                if (name == null) {
                    continue;
                }
                current.setLevel(depth, 0, name);
            } else {
                current.setLevel(depth, (int) (nameField & 0xffff), null);
            }

            if ((dataField & HIGH_BIT) != 0) {
                readDirectory(resources, dataField & ~HIGH_BIT, depth + 1, visited, current);
            } else if (depth == MAX_RESOURCE_TREE_DEPTH - 1) {
                readDataEntry(resources, dataField, current);
            }
        }
    }

    private static void readDataEntry(Resources resources, long offset, Resource resource)
            throws TikaException {
        byte[] rsrc = resources.rsrc;
        int entry = toIndex(offset, RESOURCE_DATA_ENTRY_SIZE, rsrc);
        if (entry < 0) {
            return;
        }
        long dataRva = getUIntLE(rsrc, entry);
        long size = getUIntLE(rsrc, entry + 4);
        // Resource data normally lives in the same section as the tree; if
        // it doesn't we can't reach it with a forward-only read
        int dataIdx = toIndex(dataRva - resources.sectionVa, size, rsrc);
        if (dataIdx < 0) {
            return;
        }
        if (resources.count++ >= MAX_RESOURCES) {
            throw new TikaException("Too many resources in PE file");
        }
        resource.offset = dataIdx;
        resource.size = (int) size;
        if (resource.type == RT_ICON) {
            // named icons can't be referenced from a group, which uses numeric ids
            if (resource.name == null) {
                resources.icons.computeIfAbsent(resource.id, k -> new ArrayList<>()).add(resource);
            }
        } else {
            resources.groups.add(resource);
        }
    }

    private static String readName(byte[] rsrc, long offset) {
        int idx = toIndex(offset, 2, rsrc);
        if (idx < 0) {
            return null;
        }
        int length = EndianUtils.getUShortLE(rsrc, idx);
        if (length == 0 || length > MAX_RESOURCE_NAME_LENGTH ||
                toIndex(offset + 2, (long) length * 2, rsrc) < 0) {
            return null;
        }
        return new String(rsrc, idx + 2, length * 2, StandardCharsets.UTF_16LE);
    }

    /**
     * Rebuilds an <code>.ico</code> file for every icon group and passes it on
     * as an embedded document. The first group in resource order is the one
     * Windows shows for the file itself.
     */
    private static void emitIcons(Resources resources, XHTMLContentHandler xhtml,
                                  ParseContext context) throws IOException, SAXException {
        if (resources.groups.isEmpty()) {
            return;
        }
        EmbeddedDocumentExtractor extractor =
                EmbeddedDocumentUtil.getEmbeddedDocumentExtractor(context);
        // Count the language variants per group so that the names stay unique
        Map<String, Integer> languagesPerGroup = new HashMap<>();
        for (Resource group : resources.groups) {
            languagesPerGroup.merge(group.displayName(), 1, Integer::sum);
        }
        boolean first = true;
        for (Resource group : resources.groups) {
            byte[] ico = buildIco(group, resources);
            if (ico == null) {
                continue;
            }
            String name = "icon_" + group.displayName();
            if (languagesPerGroup.get(group.displayName()) > 1) {
                name += "_" + group.language;
            }
            Metadata metadata = new Metadata();
            metadata.set(TikaCoreProperties.RESOURCE_NAME_KEY, name + ".ico");
            metadata.set(HttpHeaders.CONTENT_TYPE, ICON_MIME_TYPE);
            metadata.set(TikaCoreProperties.EMBEDDED_RELATIONSHIP_ID,
                    RT_GROUP_ICON + "/" + group.displayName() + "/" + group.language);
            metadata.set(TikaCoreProperties.EMBEDDED_RESOURCE_TYPE, first ?
                    TikaCoreProperties.EmbeddedResourceType.THUMBNAIL.toString() :
                    TikaCoreProperties.EmbeddedResourceType.ATTACHMENT.toString());
            first = false;
            if (!extractor.shouldParseEmbedded(metadata, context)) {
                continue;
            }
            try (TikaInputStream tis = TikaInputStream.get(ico)) {
                extractor.parseEmbedded(tis, xhtml, metadata, context, true);
            }
        }
    }

    /**
     * Converts a {@code GRPICONDIR} plus its {@code RT_ICON} images into an
     * {@code ICONDIR} based <code>.ico</code> file.
     *
     * @return the file, or null if the group is unusable
     */
    private static byte[] buildIco(Resource group, Resources resources) {
        byte[] rsrc = resources.rsrc;
        int g = group.offset;
        if (group.size < 6 || EndianUtils.getUShortLE(rsrc, g) != 0 ||
                EndianUtils.getUShortLE(rsrc, g + 2) != 1) {
            return null;
        }
        int count = EndianUtils.getUShortLE(rsrc, g + 4);
        if (count == 0 || count > MAX_ICONS_PER_GROUP ||
                6 + count * GRP_ICON_DIR_ENTRY_SIZE > group.size) {
            return null;
        }
        List<Resource> images = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            int e = g + 6 + i * GRP_ICON_DIR_ENTRY_SIZE;
            int id = EndianUtils.getUShortLE(rsrc, e + 12);
            Resource image = resources.findIcon(id, group.language);
            if (image == null) {
                return null;
            }
            images.add(image);
        }

        ByteArrayOutputStream ico = new ByteArrayOutputStream();
        // ICONDIR: reserved, type, count - identical to the GRPICONDIR
        ico.write(rsrc, g, 6);
        int imageOffset = 6 + count * ICON_DIR_ENTRY_SIZE;
        for (int i = 0; i < count; i++) {
            int e = g + 6 + i * GRP_ICON_DIR_ENTRY_SIZE;
            Resource image = images.get(i);
            // width, height, colours, reserved, planes and bit count are shared
            ico.write(rsrc, e, 8);
            // the group's BytesInRes may disagree with the actual resource; trust the resource
            writeIntLE(ico, image.size);
            writeIntLE(ico, imageOffset);
            imageOffset += image.size;
        }
        for (Resource image : images) {
            ico.write(rsrc, image.offset, image.size);
        }
        return ico.toByteArray();
    }

    private static void writeIntLE(ByteArrayOutputStream out, int value) {
        out.write(value & 0xff);
        out.write((value >>> 8) & 0xff);
        out.write((value >>> 16) & 0xff);
        out.write((value >>> 24) & 0xff);
    }

    /**
     * @return the index into the array for the given offset, or -1 if
     * {@code length} bytes starting there don't fit in the array
     */
    private static int toIndex(long offset, long length, byte[] array) {
        if (offset < 0 || length < 0 || offset + length > array.length) {
            return -1;
        }
        return (int) offset;
    }

    /**
     * Unsigned 32 bit little endian read; the value is returned as a long so
     * that large offsets don't turn negative.
     */
    private static long getUIntLE(byte[] data, int offset) {
        return EndianUtils.getIntLE(data, offset) & 0xffffffffL;
    }

    /**
     * A leaf of the resource tree: type, name (or id) and language, plus the
     * location of its data within the resource section.
     */
    private static final class Resource {
        int type;
        int id;
        String name;
        int language;
        int offset;
        int size;

        Resource() {
        }

        Resource(Resource parent) {
            type = parent.type;
            id = parent.id;
            name = parent.name;
            language = parent.language;
        }

        void setLevel(int depth, int numericId, String stringName) {
            switch (depth) {
                case 0:
                    type = numericId;
                    break;
                case 1:
                    id = numericId;
                    name = stringName;
                    break;
                default:
                    language = numericId;
                    break;
            }
        }

        String displayName() {
            return name != null ? name : Integer.toString(id);
        }
    }

    private static final class Resources {
        final byte[] rsrc;
        final long sectionVa;
        final Map<Integer, List<Resource>> icons = new HashMap<>();
        final List<Resource> groups = new ArrayList<>();
        int count;

        Resources(byte[] rsrc, long sectionVa) {
            this.rsrc = rsrc;
            this.sectionVa = sectionVa;
        }

        /**
         * @return the icon with the given id, preferring the group's language
         */
        Resource findIcon(int id, int language) {
            List<Resource> candidates = icons.get(id);
            if (candidates == null) {
                return null;
            }
            for (Resource icon : candidates) {
                if (icon.language == language) {
                    return icon;
                }
            }
            return candidates.get(0);
        }
    }
}
