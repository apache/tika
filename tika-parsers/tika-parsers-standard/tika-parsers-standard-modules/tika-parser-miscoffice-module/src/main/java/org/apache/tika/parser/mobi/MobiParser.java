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
package org.apache.tika.parser.mobi;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.apache.commons.io.IOUtils;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import org.apache.tika.annotation.TikaComponent;
import org.apache.tika.exception.EncryptedDocumentException;
import org.apache.tika.exception.TikaException;
import org.apache.tika.extractor.EmbeddedDocumentExtractor;
import org.apache.tika.extractor.EmbeddedDocumentUtil;
import org.apache.tika.io.BoundedInputStream;
import org.apache.tika.io.EndianUtils;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.HttpHeaders;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.Mobi;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.parser.html.JSoupParser;
import org.apache.tika.sax.BodyContentHandler;
import org.apache.tika.sax.EmbeddedContentHandler;
import org.apache.tika.sax.XHTMLContentHandler;
import org.apache.tika.utils.TikaDates;

/**
 * Parser for Mobipocket books and their Kindle descendants: {@code .mobi}/{@code .prc},
 * {@code .azw} and the KF8 generation {@code .azw3}. All three are Palm Database files
 * whose record 0 carries the MOBI header and the EXTH metadata, followed by the text
 * records and the images.
 * <p>
 * Metadata comes from EXTH, the cover and the other images are emitted as embedded
 * documents (the cover as the {@link TikaCoreProperties.EmbeddedResourceType#THUMBNAIL}),
 * and the text records, PalmDOC- or HUFF/CDIC-compressed or stored, are handed to the HTML
 * parser. The KF8 text is taken as its records hold it, without reassembling skeletons and
 * fragments; of its content flows only the first, the book's HTML, is text, the others are
 * stylesheets and images. A hybrid file, a version 6 book with a KF8 book of the same
 * content behind a BOUNDARY record, yields the first; the KF8 book is emitted as an embedded
 * document of its own only with {@link #setExtractKf8(boolean)}. An encrypted book yields
 * its metadata and images, which are not encrypted, sets {@code tika:encrypted} and records
 * the locked text as an {@link EncryptedDocumentException} warning rather than throwing it,
 * so that the unpack endpoints still answer with the cover. A file that is not a Mobipocket
 * book, a {@code .prc} that is a Palm application or a plain PalmDOC text, yields nothing
 * and a warning, as it did before there was a parser for the type.
 */
@TikaComponent
public class MobiParser implements Parser {

    private static final long serialVersionUID = 3356098457298146191L;

    static final MediaType MOBI_TYPE = MediaType.application("x-mobipocket-ebook");
    static final MediaType AZW_TYPE = MediaType.application("vnd.amazon.ebook");
    static final MediaType KF8_TYPE = MediaType.application("vnd.amazon.mobi8-ebook");
    private static final Set<MediaType> SUPPORTED_TYPES = Set.of(MOBI_TYPE, AZW_TYPE, KF8_TYPE);

    /**
     * Of a larger book only the first this many bytes are read: the header, the metadata and
     * the records that fit. Real books are a few MB, illustrated ones some tens of MB.
     */
    static final int DEFAULT_MAX_FILE_SIZE = 256 * 1024 * 1024;
    /** The text is cut here whatever the header declares; a novel is one or two MB. */
    static final int DEFAULT_MAX_TEXT_LENGTH = 16 * 1024 * 1024;
    /**
     * The text is also cut at this multiple of the file's size: PalmDOC and HUFF/CDIC reach
     * a few times the compressed size on real books, a crafted dictionary far more.
     */
    static final int MAX_TEXT_EXPANSION = 32;

    private int maxFileSize = DEFAULT_MAX_FILE_SIZE;
    private int maxTextLength = DEFAULT_MAX_TEXT_LENGTH;
    private boolean extractKf8 = false;

    private static final String BOOK_MOBI = "BOOKMOBI";

    /** How an image record is recognised: by the magic the image format starts with. */
    private record ImageMagic(byte[] magic, MediaType type) {
    }

    private static final List<ImageMagic> IMAGE_MAGICS = List.of(
            new ImageMagic(new byte[]{(byte) 0xff, (byte) 0xd8, (byte) 0xff}, MediaType.image("jpeg")),
            new ImageMagic(new byte[]{(byte) 0x89, 'P', 'N', 'G'}, MediaType.image("png")),
            new ImageMagic(new byte[]{'G', 'I', 'F', '8'}, MediaType.image("gif")),
            new ImageMagic(new byte[]{'B', 'M'}, MediaType.image("bmp")));
    private static final byte[] BOUNDARY = "BOUNDARY".getBytes(StandardCharsets.ISO_8859_1);

    private static final byte[] FDST = "FDST".getBytes(StandardCharsets.ISO_8859_1);

    @Override
    public Set<MediaType> getSupportedTypes(ParseContext context) {
        return SUPPORTED_TYPES;
    }

    /** How many bytes of text are extracted at most, whatever the header declares. */
    public int getMaxTextLength() {
        return maxTextLength;
    }

    public void setMaxTextLength(int maxTextLength) {
        if (maxTextLength <= 0) {
            throw new IllegalArgumentException("maxTextLength must be positive, got "
                    + maxTextLength);
        }
        this.maxTextLength = maxTextLength;
    }

    /** How many bytes of a book are read at most; of a larger one, its beginning. */
    public int getMaxFileSize() {
        return maxFileSize;
    }

    public void setMaxFileSize(int maxFileSize) {
        if (maxFileSize <= 0) {
            throw new IllegalArgumentException("maxFileSize must be positive, got " + maxFileSize);
        }
        this.maxFileSize = maxFileSize;
    }

    /**
     * Whether the KF8 book of a hybrid file is emitted as an embedded document too. It holds
     * the same book as the first half, so it is off by default.
     */
    public boolean isExtractKf8() {
        return extractKf8;
    }

    public void setExtractKf8(boolean extractKf8) {
        this.extractKf8 = extractKf8;
    }

    @Override
    public void parse(TikaInputStream tis, ContentHandler handler, Metadata metadata,
                      ParseContext context) throws IOException, SAXException, TikaException {
        byte[] file = IOUtils.toByteArray(new BoundedInputStream(maxFileSize, tis));
        if (file.length == maxFileSize && tis.read() >= 0) {
            // the beginning holds the header and the metadata; the records that do not fit
            // are dropped as those of a truncated file are
            EmbeddedDocumentUtil.recordException(new TikaException("The book is larger than "
                    + maxFileSize + " bytes (maxFileSize); its first " + maxFileSize
                    + " bytes are read"), metadata, context);
        }
        // a .prc or .azw reaches this parser by its name: a Palm application, a PalmDOC text,
        // a Topaz or KFX book; none of them is a Mobipocket book, and none was an error
        // before there was a parser for the type
        PalmDatabase pdb;
        try {
            pdb = PalmDatabase.read(file);
        } catch (TikaException e) {
            nothingExtracted(new TikaException("Not a Mobipocket book; nothing extracted", e),
                    handler, metadata, context);
            return;
        }
        if (!BOOK_MOBI.equals(pdb.typeAndCreator())) {
            nothingExtracted(new TikaException("Not a Mobipocket book: Palm Database type "
                    + pdb.typeAndCreator() + "; nothing extracted"), handler, metadata, context);
            return;
        }
        MobiHeader header = MobiHeader.read(pdb);
        if (pdb.recordCount() < pdb.declaredRecordCount()) {
            EmbeddedDocumentUtil.recordException(new TikaException("The file is truncated: "
                    + pdb.recordCount() + " of " + pdb.declaredRecordCount()
                    + " records are there; what they hold is read"), metadata, context);
        }
        Kf8Half kf8Half = kf8Half(pdb, header, metadata, context);
        extractMetadata(header, kf8Half != null, metadata);

        XHTMLContentHandler xhtml = new XHTMLContentHandler(handler, metadata, context);
        xhtml.startDocument();
        try {
            extractImages(pdb, header, kf8Half == null ? pdb.recordCount() : kf8Half.index(), xhtml,
                    context);
            if (kf8Half != null && extractKf8) {
                extractKf8Half(pdb, kf8Half, xhtml, context);
            }
            if (header.encryption() == MobiHeader.Encryption.NONE) {
                extractText(pdb, header, xhtml, metadata, context,
                        (int) Math.min(maxTextLength, (long) MAX_TEXT_EXPANSION * file.length));
            } else {
                // not thrown: the metadata and the images are complete, only the text is
                // locked, and a thrown exception would cost the client the cover (422); the
                // flag a thrown EncryptedDocumentException would have set is set here
                metadata.set(TikaCoreProperties.IS_ENCRYPTED, true);
                EmbeddedDocumentUtil.recordException(new EncryptedDocumentException(
                        "The text is encrypted (Mobipocket encryption " + header.encryption().label()
                                + "); metadata and images only"), metadata, context);
            }
        } finally {
            xhtml.endDocument();
        }
    }

    private static void nothingExtracted(TikaException why, ContentHandler handler,
                                         Metadata metadata, ParseContext context)
            throws SAXException {
        EmbeddedDocumentUtil.recordException(why, metadata, context);
        XHTMLContentHandler xhtml = new XHTMLContentHandler(handler, metadata, context);
        xhtml.startDocument();
        xhtml.endDocument();
    }

    private static void extractMetadata(MobiHeader header, boolean hybrid, Metadata metadata) {
        String detected = metadata.get(HttpHeaders.CONTENT_TYPE);
        if (header.isKf8()) {
            metadata.set(HttpHeaders.CONTENT_TYPE, KF8_TYPE.toString());
        } else if (detected == null || !detected.startsWith(AZW_TYPE.toString())) {
            metadata.set(HttpHeaders.CONTENT_TYPE, MOBI_TYPE.toString());
        }
        String title = header.exthString(MobiHeader.EXTH_UPDATED_TITLE);
        if (title == null) {
            title = header.fullName();
        }
        if (!title.isEmpty()) {
            metadata.set(TikaCoreProperties.TITLE, title);
        }
        for (String creator : header.exthStrings(MobiHeader.EXTH_CREATOR)) {
            metadata.add(TikaCoreProperties.CREATOR, creator);
        }
        for (String publisher : header.exthStrings(MobiHeader.EXTH_PUBLISHER)) {
            metadata.add(TikaCoreProperties.PUBLISHER, publisher);
        }
        String description = header.exthString(MobiHeader.EXTH_DESCRIPTION);
        if (description != null) {
            metadata.set(TikaCoreProperties.DESCRIPTION, description);
        }
        Set<String> identifiers = new LinkedHashSet<>();
        identifiers.addAll(header.exthStrings(MobiHeader.EXTH_ISBN));
        identifiers.addAll(header.exthStrings(MobiHeader.EXTH_ASIN));
        identifiers.addAll(header.exthStrings(MobiHeader.EXTH_ASIN_2));
        for (String identifier : identifiers) {
            metadata.add(TikaCoreProperties.IDENTIFIER, identifier);
        }
        for (String subject : header.exthStrings(MobiHeader.EXTH_SUBJECT)) {
            metadata.add(TikaCoreProperties.SUBJECT, subject);
        }
        for (String contributor : header.exthStrings(MobiHeader.EXTH_CONTRIBUTOR)) {
            metadata.add(TikaCoreProperties.CONTRIBUTOR, contributor);
        }
        String rights = header.exthString(MobiHeader.EXTH_RIGHTS);
        if (rights != null) {
            metadata.set(TikaCoreProperties.RIGHTS, rights);
        }
        String language = header.language();
        if (language != null) {
            metadata.set(TikaCoreProperties.LANGUAGE, language);
        }
        String published = header.exthString(MobiHeader.EXTH_PUBLISHING_DATE);
        String date = published == null ? null : TikaDates.toMetadataString(published);
        if (date != null) {
            metadata.set(TikaCoreProperties.CREATED, date);
        }
        metadata.set(HttpHeaders.CONTENT_ENCODING, header.charset().name());
        metadata.set(Mobi.VERSION, header.fileVersion());
        metadata.set(Mobi.COMPRESSION, header.compression().label());
        metadata.set(Mobi.ENCRYPTION, header.encryption().label());
        String cdeType = header.exthString(MobiHeader.EXTH_CDE_TYPE);
        if (cdeType != null) {
            metadata.set(Mobi.CDE_TYPE, cdeType);
        }
        metadata.set(Mobi.HYBRID, hybrid);
    }

    /**
     * Every image record from the header's first image index up to {@code end}: the end of
     * the book, or the KF8 half of a hybrid file, whose two halves share the images. A
     * record is an image when it starts like one; the index and font records, the boundary
     * and the end-of-file marker that sit among them do not. The record EXTH 201 names is
     * the thumbnail, or the one EXTH 202 names when there is no 201.
     */
    private static void extractImages(PalmDatabase pdb, MobiHeader header, int end,
                                      XHTMLContentHandler xhtml, ParseContext context)
            throws IOException, SAXException {
        long first = header.firstImageIndex();
        if (first <= 0 || first >= end) {
            return;
        }
        long cover = header.exthNumber(MobiHeader.EXTH_COVER_OFFSET);
        long thumbnail = header.exthNumber(MobiHeader.EXTH_THUMBNAIL_OFFSET);
        long coverRecord = cover >= 0 ? first + cover : -1;
        long thumbnailRecord = thumbnail >= 0 ? first + thumbnail : -1;

        EmbeddedDocumentExtractor extractor =
                EmbeddedDocumentUtil.getEmbeddedDocumentExtractor(context);
        for (int i = (int) first; i < end; i++) {
            MediaType type = imageType(pdb, i);
            if (type == null) {
                continue;
            }
            boolean representative = i == coverRecord || (coverRecord < 0 && i == thumbnailRecord);
            String name = i == coverRecord ? "cover" : i == thumbnailRecord ? "thumbnail"
                    : "image" + i;
            Metadata imageMetadata = Metadata.newInstance(context);
            imageMetadata.set(HttpHeaders.CONTENT_TYPE, type.toString());
            imageMetadata.set(TikaCoreProperties.EMBEDDED_RESOURCE_TYPE, representative
                    ? TikaCoreProperties.EmbeddedResourceType.THUMBNAIL.toString()
                    : TikaCoreProperties.EmbeddedResourceType.INLINE.toString());
            imageMetadata.set(TikaCoreProperties.RESOURCE_NAME_KEY, name + "." + type.getSubtype());
            if (!extractor.shouldParseEmbedded(imageMetadata, context)) {
                continue;
            }
            try (TikaInputStream image = TikaInputStream.get(pdb.record(i), imageMetadata)) {
                extractor.parseEmbedded(image, xhtml, imageMetadata, context, true);
            }
        }
    }

    /** The KF8 book a hybrid file carries: where its record 0 is, and that record read. */
    private record Kf8Half(int index, MobiHeader header) {
    }

    /**
     * The KF8 book a hybrid file carries behind a {@code BOUNDARY} record, for readers that
     * know the newer format, or null. EXTH 121 of the first header points there, at the
     * boundary record or at the header behind it; the header has to be KF8, which a hybrid's
     * second header is by definition, so a KF8 header never looks for a half of its own and
     * nothing can nest.
     */
    private static Kf8Half kf8Half(PalmDatabase pdb, MobiHeader header, Metadata metadata,
                                   ParseContext context) {
        if (header.isKf8()) {
            return null;
        }
        long named = header.exthNumber(MobiHeader.EXTH_KF8_BOUNDARY);
        if (named <= 0) {
            return null;
        }
        int index = (int) Math.min(named, Integer.MAX_VALUE);
        if (index < pdb.recordCount() && pdb.recordStartsWith(index, BOUNDARY)) {
            index++;
        }
        TikaException why = null;
        if (index < pdb.recordCount()) {
            try {
                MobiHeader half = MobiHeader.read(pdb.record(index));
                if (half.isKf8()) {
                    return new Kf8Half(index, half);
                }
            } catch (TikaException e) {
                why = e;
            }
        }
        EmbeddedDocumentUtil.recordException(new TikaException("EXTH 121 names record " + named
                + " as the KF8 half of a hybrid, but there is no KF8 header there; read as a "
                + "single book", why), metadata, context);
        return null;
    }

    /**
     * The KF8 book of a hybrid file as an embedded document of its own: a Palm Database
     * rebuilt from the records from its record 0 on, whose record indices already count
     * from there, without images, since the two halves share them and the first half has
     * emitted them. Built only once the extractor wants it.
     */
    private static void extractKf8Half(PalmDatabase pdb, Kf8Half half, XHTMLContentHandler xhtml,
                                       ParseContext context) throws IOException, SAXException {
        Metadata halfMetadata = Metadata.newInstance(context);
        halfMetadata.set(HttpHeaders.CONTENT_TYPE, KF8_TYPE.toString());
        halfMetadata.set(TikaCoreProperties.RESOURCE_NAME_KEY, "kf8.azw3");
        EmbeddedDocumentExtractor extractor =
                EmbeddedDocumentUtil.getEmbeddedDocumentExtractor(context);
        if (!extractor.shouldParseEmbedded(halfMetadata, context)) {
            return;
        }
        byte[] book = pdb.rebuild(half.header().kf8Record0(), half.index() + 1);
        try (TikaInputStream stream = TikaInputStream.get(book, halfMetadata)) {
            extractor.parseEmbedded(stream, xhtml, halfMetadata, context, true);
        }
    }

    private static MediaType imageType(PalmDatabase pdb, int index) {
        for (ImageMagic image : IMAGE_MAGICS) {
            if (pdb.recordStartsWith(index, image.magic())) {
                return image.type();
            }
        }
        return null;
    }

    private static void extractText(PalmDatabase pdb, MobiHeader header,
                                    XHTMLContentHandler xhtml, Metadata metadata,
                                    ParseContext context, int maxTextLength)
            throws IOException, SAXException, TikaException {
        TextDecoder decoder;
        switch (header.compression()) {
            case HUFF_CDIC:
                try {
                    HuffCdic huffCdic = HuffCdic.read(pdb, header.huffRecordIndex(),
                            header.huffRecordCount());
                    decoder = huffCdic::decode;
                } catch (TikaException e) {
                    EmbeddedDocumentUtil.recordException(new TikaException(
                            "HUFF/CDIC tables unreadable; metadata and images only", e),
                            metadata, context);
                    return;
                }
                break;
            case PALMDOC:
                decoder = PalmDoc::decompress;
                break;
            case NONE:
                decoder = (record, max) -> Arrays.copyOfRange(record.data(), record.offset(),
                        record.offset() + Math.min(record.length(), max));
                break;
            default:
                EmbeddedDocumentUtil.recordException(new TikaException(
                        "Unknown text compression; metadata and images only"), metadata, context);
                return;
        }
        int lastTextRecord = (int) Math.min(header.textRecordCount(), pdb.recordCount() - 1L);
        long bookLength = Math.min(header.textLength(), firstFlowEnd(pdb, header));
        long remaining = Math.min(bookLength, maxTextLength);
        // decoded once at the end: a multibyte character may straddle two records
        ByteArrayOutputStream html = new ByteArrayOutputStream();
        for (int i = 1; i <= lastTextRecord && remaining > 0; i++) {
            PalmDatabase.Slice record = pdb.slice(i);
            record = record.trimmed(PalmDoc.trailingEntriesLength(record, header.trailingEntryFlags()));
            byte[] text;
            try {
                text = decoder.decode(record, (int) Math.min(remaining, header.textRecordSize()));
            } catch (TikaException e) {
                // what the records before it held is kept, as for a truncated book
                EmbeddedDocumentUtil.recordException(new TikaException("Text record " + i
                        + " cannot be decoded; the text of the records before it is kept", e),
                        metadata, context);
                break;
            }
            remaining -= text.length;
            html.write(text);
        }
        if (bookLength > maxTextLength) {
            EmbeddedDocumentUtil.recordException(new TikaException("The text is cut at "
                    + maxTextLength + " of " + bookLength + " bytes"), metadata, context);
        }
        if (html.size() == 0) {
            return;
        }
        htmlParser(context).parseString(html.toString(header.charset()),
                new EmbeddedContentHandler(new BodyContentHandler(xhtml)),
                Metadata.newInstance(context), context);
    }

    /**
     * Where the first content flow ends, by the FDST record of a KF8 book: the book's HTML;
     * the flows after it are its stylesheets and SVG images. {@code Long.MAX_VALUE} when
     * there is no FDST record to go by.
     */
    private static long firstFlowEnd(PalmDatabase pdb, MobiHeader header) {
        long index = header.fdstIndex();
        if (index <= 0 || index >= pdb.recordCount() || !pdb.recordStartsWith((int) index, FDST)) {
            return Long.MAX_VALUE;
        }
        PalmDatabase.Slice fdst = pdb.slice((int) index);
        // FDST, the offset of the table, the flow count, then a start and an end per flow
        if (fdst.length() < 20) {
            return Long.MAX_VALUE;
        }
        long count = EndianUtils.getUIntBE(fdst.data(), fdst.offset() + 8);
        long end = EndianUtils.getUIntBE(fdst.data(), fdst.offset() + 16);
        return count == 0 || end == 0 ? Long.MAX_VALUE : end;
    }

    /** What turns one text record into text, chosen once per book. */
    private interface TextDecoder {
        byte[] decode(PalmDatabase.Slice record, int maxLength) throws TikaException;
    }

    private static JSoupParser htmlParser(ParseContext context) {
        Parser configured = EmbeddedDocumentUtil.tryToFindExistingLeafParser(JSoupParser.class,
                context);
        return configured instanceof JSoupParser ? (JSoupParser) configured : new JSoupParser();
    }
}
