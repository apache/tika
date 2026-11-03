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
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

import org.apache.commons.io.IOUtils;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import org.apache.tika.annotation.TikaComponent;
import org.apache.tika.exception.EncryptedDocumentException;
import org.apache.tika.exception.TikaException;
import org.apache.tika.extractor.EmbeddedDocumentExtractor;
import org.apache.tika.extractor.EmbeddedDocumentUtil;
import org.apache.tika.io.BoundedInputStream;
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
 * fragments. A hybrid file, a version 6 book with a KF8 book behind a BOUNDARY record,
 * yields the first as the document and the second as an embedded document. An encrypted
 * book yields its metadata and images, which are not encrypted, and records the locked text
 * as an {@link EncryptedDocumentException} warning rather than throwing it, so that the
 * unpack endpoints still answer with the cover.
 */
@TikaComponent
public class MobiParser implements Parser {

    private static final long serialVersionUID = 3356098457298146191L;

    static final MediaType MOBI_TYPE = MediaType.application("x-mobipocket-ebook");
    static final MediaType AZW_TYPE = MediaType.application("vnd.amazon.ebook");
    static final MediaType KF8_TYPE = MediaType.application("vnd.amazon.mobi8-ebook");
    private static final Set<MediaType> SUPPORTED_TYPES = Set.of(MOBI_TYPE, AZW_TYPE, KF8_TYPE);

    /** A book larger than this is not read; the largest real ones are tens of MB. */
    static final int MAX_FILE_SIZE = 256 * 1024 * 1024;
    /** The text is cut here whatever the header declares; a novel is a few MB. */
    static final int DEFAULT_MAX_TEXT_LENGTH = 64 * 1024 * 1024;

    private int maxTextLength = DEFAULT_MAX_TEXT_LENGTH;

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

    private static final List<Function<String, Date>> DATE_FORMS = List.of(
            value -> Date.from(OffsetDateTime.parse(value).toInstant()),
            value -> Date.from(LocalDateTime.parse(value).toInstant(ZoneOffset.UTC)),
            value -> Date.from(LocalDate.parse(value).atStartOfDay().toInstant(ZoneOffset.UTC)));

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

    @Override
    public void parse(TikaInputStream tis, ContentHandler handler, Metadata metadata,
                      ParseContext context) throws IOException, SAXException, TikaException {
        // refused before the read when the stream knows its length, after it otherwise
        byte[] file = tis.hasLength() && tis.getLength() > MAX_FILE_SIZE ? null
                : IOUtils.toByteArray(new BoundedInputStream(MAX_FILE_SIZE + 1L, tis));
        if (file == null || file.length > MAX_FILE_SIZE) {
            throw new TikaException("The book is larger than " + MAX_FILE_SIZE + " bytes");
        }
        PalmDatabase pdb = PalmDatabase.read(file);
        if (!BOOK_MOBI.equals(pdb.typeAndCreator())) {
            throw new TikaException("Not a Mobipocket book: Palm Database type "
                    + pdb.typeAndCreator());
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
            if (kf8Half != null) {
                extractKf8Half(pdb, kf8Half, xhtml, context);
            }
            if (header.encryption() == MobiHeader.Encryption.NONE) {
                extractText(pdb, header, xhtml, metadata, context, maxTextLength);
            } else {
                // not thrown: the metadata and the images are complete, only the text is
                // locked, and a thrown exception would cost the client the cover (422)
                EmbeddedDocumentUtil.recordException(new EncryptedDocumentException(
                        "The text is encrypted (Mobipocket encryption " + header.encryption().label()
                                + "); metadata and images only"), metadata, context);
            }
        } finally {
            xhtml.endDocument();
        }
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
        Date date = published == null ? null : parseDate(published);
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

    /** EXTH 106 is an ISO date, with or without a time and an offset. */
    static Date parseDate(String value) {
        for (Function<String, Date> form : DATE_FORMS) {
            try {
                return form.apply(value);
            } catch (DateTimeException | IllegalArgumentException e) {
                // the next form, or none: a year beyond what a Date holds ends here too
                continue;
            }
        }
        return null;
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
     * rebuilt from the records from its record 0 on, with the header's record indices
     * counted from there and no images, since the two halves share them and the first half
     * has emitted them.
     */
    private static void extractKf8Half(PalmDatabase pdb, Kf8Half half, XHTMLContentHandler xhtml,
                                       ParseContext context) throws IOException, SAXException {
        List<byte[]> records = new ArrayList<>();
        records.add(half.header().rebasedRecord0(half.index()));
        for (int i = half.index() + 1; i < pdb.recordCount(); i++) {
            records.add(pdb.record(i));
        }
        Metadata halfMetadata = Metadata.newInstance(context);
        halfMetadata.set(HttpHeaders.CONTENT_TYPE, KF8_TYPE.toString());
        halfMetadata.set(TikaCoreProperties.EMBEDDED_RESOURCE_TYPE,
                TikaCoreProperties.EmbeddedResourceType.ALTERNATE_FORMAT_CHUNK.toString());
        halfMetadata.set(TikaCoreProperties.RESOURCE_NAME_KEY, "kf8.azw3");
        EmbeddedDocumentExtractor extractor =
                EmbeddedDocumentUtil.getEmbeddedDocumentExtractor(context);
        if (!extractor.shouldParseEmbedded(halfMetadata, context)) {
            return;
        }
        try (TikaInputStream stream = TikaInputStream.get(pdb.rebuild(records), halfMetadata)) {
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
        long remaining = Math.min(header.textLength(), maxTextLength);
        // decoded once at the end: a multibyte character may straddle two records
        ByteArrayOutputStream html = new ByteArrayOutputStream();
        for (int i = 1; i <= lastTextRecord && remaining > 0; i++) {
            PalmDatabase.Slice record = pdb.slice(i);
            record = record.trimmed(PalmDoc.trailingEntriesLength(record, header.trailingEntryFlags()));
            byte[] text = decoder.decode(record, (int) Math.min(remaining, header.textRecordSize()));
            remaining -= text.length;
            html.write(text);
        }
        if (header.textLength() > maxTextLength) {
            EmbeddedDocumentUtil.recordException(new TikaException("The text is cut at "
                    + maxTextLength + " of the declared " + header.textLength() + " bytes"),
                    metadata, context);
        }
        if (html.size() == 0) {
            return;
        }
        htmlParser(context).parseString(html.toString(header.charset()),
                new EmbeddedContentHandler(new BodyContentHandler(xhtml)),
                new Metadata(), context);
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
