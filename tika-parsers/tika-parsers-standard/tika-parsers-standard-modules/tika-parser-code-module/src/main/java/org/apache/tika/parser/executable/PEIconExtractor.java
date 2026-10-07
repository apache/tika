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
import java.util.Comparator;
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
import org.apache.tika.parser.executable.ExecutableParser.CoffHeader;
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
    private static final byte[] PNG_SIGNATURE =
            {(byte) 0x89, 'P', 'N', 'G', 0x0d, 0x0a, 0x1a, 0x0a};
    private static final int PNG_IHDR = 0x49484452;
    /** the signature, the IHDR chunk's length and type, then its width and height */
    private static final int PNG_DIMENSIONS_END = 8 + 8 + 8;
    private static final int BITMAP_INFO_HEADER_SIZE = 40;
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
     * @param stream   the input positioned right after the COFF header
     * @param metadata the PE file's own metadata, receives a note if the
     *                 resource section is cut or broken and a warning if not
     *                 all of the tree or of the icons could be handled
     */
    static void extract(TikaInputStream stream, CoffHeader header, XHTMLContentHandler xhtml,
                        Metadata metadata, ParseContext context) throws IOException, SAXException {
        ResourceTree tree;
        try {
            tree = ResourceTree.read(stream, header);
        } catch (SecurityException e) {
            throw e;
        } catch (EOFException | RuntimeException e) {
            // A cut or broken resource section must not cost the caller the
            // header metadata; any other IOException is the source failing
            EmbeddedDocumentUtil.recordEmbeddedStreamException(e, metadata, context);
            return;
        }
        if (tree == null) {
            return;
        }
        if (tree.budgetExceeded) {
            EmbeddedDocumentUtil.recordException(new TikaException(
                    "PE resource directory has more than " + MAX_DIRECTORY_ENTRIES +
                            " entries; icon extraction stopped early"), metadata, context);
        }
        emitIcons(tree, xhtml, metadata, context);
        if (tree.section.beyondLimit()) {
            EmbeddedDocumentUtil.recordException(new TikaException(
                    "PE resource section is larger than " + MAX_BUFFERED_SECTION_MB +
                            " MB and the input is not a file; icons beyond that were not" +
                            " extracted"), metadata, context);
        }
    }

    /**
     * Rebuilds an <code>.ico</code> file for every icon group and passes it on
     * as an embedded document. The first usable group in resource order is the
     * one Windows shows for the file itself. The icon adds nothing to the text
     * output; its name is Tika's invention, not content of the file.
     */
    private static void emitIcons(ResourceTree tree, XHTMLContentHandler xhtml,
                                  Metadata parentMetadata, ParseContext context)
            throws IOException, SAXException {
        if (tree.groups.isEmpty()) {
            return;
        }
        EmbeddedDocumentExtractor extractor =
                EmbeddedDocumentUtil.getEmbeddedDocumentExtractor(context);
        // A group that comes in several languages carries the language in its name
        Map<String, Set<Integer>> languages = new HashMap<>();
        for (Resource group : tree.groups) {
            languages.computeIfAbsent(group.displayName(), k -> new HashSet<>())
                    .add(group.language);
        }
        Set<String> relationshipIds = new HashSet<>();
        long emitted = 0;
        boolean first = true;
        for (Resource group : tree.groups) {
            Icon icon = resolveGroup(group, tree);
            String relationshipId =
                    RT_GROUP_ICON + "/" + group.displayName() + "/" + group.language;
            // A name can spell an id, or two names can clean up to the same one;
            // what cannot be told apart comes out once
            if (icon == null || !relationshipIds.add(relationshipId)) {
                continue;
            }
            String name = "icon_" + group.displayName();
            if (languages.get(group.displayName()).size() > 1) {
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
            if (emitted > MAX_OUTPUT_FACTOR * tree.section.available()) {
                EmbeddedDocumentUtil.recordException(new TikaException(
                        "PE icons add up to more than " + MAX_OUTPUT_FACTOR +
                                " times the resource section they were read from; icon" +
                                " extraction stopped early"), parentMetadata, context);
                return;
            }
            byte[] ico = buildIco(icon, tree.section);
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
    private static Icon resolveGroup(Resource group, ResourceTree tree) throws IOException {
        Section section = tree.section;
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
        for (int i = 0; i < count; i++) {
            int id = EndianUtils.getUShortLE(dir, groupEntry(i) + 12);
            Resource image = tree.findIcon(id, group.language);
            if (image == null) {
                return null;
            }
            images.add(image);
        }
        if (shareBytes(images) || icoSize(images) > MAX_ICO_SIZE) {
            return null;
        }
        for (Resource image : images) {
            if (!section.canRead(image.offset, image.size)) {
                return null;
            }
        }
        return new Icon(dir, images);
    }

    /**
     * Images that share bytes, the same one listed twice being the plain
     * case, would let a tiny group inflate into a huge file.
     */
    private static boolean shareBytes(List<Resource> images) {
        List<Resource> sorted = new ArrayList<>(images);
        sorted.sort(Comparator.comparingLong(image -> image.offset));
        for (int i = 1; i < sorted.size(); i++) {
            Resource previous = sorted.get(i - 1);
            if (sorted.get(i).offset < previous.offset + previous.size) {
                return true;
            }
        }
        return false;
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
        List<Integer> order = largestFirst(icon, section);
        // ICONDIR: reserved, type, count - identical to the GRPICONDIR
        ico.put(icon.dir, 0, GRP_ICON_DIR_SIZE);
        for (int index : order) {
            int size = (int) icon.images.get(index).size;
            // width, height, colours, reserved, planes and bit count are shared
            ico.put(icon.dir, groupEntry(index), 8);
            // the group's BytesInRes may disagree with the actual resource; trust the resource
            ico.putInt(size);
            ico.putInt(imageOffset);
            imageOffset += size;
        }
        for (int index : order) {
            Resource image = icon.images.get(index);
            if (!section.read(image.offset, ico.array(), ico.position(), (int) image.size)) {
                return null;
            }
            ico.position(ico.position() + (int) image.size);
        }
        return ico.array();
    }

    /**
     * Orders a group's images, largest first. The order a resource compiler
     * wrote carries no meaning - Windows looks up the size it needs - but a
     * reader that treats an icon as a multi-page image hands out the first
     * entry, and for a single image out of an icon that should be the best one.
     *
     * @return indices into the group, by descending pixel count and colour depth
     */
    private static List<Integer> largestFirst(Icon icon, Section section) throws IOException {
        int count = icon.images.size();
        long[] pixels = new long[count];
        List<Integer> order = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            pixels[i] = pixelCount(icon, i, section);
            order.add(i);
        }
        // a stable sort, so images of one size keep the order the group has
        order.sort(Comparator.<Integer>comparingLong(index -> pixels[index])
                .thenComparingInt(icon::bitCount).reversed());
        return order;
    }

    /**
     * The pixels an image covers, read from the image itself: a directory entry
     * holds one byte per side and reads 0 for 256 or above, so a 1024 pixel PNG
     * and a 256 pixel bitmap are indistinguishable there.
     *
     * @return the pixel count, falling back to what the directory entry claims
     */
    private static long pixelCount(Icon icon, int index, Section section) throws IOException {
        Resource image = icon.images.get(index);
        byte[] head = section.read(image.offset,
                (int) Math.min(image.size, BITMAP_INFO_HEADER_SIZE));
        long width = 0;
        long height = 0;
        if (head != null && startsWithPngHeader(head)) {
            width = EndianUtils.getUIntBE(head, 16);
            height = EndianUtils.getUIntBE(head, 20);
        } else if (head != null && head.length >= BITMAP_INFO_HEADER_SIZE &&
                EndianUtils.getUIntLE(head, 0) == BITMAP_INFO_HEADER_SIZE) {
            width = EndianUtils.getIntLE(head, 4);
            // the height covers the XOR bitmap and the AND mask below it; a
            // negative one means the rows are stored top-down
            height = Math.abs((long) EndianUtils.getIntLE(head, 8)) / 2;
        }
        if (width <= 0 || height <= 0 || width > Integer.MAX_VALUE ||
                height > Integer.MAX_VALUE) {
            return icon.claimedPixelCount(index);
        }
        return width * height;
    }

    private static boolean startsWithPngHeader(byte[] head) {
        return head.length >= PNG_DIMENSIONS_END &&
                Arrays.equals(head, 0, PNG_SIGNATURE.length, PNG_SIGNATURE, 0,
                        PNG_SIGNATURE.length) &&
                EndianUtils.getIntBE(head, 12) == PNG_IHDR;
    }

    /**
     * @return the offset of an entry in a {@code GRPICONDIR}
     */
    private static int groupEntry(int index) {
        return GRP_ICON_DIR_SIZE + index * GRP_ICON_DIR_ENTRY_SIZE;
    }

    /**
     * The three level resource tree (type / name / language), reduced to its
     * icons and icon groups.
     */
    private static final class ResourceTree {
        final Section section;
        private final long sectionVa;
        private final long rootOffset;
        // icon id -> language -> icon, in directory order
        private final Map<Integer, Map<Integer, Resource>> icons = new HashMap<>();
        final List<Resource> groups = new ArrayList<>();
        private int budget = MAX_DIRECTORY_ENTRIES;
        boolean budgetExceeded;

        private ResourceTree(Section section, long sectionVa, long rootOffset) {
            this.section = section;
            this.sectionVa = sectionVa;
            this.rootOffset = rootOffset;
        }

        /**
         * Finds the resource section through the optional header and the
         * section table, which follow the COFF header, and walks its tree.
         *
         * @return the tree, or null if the file has no resource section
         */
        static ResourceTree read(TikaInputStream stream, CoffHeader header) throws IOException {
            if (header.numSections() <= 0 || header.numSections() > MAX_SECTIONS) {
                return null;
            }
            // The optional header holds the data directories, of which we
            // need the one pointing at the resource tree
            byte[] optHdr = new byte[header.sizeOptHdrs()];
            IOUtils.readFully(stream, optHdr);
            int dataDirOffset;
            switch (optHdr.length >= 2 ? EndianUtils.getUShortLE(optHdr, 0) : 0) {
                case PE32_MAGIC:
                    dataDirOffset = 96;
                    break;
                case PE32PLUS_MAGIC:
                    dataDirOffset = 112;
                    break;
                default:
                    return null;
            }
            int rsrcEntry = dataDirOffset + IMAGE_DIRECTORY_ENTRY_RESOURCE * 8;
            if (rsrcEntry + 8 > optHdr.length) {
                return null;
            }
            long numDataDirs = EndianUtils.getUIntLE(optHdr, dataDirOffset - 4);
            if (numDataDirs <= IMAGE_DIRECTORY_ENTRY_RESOURCE) {
                return null;
            }
            long rsrcRva = EndianUtils.getUIntLE(optHdr, rsrcEntry);
            long rsrcSize = EndianUtils.getUIntLE(optHdr, rsrcEntry + 4);
            if (rsrcRva == 0 || rsrcSize == 0) {
                return null;
            }

            // The section table tells us where in the file the resource RVA lives
            byte[] sections = new byte[header.numSections() * SECTION_HEADER_SIZE];
            IOUtils.readFully(stream, sections);
            long sectionVa = -1;
            long sectionRawPtr = -1;
            long sectionRawSize = -1;
            for (int off = 0; off < sections.length; off += SECTION_HEADER_SIZE) {
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
                return null;
            }

            Section section;
            if (stream.hasFile()) {
                section = new FileSection(stream.getFileChannel(), sectionRawPtr, sectionRawSize);
            } else {
                long position = header.end() + optHdr.length + sections.length;
                if (sectionRawPtr < position) {
                    return null;
                }
                IOUtils.skipFully(stream, sectionRawPtr - position);
                section = new BufferedSection(stream, sectionRawSize);
            }
            // Offsets inside the tree are relative to its root, which normally
            // but not necessarily sits at the start of the section
            ResourceTree tree = new ResourceTree(section, sectionVa, rsrcRva - sectionVa);
            tree.readTypes();
            return tree;
        }

        private void readTypes() throws IOException {
            long groupDir = -1;
            long iconDir = -1;
            for (Entry entry : readEntries(rootOffset)) {
                // Only icons are interesting, and a well-formed tree lists each type once
                if (entry.subdirectory && entry.nameField == RT_GROUP_ICON && groupDir < 0) {
                    groupDir = entry.target;
                } else if (entry.subdirectory && entry.nameField == RT_ICON && iconDir < 0) {
                    iconDir = entry.target;
                }
            }
            // Groups first: should the entry budget run out among the icons,
            // the groups whose icons were reached still come out
            if (groupDir >= 0) {
                readNames(groupDir, RT_GROUP_ICON);
            }
            if (iconDir >= 0) {
                readNames(iconDir, RT_ICON);
            }
        }

        private void readNames(long dirOffset, int type) throws IOException {
            for (Entry entry : readEntries(dirOffset)) {
                if (!entry.subdirectory) {
                    continue;
                }
                String name = null;
                if (entry.named()) {
                    name = readName(rootOffset + (entry.nameField & ~HIGH_BIT));
                    if (name == null) {
                        continue;
                    }
                }
                readLanguages(entry.target, type, entry.id(), name);
            }
        }

        private void readLanguages(long dirOffset, int type, int id, String name)
                throws IOException {
            for (Entry entry : readEntries(dirOffset)) {
                // Language ids are always numeric, and a subdirectory below the
                // language level is malformed; nothing to find there
                if (!entry.subdirectory && !entry.named()) {
                    readDataEntry(entry.target, new Resource(type, id, name, entry.id()));
                }
            }
        }

        /**
         * @return the entries of the directory, as many as the entry budget still allows
         */
        private List<Entry> readEntries(long dirOffset) throws IOException {
            byte[] dir = section.read(dirOffset, RESOURCE_DIRECTORY_SIZE);
            if (dir == null) {
                return List.of();
            }
            int numEntries = EndianUtils.getUShortLE(dir, 12) + EndianUtils.getUShortLE(dir, 14);
            int count = Math.min(numEntries, budget);
            budget -= count;
            budgetExceeded |= count < numEntries;
            byte[] bytes = section.read(dirOffset + RESOURCE_DIRECTORY_SIZE,
                    count * RESOURCE_DIRECTORY_ENTRY_SIZE);
            if (bytes == null) {
                return List.of();
            }
            List<Entry> entries = new ArrayList<>(count);
            for (int i = 0; i < bytes.length; i += RESOURCE_DIRECTORY_ENTRY_SIZE) {
                long dataField = EndianUtils.getUIntLE(bytes, i + 4);
                entries.add(new Entry(EndianUtils.getUIntLE(bytes, i),
                        (dataField & HIGH_BIT) != 0, rootOffset + (dataField & ~HIGH_BIT)));
            }
            return entries;
        }

        private void readDataEntry(long offset, Resource resource) throws IOException {
            byte[] entry = section.read(offset, RESOURCE_DATA_ENTRY_SIZE);
            if (entry == null) {
                return;
            }
            // Resource data normally lives in the same section as the tree;
            // data elsewhere is out of reach
            resource.offset = EndianUtils.getUIntLE(entry, 0) - sectionVa;
            resource.size = EndianUtils.getUIntLE(entry, 4);
            if (!section.contains(resource.offset, resource.size)) {
                return;
            }
            if (resource.type == RT_GROUP_ICON) {
                groups.add(resource);
            } else if (resource.name == null) {
                // Groups reference icons by numeric id, so a named icon is unreachable
                icons.computeIfAbsent(resource.id, k -> new LinkedHashMap<>())
                        .putIfAbsent(resource.language, resource);
            }
        }

        /**
         * @return the name with path separators and control characters
         * replaced: it ends up in a file name and in an id whose parts are
         * separated by slashes
         */
        private String readName(long offset) throws IOException {
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

    /**
     * One entry of a resource directory.
     *
     * @param nameField    the id, or with the high bit set the offset of the name
     * @param subdirectory whether the target is a directory rather than a data entry
     * @param target       the section offset the entry points to
     */
    private record Entry(long nameField, boolean subdirectory, long target) {

        boolean named() {
            return (nameField & HIGH_BIT) != 0;
        }

        int id() {
            return (int) (nameField & 0xffff);
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

        int bitCount(int index) {
            return EndianUtils.getUShortLE(dir, groupEntry(index) + 6);
        }

        long claimedPixelCount(int index) {
            return (long) claimedSide(dir[groupEntry(index)]) *
                    claimedSide(dir[groupEntry(index) + 1]);
        }

        /** A side of 0 in a directory entry means 256 or above. */
        private static int claimedSide(byte side) {
            int value = side & 0xff;
            return value == 0 ? 256 : value;
        }
    }

    /**
     * The bytes of the resource section, addressed by their offset in it.
     */
    abstract static class Section {
        private final long declaredSize;

        Section(long declaredSize) {
            this.declaredSize = declaredSize;
        }

        /**
         * @return whether the range lies inside the section as declared
         */
        final boolean contains(long offset, long length) {
            return offset >= 0 && length >= 0 && offset + length <= declaredSize;
        }

        /**
         * @return the number of bytes of the section known to exist
         */
        abstract long available();

        /**
         * @return whether the range can be read; finding out may read the input that far
         */
        abstract boolean canRead(long offset, long length) throws IOException;

        /**
         * Copies a range that {@link #canRead(long, long)} has confirmed.
         *
         * @return false if the bytes are not there after all
         */
        abstract boolean copy(long offset, byte[] target, int targetOffset, int length)
                throws IOException;

        /**
         * @return whether a range was refused that the section declares but
         * this class does not reach
         */
        boolean beyondLimit() {
            return false;
        }

        /**
         * @return false if the range cannot be read
         */
        final boolean read(long offset, byte[] target, int targetOffset, int length)
                throws IOException {
            return canRead(offset, length) && copy(offset, target, targetOffset, length);
        }

        /**
         * @return the bytes, or null if the range cannot be read
         */
        final byte[] read(long offset, int length) throws IOException {
            if (!canRead(offset, length)) {
                return null;
            }
            byte[] bytes = new byte[length];
            return copy(offset, bytes, 0, length) ? bytes : null;
        }
    }

    /**
     * A section of a file, read where the walk points.
     */
    static final class FileSection extends Section {
        private final FileChannel channel;
        private final long start;
        private final long present;

        /**
         * @param start the file offset of the section
         */
        FileSection(FileChannel channel, long start, long declaredSize) throws IOException {
            super(declaredSize);
            if (start > channel.size()) {
                throw new EOFException("The file ends before its resource section");
            }
            this.channel = channel;
            this.start = start;
            this.present = Math.min(declaredSize, channel.size() - start);
        }

        @Override
        long available() {
            return present;
        }

        @Override
        boolean canRead(long offset, long length) {
            return contains(offset, length) && offset + length <= present;
        }

        @Override
        boolean copy(long offset, byte[] target, int targetOffset, int length)
                throws IOException {
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
    }

    /**
     * A section that can only be read forward. It is buffered from its start
     * as far as the walk has reached; the buffer grows with the bytes that
     * arrive, never with a size the file merely declares.
     */
    static final class BufferedSection extends Section {
        private final InputStream source;
        private final long limit;
        byte[] buf = new byte[0];
        private int length;
        private boolean eof;
        private boolean beyondLimit;

        /**
         * @param source a stream positioned at the start of the section
         */
        BufferedSection(InputStream source, long declaredSize) {
            super(declaredSize);
            this.source = source;
            this.limit = Math.min(declaredSize, MAX_BUFFERED_SECTION_MB * 1024L * 1024L);
        }

        @Override
        long available() {
            return length;
        }

        @Override
        boolean canRead(long offset, long length) throws IOException {
            if (!contains(offset, length)) {
                return false;
            }
            if (offset + length > limit) {
                beyondLimit = true;
                return false;
            }
            return ensure((int) (offset + length));
        }

        @Override
        boolean copy(long offset, byte[] target, int targetOffset, int length) {
            System.arraycopy(buf, (int) offset, target, targetOffset, length);
            return true;
        }

        @Override
        boolean beyondLimit() {
            return beyondLimit;
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
}
