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

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
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
import org.apache.tika.sax.EmbeddedContentHandler;
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
 * The resource section is read lazily and only as far as the icon data
 * reaches: a file without icons costs little more than its resource
 * directory. File-backed input is read through a positioned channel,
 * anything else by skipping forward, so non-seekable input works too.
 * Hostile input is contained by bounds checking every offset, capping the
 * section size, the number of directory entries visited, the icons per group,
 * the size of a rebuilt icon and the size of all rebuilt icons together, and
 * by refusing cycles in the tree.
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
    private static final int MAX_DIRECTORY_ENTRIES = 20000; // visited across the whole tree
    private static final int MAX_RESOURCE_NAME_LENGTH = 256;
    private static final int MAX_ICONS_PER_GROUP = 256;
    // A genuine icon never outgrows the section it came from
    private static final long MAX_ICO_SIZE = MAX_RESOURCE_SECTION_SIZE;
    // Groups may share images, so all icons together may outgrow the section read, but not by much
    private static final int MAX_OUTPUT_FACTOR = 4;
    private static final int SECTION_BUFFER_FLOOR = 8192;

    private PEIconExtractor() {
    }

    /**
     * Continues reading the PE file directly after the COFF file header and
     * emits every icon group as an embedded document.
     *
     * @param stream      the input positioned right after the 24 byte COFF header
     * @param sizeOptHdrs the SizeOfOptionalHeader field of the COFF header
     * @param numSections the NumberOfSections field of the COFF header
     * @param metadata    the PE file's own metadata, receives a warning if the
     *                    resource tree was too large to walk completely or
     *                    its icons too large to rebuild them all
     */
    static void extract(TikaInputStream stream, int sizeOptHdrs, int numSections,
                        XHTMLContentHandler xhtml, Metadata metadata, ParseContext context)
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
        long numDataDirs = EndianUtils.getUIntLE(optHdr, dataDirOffset - 4);
        if (numDataDirs <= IMAGE_DIRECTORY_ENTRY_RESOURCE) {
            return;
        }
        long rsrcRva = EndianUtils.getUIntLE(optHdr, rsrcEntry);
        long rsrcSize = EndianUtils.getUIntLE(optHdr, rsrcEntry + 4);
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
            long va = EndianUtils.getUIntLE(sections, off + 12);
            long rawSize = EndianUtils.getUIntLE(sections, off + 16);
            long rawPtr = EndianUtils.getUIntLE(sections, off + 20);
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

        InputStream source;
        if (stream.hasFile()) {
            FileChannel channel = stream.getFileChannel();
            if (sectionRawPtr >= channel.size()) {
                return;
            }
            channel.position(sectionRawPtr);
            source = Channels.newInputStream(channel);
        } else {
            // Everything read so far: DOS header up to and including the section table
            long position = stream.getPosition();
            if (sectionRawPtr < position) {
                return;
            }
            IOUtils.skipFully(stream, sectionRawPtr - position);
            source = stream;
        }

        // Offsets inside the resource tree are relative to its root, which
        // normally but not necessarily sits at the start of the section
        Resources resources = new Resources(new Section(source, (int) sectionRawSize), sectionVa,
                rsrcRva - sectionVa);
        readDirectory(resources, resources.rootOffset, 0, new HashSet<>(), 0, 0, null);
        if (resources.budget < 0) {
            EmbeddedDocumentUtil.recordException(new TikaException(
                    "PE resource directory has more than " + MAX_DIRECTORY_ENTRIES +
                            " entries; icon extraction stopped early"), metadata, context);
        }
        emitIcons(resources, xhtml, metadata, context);
    }

    /**
     * Walks the three level resource tree (type / name / language) and
     * collects every icon and icon group. {@code type}, {@code id} and
     * {@code name} carry what the levels above have established.
     */
    private static void readDirectory(Resources resources, long dirOffset, int depth,
                                      Set<Long> visited, int type, int id, String name)
            throws IOException {
        if (depth >= MAX_RESOURCE_TREE_DEPTH || !visited.add(dirOffset)) {
            return;
        }
        Section section = resources.section;
        int dir = section.index(dirOffset, RESOURCE_DIRECTORY_SIZE);
        if (dir < 0) {
            return;
        }
        int numEntries = EndianUtils.getUShortLE(section.buf, dir + 12) +
                EndianUtils.getUShortLE(section.buf, dir + 14);
        for (int i = 0; i < numEntries; i++) {
            if (--resources.budget < 0) {
                return;
            }
            int entry = section.index(dirOffset + RESOURCE_DIRECTORY_SIZE +
                    (long) i * RESOURCE_DIRECTORY_ENTRY_SIZE, RESOURCE_DIRECTORY_ENTRY_SIZE);
            if (entry < 0) {
                return;
            }
            long nameField = EndianUtils.getUIntLE(section.buf, entry);
            long dataField = EndianUtils.getUIntLE(section.buf, entry + 4);

            if (depth == 0) {
                // Only icons are interesting, and a well-formed tree lists each type once
                if (nameField != RT_ICON && nameField != RT_GROUP_ICON ||
                        !resources.typesSeen.add((int) nameField)) {
                    continue;
                }
            }
            boolean named = (nameField & HIGH_BIT) != 0;
            String entryName = null;
            if (named) {
                entryName = readName(section, resources.rootOffset + (nameField & ~HIGH_BIT));
                if (entryName == null) {
                    continue;
                }
            }
            int entryId = (int) (nameField & 0xffff);

            if ((dataField & HIGH_BIT) != 0) {
                long subdir = resources.rootOffset + (dataField & ~HIGH_BIT);
                if (depth == 0) {
                    readDirectory(resources, subdir, 1, visited, entryId, 0, null);
                } else if (depth == 1) {
                    readDirectory(resources, subdir, 2, visited, type, entryId, entryName);
                }
                // a subdirectory below the language level is malformed; nothing to find there
            } else if (depth == MAX_RESOURCE_TREE_DEPTH - 1 && !named) {
                // Language ids are always numeric
                readDataEntry(resources, resources.rootOffset + dataField,
                        new Resource(type, id, name, entryId));
            }
        }
    }

    private static void readDataEntry(Resources resources, long offset, Resource resource)
            throws IOException {
        Section section = resources.section;
        int entry = section.index(offset, RESOURCE_DATA_ENTRY_SIZE);
        if (entry < 0) {
            return;
        }
        long dataRva = EndianUtils.getUIntLE(section.buf, entry);
        long size = EndianUtils.getUIntLE(section.buf, entry + 4);
        // Resource data normally lives in the same section as the tree; if
        // it doesn't we can't reach it with a forward-only read
        int dataIdx = section.index(dataRva - resources.sectionVa, size);
        if (dataIdx < 0) {
            return;
        }
        resource.offset = dataIdx;
        resource.size = (int) size;
        if (resource.type == RT_ICON) {
            // Groups reference icons by numeric id, so a named icon is unreachable
            if (resource.name == null) {
                resources.icons.computeIfAbsent(resource.id, k -> new LinkedHashMap<>())
                        .putIfAbsent(resource.language, resource);
            }
        } else {
            resources.groups.add(resource);
        }
    }

    private static String readName(Section section, long offset) throws IOException {
        int idx = section.index(offset, 2);
        if (idx < 0) {
            return null;
        }
        int length = EndianUtils.getUShortLE(section.buf, idx);
        if (length == 0 || length > MAX_RESOURCE_NAME_LENGTH ||
                section.index(offset + 2, (long) length * 2) < 0) {
            return null;
        }
        return new String(section.buf, idx + 2, length * 2, StandardCharsets.UTF_16LE);
    }

    /**
     * Rebuilds an <code>.ico</code> file for every icon group and passes it on
     * as an embedded document. The first usable group in resource order is the
     * one Windows shows for the file itself. The icon adds nothing to the text
     * output; its name is Tika's invention, not content of the file.
     */
    private static void emitIcons(Resources resources, XHTMLContentHandler xhtml,
                                  Metadata parentMetadata, ParseContext context)
            throws IOException, SAXException {
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
        long budget = (long) MAX_OUTPUT_FACTOR * resources.section.length;
        boolean first = true;
        for (Resource group : resources.groups) {
            List<Resource> images = resolveGroup(group, resources);
            if (images == null) {
                continue;
            }
            String name = "icon_" + group.displayName();
            if (languagesPerGroup.get(group.displayName()) > 1) {
                name += "_" + group.language;
            }
            Metadata metadata = Metadata.newInstance(context);
            metadata.set(TikaCoreProperties.RESOURCE_NAME_KEY, name + ".ico");
            metadata.set(TikaCoreProperties.RESOURCE_NAME_EXTENSION_INFERRED, true);
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
            budget -= icoSize(images);
            if (budget < 0) {
                EmbeddedDocumentUtil.recordException(new TikaException(
                        "PE icons add up to more than " + MAX_OUTPUT_FACTOR +
                                " times the resource section they were read from; icon" +
                                " extraction stopped early"), parentMetadata, context);
                return;
            }
            try (TikaInputStream tis = TikaInputStream.get(buildIco(group, images, resources))) {
                extractor.parseEmbedded(tis, new EmbeddedContentHandler(xhtml), metadata, context,
                        false);
            }
        }
    }

    /**
     * Checks a {@code GRPICONDIR} and looks up the images it references.
     *
     * @return the images in directory order, or null if the group is unusable
     */
    private static List<Resource> resolveGroup(Resource group, Resources resources) {
        byte[] rsrc = resources.section.buf;
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
        Set<Long> seen = new HashSet<>();
        for (int i = 0; i < count; i++) {
            int id = EndianUtils.getUShortLE(rsrc, g + 6 + i * GRP_ICON_DIR_ENTRY_SIZE + 12);
            Resource image = resources.findIcon(id, group.language);
            // The same image twice, under one id or several, would let a tiny
            // group inflate into a huge file
            if (image == null || !seen.add((long) image.offset << 32 | image.size)) {
                return null;
            }
            images.add(image);
        }
        return icoSize(images) > MAX_ICO_SIZE ? null : images;
    }

    private static long icoSize(List<Resource> images) {
        long size = 6 + (long) images.size() * ICON_DIR_ENTRY_SIZE;
        for (Resource image : images) {
            size += image.size;
        }
        return size;
    }

    /**
     * Converts a {@code GRPICONDIR} plus its {@code RT_ICON} images into an
     * {@code ICONDIR} based <code>.ico</code> file.
     */
    private static byte[] buildIco(Resource group, List<Resource> images, Resources resources) {
        byte[] rsrc = resources.section.buf;
        int count = images.size();
        int imageOffset = 6 + count * ICON_DIR_ENTRY_SIZE;
        ByteBuffer ico = ByteBuffer.allocate((int) icoSize(images)).order(ByteOrder.LITTLE_ENDIAN);
        // ICONDIR: reserved, type, count - identical to the GRPICONDIR
        ico.put(rsrc, group.offset, 6);
        for (int i = 0; i < count; i++) {
            Resource image = images.get(i);
            // width, height, colours, reserved, planes and bit count are shared
            ico.put(rsrc, group.offset + 6 + i * GRP_ICON_DIR_ENTRY_SIZE, 8);
            // the group's BytesInRes may disagree with the actual resource; trust the resource
            ico.putInt(image.size);
            ico.putInt(imageOffset);
            imageOffset += image.size;
        }
        for (Resource image : images) {
            ico.put(rsrc, image.offset, image.size);
        }
        return ico.array();
    }

    /**
     * The raw bytes of the resource section, pulled from the source only as
     * far as the tree walk needs them. The buffer grows with the bytes that
     * arrive, never with a size the file merely declares.
     */
    static final class Section {
        private final InputStream source;
        private final int declaredSize;
        byte[] buf = new byte[0];
        private int length;
        private boolean eof;

        Section(InputStream source, int declaredSize) {
            this.source = source;
            this.declaredSize = declaredSize;
        }

        /**
         * @return the index of {@code offset}, or -1 if {@code length} bytes
         * starting there are not available. Reads more of the section if needed,
         * which may replace {@link #buf}.
         */
        int index(long offset, long length) throws IOException {
            if (offset < 0 || length < 0 || offset + length > declaredSize) {
                return -1;
            }
            return ensure((int) (offset + length)) ? (int) offset : -1;
        }

        private boolean ensure(int end) throws IOException {
            while (length < end && !eof) {
                if (length == buf.length) {
                    buf = Arrays.copyOf(buf, (int) Math.min(declaredSize,
                            Math.max(SECTION_BUFFER_FLOOR, 2L * buf.length)));
                }
                int n = source.read(buf, length, Math.min(end, buf.length) - length);
                if (n < 0) {
                    eof = true;
                } else {
                    length += n;
                }
            }
            return end <= length;
        }
    }

    /**
     * A leaf of the resource tree: type, name (or id) and language, plus the
     * location of its data within the resource section.
     */
    private static final class Resource {
        final int type;
        final int id;
        final String name;
        final int language;
        int offset;
        int size;

        Resource(int type, int id, String name, int language) {
            this.type = type;
            this.id = id;
            this.name = name;
            this.language = language;
        }

        String displayName() {
            return name != null ? name : Integer.toString(id);
        }
    }

    private static final class Resources {
        final Section section;
        final long sectionVa;
        final long rootOffset;
        final Set<Integer> typesSeen = new HashSet<>();
        // icon id -> language -> icon, in directory order
        final Map<Integer, Map<Integer, Resource>> icons = new HashMap<>();
        final List<Resource> groups = new ArrayList<>();
        int budget = MAX_DIRECTORY_ENTRIES;

        Resources(Section section, long sectionVa, long rootOffset) {
            this.section = section;
            this.sectionVa = sectionVa;
            this.rootOffset = rootOffset;
        }

        /**
         * @return the icon with the given id, preferring the group's language
         */
        Resource findIcon(int id, int language) {
            Map<Integer, Resource> byLanguage = icons.get(id);
            if (byLanguage == null) {
                return null;
            }
            Resource icon = byLanguage.get(language);
            return icon != null ? icon : byLanguage.values().iterator().next();
        }
    }
}
