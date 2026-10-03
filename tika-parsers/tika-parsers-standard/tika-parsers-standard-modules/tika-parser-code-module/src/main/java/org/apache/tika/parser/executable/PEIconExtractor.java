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

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
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
 * File-backed input is read where the resource tree points, so only the
 * directories and the icons themselves are touched. Anything else has to be
 * read from the start: up to the resource section, then the section as far
 * as its icons reach.
 * Hostile input is contained by bounds checking every offset and by capping
 * the buffered part of a section, the number of directory entries visited,
 * the icons per group, the size of a rebuilt icon and the size of all
 * rebuilt icons together. The tree is walked to its three levels and no
 * deeper, so a cycle cannot loop.
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
    private static final int GRP_ICON_DIR_SIZE = 6;
    private static final int GRP_ICON_DIR_ENTRY_SIZE = 14;
    private static final int ICON_DIR_ENTRY_SIZE = 16;
    private static final long HIGH_BIT = 0x80000000L;

    // Sanity limits for hostile input
    private static final int MAX_SECTIONS = 96; // the PE spec's own limit
    private static final int MAX_BUFFERED_SECTION_MB = 64;
    private static final int MAX_DIRECTORY_ENTRIES = 20000; // visited across the whole tree
    private static final int MAX_RESOURCE_NAME_LENGTH = 256;
    private static final int MAX_ICONS_PER_GROUP = 256;
    private static final long MAX_ICO_SIZE = 64 * 1024 * 1024;
    // Groups may share images, so all icons together may outgrow the section, but not by much
    private static final int MAX_OUTPUT_FACTOR = 4;
    private static final int SECTION_BUFFER_FLOOR = 8192;

    private PEIconExtractor() {
    }

    /**
     * Continues reading the PE file directly after the COFF file header and
     * emits every icon group as an embedded document.
     *
     * @param stream      the input positioned right after the 24 byte COFF header
     * @param position    the offset of that position in the PE file
     * @param sizeOptHdrs the SizeOfOptionalHeader field of the COFF header
     * @param numSections the NumberOfSections field of the COFF header
     * @param metadata    the PE file's own metadata, receives a warning if not
     *                    all of the resource tree or of the icons could be handled
     */
    static void extract(TikaInputStream stream, long position, int sizeOptHdrs, int numSections,
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
        if (sectionVa < 0) {
            return;
        }

        Section section;
        if (stream.hasFile()) {
            section = Section.of(stream.getFileChannel(), sectionRawPtr, sectionRawSize);
        } else {
            position += optHdr.length + sections.length;
            if (sectionRawPtr < position) {
                return;
            }
            IOUtils.skipFully(stream, sectionRawPtr - position);
            section = Section.of(stream, sectionRawSize);
        }

        // Offsets inside the resource tree are relative to its root, which
        // normally but not necessarily sits at the start of the section
        Resources resources = new Resources(section, sectionVa, rsrcRva - sectionVa);
        readDirectory(resources, resources.rootOffset, 0, 0, 0, null);
        if (resources.budget < 0) {
            EmbeddedDocumentUtil.recordException(new TikaException(
                    "PE resource directory has more than " + MAX_DIRECTORY_ENTRIES +
                            " entries; icon extraction stopped early"), metadata, context);
        }
        emitIcons(resources, xhtml, metadata, context);
        if (section.beyondLimit) {
            EmbeddedDocumentUtil.recordException(new TikaException(
                    "PE resource section is larger than " + MAX_BUFFERED_SECTION_MB +
                            " MB and the input is not a file; icons beyond that were not" +
                            " extracted"), metadata, context);
        }
    }

    /**
     * Walks the three level resource tree (type / name / language) and
     * collects every icon and icon group. {@code type}, {@code id} and
     * {@code name} carry what the levels above have established.
     */
    private static void readDirectory(Resources resources, long dirOffset, int depth, int type,
                                      int id, String name) throws IOException {
        if (resources.budget < 0) {
            return;
        }
        Section section = resources.section;
        byte[] dir = section.read(dirOffset, RESOURCE_DIRECTORY_SIZE);
        if (dir == null) {
            return;
        }
        int numEntries = EndianUtils.getUShortLE(dir, 12) + EndianUtils.getUShortLE(dir, 14);
        // no more than the budget still lets us look at
        byte[] entries = section.read(dirOffset + RESOURCE_DIRECTORY_SIZE,
                Math.min(numEntries, resources.budget) * RESOURCE_DIRECTORY_ENTRY_SIZE);
        if (entries == null) {
            return;
        }
        long groupDir = -1;
        long iconDir = -1;
        for (int i = 0; i < numEntries; i++) {
            if (--resources.budget < 0) {
                return;
            }
            long nameField = EndianUtils.getUIntLE(entries, i * RESOURCE_DIRECTORY_ENTRY_SIZE);
            long dataField = EndianUtils.getUIntLE(entries, i * RESOURCE_DIRECTORY_ENTRY_SIZE + 4);
            boolean subdirectory = (dataField & HIGH_BIT) != 0;
            long target = resources.rootOffset + (dataField & ~HIGH_BIT);
            boolean named = (nameField & HIGH_BIT) != 0;
            int entryId = (int) (nameField & 0xffff);

            if (depth == 0) {
                // Only icons are interesting, and a well-formed tree lists each type once
                if (subdirectory && nameField == RT_GROUP_ICON && groupDir < 0) {
                    groupDir = target;
                } else if (subdirectory && nameField == RT_ICON && iconDir < 0) {
                    iconDir = target;
                }
            } else if (depth == 1) {
                String entryName = named ?
                        readName(section, resources.rootOffset + (nameField & ~HIGH_BIT)) : null;
                if (subdirectory && (!named || entryName != null)) {
                    readDirectory(resources, target, 2, type, entryId, entryName);
                }
            } else if (!subdirectory && !named) {
                // Language ids are always numeric, and a subdirectory below the
                // language level is malformed; nothing to find there
                readDataEntry(resources, target, new Resource(type, id, name, entryId));
            }
        }
        // Groups first: should the entry budget run out among the icons, the
        // groups whose icons were reached still come out
        if (groupDir >= 0) {
            readDirectory(resources, groupDir, 1, RT_GROUP_ICON, 0, null);
        }
        if (iconDir >= 0) {
            readDirectory(resources, iconDir, 1, RT_ICON, 0, null);
        }
    }

    private static void readDataEntry(Resources resources, long offset, Resource resource)
            throws IOException {
        byte[] entry = resources.section.read(offset, RESOURCE_DATA_ENTRY_SIZE);
        if (entry == null) {
            return;
        }
        // Resource data normally lives in the same section as the tree; data
        // elsewhere is out of reach
        resource.offset = EndianUtils.getUIntLE(entry, 0) - resources.sectionVa;
        resource.size = EndianUtils.getUIntLE(entry, 4);
        if (!resources.section.contains(resource.offset, resource.size)) {
            return;
        }
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

    /**
     * @return the name with path separators and control characters replaced:
     * it ends up in a file name and in an id whose parts are separated by slashes
     */
    private static String readName(Section section, long offset) throws IOException {
        byte[] prefix = section.read(offset, 2);
        if (prefix == null) {
            return null;
        }
        int length = EndianUtils.getUShortLE(prefix, 0);
        if (length == 0 || length > MAX_RESOURCE_NAME_LENGTH) {
            return null;
        }
        byte[] chars = section.read(offset + 2, length * 2);
        if (chars == null) {
            return null;
        }
        StringBuilder name = new StringBuilder(new String(chars, StandardCharsets.UTF_16LE));
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (c == '/' || c == '\\' || Character.isISOControl(c)) {
                name.setCharAt(i, '_');
            }
        }
        return name.toString();
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
        Set<String> relationshipIds = new HashSet<>();
        long emitted = 0;
        boolean first = true;
        for (Resource group : resources.groups) {
            Icon icon = resolveGroup(group, resources);
            String relationshipId =
                    RT_GROUP_ICON + "/" + group.displayName() + "/" + group.language;
            // A name can spell an id, or two names can clean up to the same one;
            // what cannot be told apart comes out once
            if (icon == null || !relationshipIds.add(relationshipId)) {
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
            metadata.set(TikaCoreProperties.EMBEDDED_RELATIONSHIP_ID, relationshipId);
            metadata.set(TikaCoreProperties.EMBEDDED_RESOURCE_TYPE, first ?
                    TikaCoreProperties.EmbeddedResourceType.THUMBNAIL.toString() :
                    TikaCoreProperties.EmbeddedResourceType.ATTACHMENT.toString());
            first = false;
            if (!extractor.shouldParseEmbedded(metadata, context)) {
                continue;
            }
            emitted += icoSize(icon.images);
            if (emitted > MAX_OUTPUT_FACTOR * resources.section.available()) {
                EmbeddedDocumentUtil.recordException(new TikaException(
                        "PE icons add up to more than " + MAX_OUTPUT_FACTOR +
                                " times the resource section they were read from; icon" +
                                " extraction stopped early"), parentMetadata, context);
                return;
            }
            byte[] ico = buildIco(icon, resources.section);
            if (ico == null) {
                continue;
            }
            try (TikaInputStream tis = TikaInputStream.get(ico)) {
                extractor.parseEmbedded(tis, new EmbeddedContentHandler(xhtml), metadata, context,
                        false);
            }
        }
    }

    /**
     * Checks a {@code GRPICONDIR} and looks up the images it references.
     *
     * @return the directory and its images in directory order, or null if the
     * group is unusable
     */
    private static Icon resolveGroup(Resource group, Resources resources) throws IOException {
        Section section = resources.section;
        byte[] dir = section.read(group.offset, (int) Math.min(group.size,
                GRP_ICON_DIR_SIZE + MAX_ICONS_PER_GROUP * GRP_ICON_DIR_ENTRY_SIZE));
        if (dir == null || dir.length < GRP_ICON_DIR_SIZE || EndianUtils.getUShortLE(dir, 0) != 0 ||
                EndianUtils.getUShortLE(dir, 2) != 1) {
            return null;
        }
        int count = EndianUtils.getUShortLE(dir, 4);
        if (count == 0 || count > MAX_ICONS_PER_GROUP ||
                GRP_ICON_DIR_SIZE + count * GRP_ICON_DIR_ENTRY_SIZE > group.size) {
            return null;
        }
        List<Resource> images = new ArrayList<>(count);
        Set<List<Long>> seen = new HashSet<>();
        for (int i = 0; i < count; i++) {
            int id = EndianUtils.getUShortLE(dir,
                    GRP_ICON_DIR_SIZE + i * GRP_ICON_DIR_ENTRY_SIZE + 12);
            Resource image = resources.findIcon(id, group.language);
            // The same image twice, under one id or several, would let a tiny
            // group inflate into a huge file
            if (image == null || !seen.add(List.of(image.offset, image.size))) {
                return null;
            }
            images.add(image);
        }
        if (icoSize(images) > MAX_ICO_SIZE) {
            return null;
        }
        for (Resource image : images) {
            if (!section.has(image.offset, image.size)) {
                return null;
            }
        }
        return new Icon(dir, images);
    }

    private static long icoSize(List<Resource> images) {
        long size = GRP_ICON_DIR_SIZE + (long) images.size() * ICON_DIR_ENTRY_SIZE;
        for (Resource image : images) {
            size += image.size;
        }
        return size;
    }

    /**
     * Converts a {@code GRPICONDIR} plus its {@code RT_ICON} images into an
     * {@code ICONDIR} based <code>.ico</code> file.
     *
     * @return the file, or null if an image could not be read after all
     */
    private static byte[] buildIco(Icon icon, Section section) throws IOException {
        int count = icon.images.size();
        int imageOffset = GRP_ICON_DIR_SIZE + count * ICON_DIR_ENTRY_SIZE;
        ByteBuffer ico = ByteBuffer.allocate((int) icoSize(icon.images))
                .order(ByteOrder.LITTLE_ENDIAN);
        // ICONDIR: reserved, type, count - identical to the GRPICONDIR
        ico.put(icon.dir, 0, GRP_ICON_DIR_SIZE);
        for (int i = 0; i < count; i++) {
            int size = (int) icon.images.get(i).size;
            // width, height, colours, reserved, planes and bit count are shared
            ico.put(icon.dir, GRP_ICON_DIR_SIZE + i * GRP_ICON_DIR_ENTRY_SIZE, 8);
            // the group's BytesInRes may disagree with the actual resource; trust the resource
            ico.putInt(size);
            ico.putInt(imageOffset);
            imageOffset += size;
        }
        for (Resource image : icon.images) {
            if (!section.read(image.offset, ico.array(), ico.position(), (int) image.size)) {
                return null;
            }
            ico.position(ico.position() + (int) image.size);
        }
        return ico.array();
    }

    /**
     * The resource section. A file is read where the walk points. Anything
     * else is buffered from the start of the section as far as the walk has
     * reached; the buffer grows with the bytes that arrive, never with a size
     * the file merely declares.
     */
    static final class Section {
        private final FileChannel channel;
        private final long start;
        private final InputStream source;
        private final long declaredSize;
        private final long limit;
        byte[] buf = new byte[0];
        private int length;
        private boolean eof;
        private boolean beyondLimit;

        private Section(FileChannel channel, long start, InputStream source, long declaredSize,
                        long limit) {
            this.channel = channel;
            this.start = start;
            this.source = source;
            this.declaredSize = declaredSize;
            this.limit = limit;
        }

        /**
         * @param start the file offset of the section
         */
        static Section of(FileChannel channel, long start, long declaredSize) throws IOException {
            long present = channel.size() - start;
            if (present < 0) {
                throw new EOFException("The file ends before its resource section");
            }
            return new Section(channel, start, null, declaredSize,
                    Math.min(declaredSize, present));
        }

        /**
         * @param source a stream positioned at the start of the section
         */
        static Section of(InputStream source, long declaredSize) {
            return new Section(null, 0, source, declaredSize,
                    Math.min(declaredSize, MAX_BUFFERED_SECTION_MB * 1024L * 1024L));
        }

        /**
         * @return whether the range lies inside the section as declared
         */
        boolean contains(long offset, long length) {
            return offset >= 0 && length >= 0 && offset + length <= declaredSize;
        }

        /**
         * @return the bytes of the section known to exist: in a file all that
         * are there, otherwise those read so far
         */
        long available() {
            return channel != null ? limit : length;
        }

        /**
         * @return whether the range can be read; buffered input is read that far
         */
        boolean has(long offset, long length) throws IOException {
            if (!contains(offset, length)) {
                return false;
            }
            if (offset + length > limit) {
                beyondLimit |= channel == null;
                return false;
            }
            return channel != null || ensure((int) (offset + length));
        }

        /**
         * @return false if the range cannot be read, see {@link #has(long, long)}
         */
        boolean read(long offset, byte[] target, int targetOffset, int length)
                throws IOException {
            if (!has(offset, length)) {
                return false;
            }
            if (channel == null) {
                System.arraycopy(buf, (int) offset, target, targetOffset, length);
                return true;
            }
            ByteBuffer buffer = ByteBuffer.wrap(target, targetOffset, length);
            long position = start + offset;
            while (buffer.hasRemaining()) {
                int n = channel.read(buffer, position);
                if (n < 0) {
                    return false;
                }
                position += n;
            }
            return true;
        }

        /**
         * @return the bytes, or null if the range cannot be read
         */
        byte[] read(long offset, int length) throws IOException {
            if (!has(offset, length)) {
                return null;
            }
            byte[] bytes = new byte[length];
            return read(offset, bytes, 0, length) ? bytes : null;
        }

        private boolean ensure(int end) throws IOException {
            while (length < end && !eof) {
                if (length == buf.length) {
                    buf = Arrays.copyOf(buf, (int) Math.min(limit,
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
        long offset;
        long size;

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

    /**
     * A usable icon group: its {@code GRPICONDIR} and the images it lists.
     */
    private record Icon(byte[] dir, List<Resource> images) {
    }

    private static final class Resources {
        final Section section;
        final long sectionVa;
        final long rootOffset;
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
