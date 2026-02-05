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
package org.apache.tika.parser.pkg;

import static org.apache.tika.detect.zip.PackageConstants.JAR;
import static org.apache.tika.detect.zip.PackageConstants.ZIP;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.commons.compress.archivers.ArchiveEntry;
import org.apache.commons.compress.archivers.zip.UnsupportedZipFeatureException;
import org.apache.commons.compress.archivers.zip.UnsupportedZipFeatureException.Feature;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveInputStream;
import org.apache.commons.compress.archivers.zip.ZipFile;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import org.apache.tika.config.ConfigDeserializer;
import org.apache.tika.config.JsonConfig;
import org.apache.tika.config.TikaComponent;
import org.apache.tika.detect.EncodingDetector;
import org.apache.tika.exception.EncryptedDocumentException;
import org.apache.tika.exception.TikaConfigException;
import org.apache.tika.exception.TikaException;
import org.apache.tika.extractor.EmbeddedDocumentExtractor;
import org.apache.tika.extractor.EmbeddedDocumentUtil;
import org.apache.tika.io.TemporaryResources;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.metadata.Zip;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.XHTMLContentHandler;
import org.apache.tika.zip.utils.ZipSalvager;

/**
 * Parser for ZIP and JAR archives using file-based access for complete metadata extraction.
 * <p>
 * This parser handles:
 * <ul>
 *   <li>Standard ZIP archives</li>
 *   <li>JAR (Java Archive) files</li>
 *   <li>Archive and entry comments</li>
 *   <li>Unix permissions and file attributes</li>
 *   <li>Charset detection for non-Unicode entry names</li>
 *   <li>Encryption detection</li>
 * </ul>
 * <p>
 * This parser prefers file-based access (ZipFile) for complete metadata extraction,
 * but falls back to streaming (ZipArchiveInputStream) for edge-case ZIPs that
 * cannot be read as files (e.g., those with data descriptors that overlap the
 * central directory).
 */
@TikaComponent()
public class ZipParser extends AbstractArchiveParser {

    /**
     * Set of media types that are specializations of ZIP (e.g., Office documents, EPUB, APK).
     * Used to avoid overwriting more specific media types with generic "application/zip".
     */
    public static final Set<MediaType> ZIP_SPECIALIZATIONS = loadZipSpecializations();

    private static final long serialVersionUID = -5331043266963888709L;

    private static final Set<MediaType> SUPPORTED_TYPES = MediaType.set(ZIP, JAR);

    private static final int MIN_BYTES_FOR_DETECTING_CHARSET = 100;

    /**
     * Maximum number of entries to record in integrity check metadata fields.
     * Prevents excessive metadata in ZIPs with many discrepancies.
     */
    private static final int MAX_INTEGRITY_CHECK_ENTRIES = 100;

    private final ZipParserConfig defaultConfig;

    private static Set<MediaType> loadZipSpecializations() {
        Set<MediaType> zipSpecializations = new HashSet<>();
        for (String mediaTypeString : new String[]{
                //specializations of ZIP
                "application/bizagi-modeler", "application/epub+zip",
                "application/hwp+zip",
                "application/java-archive",
                "application/vnd.adobe.air-application-installer-package+zip",
                "application/vnd.android.package-archive", "application/vnd.apple.iwork",
                "application/vnd.apple.keynote", "application/vnd.apple.numbers",
                "application/vnd.apple.pages", "application/vnd.apple.unknown.13",
                "application/vnd.etsi.asic-e+zip", "application/vnd.etsi.asic-s+zip",
                "application/vnd.google-earth.kmz", "application/vnd.mindjet.mindmanager",
                "application/vnd.ms-excel.addin.macroenabled.12",
                "application/vnd.ms-excel.sheet.binary.macroenabled.12",
                "application/vnd.ms-excel.sheet.macroenabled.12",
                "application/vnd.ms-excel.template.macroenabled.12",
                "application/vnd.ms-powerpoint.addin.macroenabled.12",
                "application/vnd.ms-powerpoint.presentation.macroenabled.12",
                "application/vnd.ms-powerpoint.slide.macroenabled.12",
                "application/vnd.ms-powerpoint.slideshow.macroenabled.12",
                "application/vnd.ms-powerpoint.template.macroenabled.12",
                "application/vnd.ms-visio.drawing",
                "application/vnd.ms-visio.drawing.macroenabled.12",
                "application/vnd.ms-visio.stencil",
                "application/vnd.ms-visio.stencil.macroenabled.12",
                "application/vnd.ms-visio.template",
                "application/vnd.ms-visio.template.macroenabled.12",
                "application/vnd.ms-word.document.macroenabled.12",
                "application/vnd.ms-word.template.macroenabled.12",
                "application/vnd.ms-xpsdocument", "application/vnd.oasis.opendocument.formula",
                "application/vnd.openxmlformats-officedocument.presentationml.presentation",
                "application/vnd.openxmlformats-officedocument.presentationml.slide",
                "application/vnd.openxmlformats-officedocument.presentationml.slideshow",
                "application/vnd.openxmlformats-officedocument.presentationml.template",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.template",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.template",
                "application/x-ibooks+zip", "application/x-itunes-ipa",
                "application/x-tika-iworks-protected", "application/x-tika-java-enterprise-archive",
                "application/x-tika-java-web-archive", "application/x-tika-ooxml",
                "application/x-tika-visio-ooxml", "application/x-xliff+zip", "application/x-xmind",
                "model/vnd.dwfx+xps", "application/vnd.sun.xml.calc",
                "application/vnd.sun.xml.writer", "application/vnd.sun.xml.writer.template",
                "application/vnd.sun.xml.draw", "application/vnd.sun.xml.impress",
                "application/vnd.openofficeorg.autotext",
                "application/vnd.oasis.opendocument.graphics-template",
                "application/vnd.oasis.opendocument.text-web",
                "application/vnd.oasis.opendocument.spreadsheet-template",
                "application/vnd.oasis.opendocument.graphics",
                "application/vnd.oasis.opendocument.image-template",
                "application/vnd.oasis.opendocument.text",
                "application/vnd.oasis.opendocument.text-template",
                "application/vnd.oasis.opendocument.presentation",
                "application/vnd.oasis.opendocument.chart",
                "application/vnd.openofficeorg.extension",
                "application/vnd.oasis.opendocument.spreadsheet",
                "application/vnd.oasis.opendocument.image",
                "application/vnd.oasis.opendocument.formula-template",
                "application/vnd.oasis.opendocument.presentation-template",
                "application/vnd.oasis.opendocument.chart-template",
                "application/vnd.oasis.opendocument.text-master",
                "application/vnd.adobe.indesign-idml-package",
                "application/x-wacz", "application/x-vnd.datapackage+zip"
        }) {
            zipSpecializations.add(MediaType.parse(mediaTypeString));
        }
        return Collections.unmodifiableSet(zipSpecializations);
    }

    public ZipParser() {
        super();
        this.defaultConfig = new ZipParserConfig();
    }

    public ZipParser(ZipParserConfig config) {
        super();
        this.defaultConfig = config;
    }

    /**
     * Constructor for JSON-based configuration.
     */
    public ZipParser(JsonConfig jsonConfig) throws TikaConfigException {
        this(ConfigDeserializer.buildConfig(jsonConfig, ZipParserConfig.class));
    }

    public ZipParser(EncodingDetector encodingDetector) {
        super(encodingDetector);
        this.defaultConfig = new ZipParserConfig();
    }

    @Override
    public Set<MediaType> getSupportedTypes(ParseContext context) {
        return SUPPORTED_TYPES;
    }

    @Override
    public void parse(TikaInputStream tis, ContentHandler handler, Metadata metadata,
                      ParseContext context) throws IOException, SAXException, TikaException {

        ZipParserConfig config = context.get(ZipParserConfig.class, defaultConfig);

        if (tis.getOpenContainer() instanceof ZipFile) {
            // detectEntryName handles charset decoding from raw bytes, no need to reopen
            parseWithZipFile((ZipFile) tis.getOpenContainer(), tis, handler, metadata, context, config);
            return;
        }

        // Check detector hints - if detector already tried ZipFile and failed, go straight to streaming
        String detectorZipFileOpened = metadata.get(Zip.DETECTOR_ZIPFILE_OPENED);
        if ("false".equals(detectorZipFileOpened)) {
            // Detector already tried and failed - skip ZipFile, use streaming
            // Enable rewind for DATA_DESCRIPTOR retry in parseWithStream
            tis.enableRewind();
            String dataDescriptorRequired = metadata.get(Zip.DETECTOR_DATA_DESCRIPTOR_REQUIRED);
            parseWithStream(tis, handler, metadata, context, config,
                    "true".equals(dataDescriptorRequired));
            return;
        }

        // No detector hint - try to open ZipFile (with salvaging fallback)
        // This likely means that the user didn't apply a detector first or the zip detector was not in the chain
        ZipFile zipFile = ZipSalvager.tryToOpenZipFile(tis, metadata, config.getEntryEncoding());

        if (zipFile != null) {
            // ZipFile opened (directly or via salvaging) - use file-based parsing
            parseWithZipFile(zipFile, tis, handler, metadata, context, config);
        } else {
            // ZipFile and salvaging both failed - use streaming
            // Enable rewind for DATA_DESCRIPTOR retry in parseWithStream
            // (may be redundant if tryToOpenZipFile already called it, but that's safe)
            tis.enableRewind();
            parseWithStream(tis, handler, metadata, context, config, false);
        }
    }

    /**
     * Parses using a pre-opened ZipFile passed from the detector.
     *
     * @param zipFile  the pre-opened ZipFile from detector
     * @param tis      the TikaInputStream (for integrity check rewind)
     * @param handler  the content handler
     * @param metadata the metadata
     * @param context  the parse context
     * @param config   the parser configuration
     */
    private void parseWithZipFile(ZipFile zipFile, TikaInputStream tis, ContentHandler handler,
                                   Metadata metadata, ParseContext context, ZipParserConfig config)
            throws IOException, SAXException, TikaException {

        // Collect entry names from central directory for integrity check
        Set<String> centralDirectoryEntries = config.isIntegrityCheck()
                ? new LinkedHashSet<>() : null;

        // Don't close the ZipFile - it was passed from the detector and will be closed
        // when TikaInputStream is closed (it's set as the openContainer)
        updateMediaType(zipFile, metadata);

        EmbeddedDocumentExtractor extractor =
                EmbeddedDocumentUtil.getEmbeddedDocumentExtractor(context);

        XHTMLContentHandler xhtml = new XHTMLContentHandler(handler, metadata, context);
        xhtml.startDocument();

        try {
            Enumeration<ZipArchiveEntry> entries = zipFile.getEntries();
            while (entries.hasMoreElements()) {
                ZipArchiveEntry entry = entries.nextElement();
                if (centralDirectoryEntries != null) {
                    centralDirectoryEntries.add(entry.getName());
                }
                if (!entry.isDirectory()) {
                    parseZipFileEntry(zipFile, entry, extractor, metadata, xhtml, context, config);
                }
            }
        } finally {
            xhtml.endDocument();
        }

        // Perform integrity check if enabled
        if (config.isIntegrityCheck()) {
            tis.enableRewind();
            tis.rewind();
            performIntegrityCheck(tis, metadata, centralDirectoryEntries, config);
        }
    }

    /**
     * Parses using streaming with optional initial data descriptor support.
     *
     * @param tis                    the TikaInputStream
     * @param handler                the content handler
     * @param metadata               the metadata
     * @param context                the parse context
     * @param config                 the parser configuration
     * @param startWithDataDescriptor whether to start with data descriptor support enabled
     */
    private void parseWithStream(TikaInputStream tis, ContentHandler handler, Metadata metadata,
                                  ParseContext context, ZipParserConfig config,
                                  boolean startWithDataDescriptor)
            throws IOException, SAXException, TikaException {

        // Track entry names for duplicate detection during streaming
        Set<String> seenEntryNames = config.isIntegrityCheck()
                ? new LinkedHashSet<>() : null;
        List<String> duplicates = config.isIntegrityCheck()
                ? new ArrayList<>() : null;

        String encoding = config.getEntryEncoding() != null
                ? config.getEntryEncoding().name()
                : null;
        ZipArchiveInputStream zis = new ZipArchiveInputStream(tis, encoding, true, startWithDataDescriptor);

        updateMediaType(metadata);

        EmbeddedDocumentExtractor extractor =
                EmbeddedDocumentUtil.getEmbeddedDocumentExtractor(context);

        XHTMLContentHandler xhtml = new XHTMLContentHandler(handler, metadata, context);
        xhtml.startDocument();

        AtomicInteger entryCnt = new AtomicInteger();
        try {
            parseStreamEntries(zis, metadata, extractor, xhtml, false, entryCnt, context, config,
                    seenEntryNames, duplicates);
        } catch (UnsupportedZipFeatureException zfe) {
            if (zfe.getFeature() == Feature.DATA_DESCRIPTOR && !startWithDataDescriptor) {
                // Re-read with data descriptor support
                zis.close();
                tis.rewind();
                zis = new ZipArchiveInputStream(tis, encoding, true, true);
                parseStreamEntries(zis, metadata, extractor, xhtml, true, entryCnt, context, config,
                        seenEntryNames, duplicates);
            } else {
                throw zfe;
            }
        } finally {
            zis.close();
            xhtml.endDocument();
        }

        // Record integrity check results (streaming only = can't compare to central directory)
        if (config.isIntegrityCheck()) {
            if (duplicates.isEmpty()) {
                // No duplicates found, but we couldn't compare to central directory
                metadata.set(Zip.INTEGRITY_CHECK_RESULT, "PARTIAL");
            } else {
                metadata.set(Zip.INTEGRITY_CHECK_RESULT, "FAIL");
                for (String dup : duplicates) {
                    metadata.add(Zip.DUPLICATE_ENTRY_NAMES, dup);
                }
            }
        }
    }

    private void parseStreamEntries(ZipArchiveInputStream zis, Metadata metadata,
                                     EmbeddedDocumentExtractor extractor, XHTMLContentHandler xhtml,
                                     boolean shouldUseDataDescriptor, AtomicInteger entryCnt,
                                     ParseContext context, ZipParserConfig config,
                                     Set<String> seenEntryNames, List<String> duplicates)
            throws TikaException, IOException, SAXException {

        try {
            ArchiveEntry entry = zis.getNextEntry();
            while (entry != null) {
                if (shouldUseDataDescriptor && entryCnt.get() > 0) {
                    // Skip already-processed entries on re-read
                    entryCnt.decrementAndGet();
                    entry = zis.getNextEntry();
                    continue;
                }

                if (!entry.isDirectory() && entry instanceof ZipArchiveEntry) {
                    parseStreamEntry(zis, (ZipArchiveEntry) entry, extractor, metadata,
                            xhtml, context, config);

                    // Track duplicates AFTER successful processing
                    // (if DATA_DESCRIPTOR exception occurs, we'll re-read this entry)
                    if (seenEntryNames != null && duplicates != null) {
                        String name = entry.getName();
                        if (seenEntryNames.contains(name)) {
                            if (duplicates.size() < MAX_INTEGRITY_CHECK_ENTRIES) {
                                duplicates.add(name);
                            }
                        } else {
                            seenEntryNames.add(name);
                        }
                    }
                }

                // Increment AFTER successful processing
                if (!shouldUseDataDescriptor) {
                    entryCnt.incrementAndGet();
                }

                entry = zis.getNextEntry();
            }
        } catch (UnsupportedZipFeatureException zfe) {
            if (zfe.getFeature() == Feature.ENCRYPTION) {
                throw new EncryptedDocumentException(zfe);
            }
            if (zfe.getFeature() == Feature.DATA_DESCRIPTOR) {
                throw zfe;
            }
            throw new TikaException("UnsupportedZipFeature", zfe);
        }
    }

    private void updateMediaType(ZipFile zipFile, Metadata metadata) {
        MediaType type = ZIP;
        Enumeration<ZipArchiveEntry> entries = zipFile.getEntries();
        if (entries.hasMoreElements()) {
            ZipArchiveEntry first = entries.nextElement();
            if ("META-INF/MANIFEST.MF".equals(first.getName())) {
                type = JAR;
            }
        }
        setMediaTypeIfNotSpecialization(metadata, type);
    }

    private void updateMediaType(Metadata metadata) {
        setMediaTypeIfNotSpecialization(metadata, ZIP);
    }

    private void setMediaTypeIfNotSpecialization(Metadata metadata, MediaType type) {
        String incomingContentTypeString = metadata.get(Metadata.CONTENT_TYPE);
        if (incomingContentTypeString == null) {
            metadata.set(Metadata.CONTENT_TYPE, type.toString());
            return;
        }

        MediaType incomingMediaType = MediaType.parse(incomingContentTypeString);
        if (incomingMediaType == null) {
            metadata.set(Metadata.CONTENT_TYPE, type.toString());
            return;
        }

        if (!ZIP_SPECIALIZATIONS.contains(incomingMediaType)) {
            metadata.set(Metadata.CONTENT_TYPE, type.toString());
        }
    }

    private void parseZipFileEntry(ZipFile zipFile, ZipArchiveEntry entry,
                                    EmbeddedDocumentExtractor extractor, Metadata parentMetadata,
                                    XHTMLContentHandler xhtml, ParseContext context,
                                    ZipParserConfig config)
            throws SAXException, IOException, TikaException {

        String name = detectEntryName(entry, parentMetadata, context, config);

        if (entry.getGeneralPurposeBit().usesEncryption()) {
            handleEncryptedEntry(name, parentMetadata, xhtml);
            return;
        }

        Metadata entryMetadata = buildEntryMetadata(entry, name, context);

        writeEntryXhtml(name, xhtml);

        if (extractor.shouldParseEmbedded(entryMetadata)) {
            TemporaryResources tmp = new TemporaryResources();
            try (InputStream entryStream = zipFile.getInputStream(entry)) {
                TikaInputStream tis = TikaInputStream.get(entryStream, tmp, entryMetadata);
                extractor.parseEmbedded(tis, xhtml, entryMetadata, new ParseContext(), true);
            } finally {
                tmp.dispose();
            }
        }
    }

    private void parseStreamEntry(ZipArchiveInputStream zis, ZipArchiveEntry entry,
                                   EmbeddedDocumentExtractor extractor, Metadata parentMetadata,
                                   XHTMLContentHandler xhtml, ParseContext context,
                                   ZipParserConfig config)
            throws SAXException, IOException, TikaException {

        String name = detectEntryName(entry, parentMetadata, context, config);

        if (!zis.canReadEntryData(entry)) {
            if (entry.getGeneralPurposeBit().usesEncryption()) {
                handleEncryptedEntry(name, parentMetadata, xhtml);
            } else if (entry.getGeneralPurposeBit().usesDataDescriptor()
                    && entry.getMethod() == java.util.zip.ZipEntry.STORED) {
                throw new UnsupportedZipFeatureException(Feature.DATA_DESCRIPTOR, entry);
            } else {
                EmbeddedDocumentUtil.recordEmbeddedStreamException(
                        new TikaException("Can't read archive stream (" + name + ")"),
                        parentMetadata);
                if (name != null && !name.isEmpty()) {
                    xhtml.element("p", name);
                }
            }
            return;
        }

        Metadata entryMetadata = buildEntryMetadata(entry, name, context);

        writeEntryXhtml(name, xhtml);

        if (extractor.shouldParseEmbedded(entryMetadata)) {
            TemporaryResources tmp = new TemporaryResources();
            try {
                TikaInputStream tis = TikaInputStream.get(zis, tmp, entryMetadata);
                extractor.parseEmbedded(tis, xhtml, entryMetadata, new ParseContext(), true);
            } finally {
                tmp.dispose();
            }
        }
    }

    private String detectEntryName(ZipArchiveEntry entry, Metadata parentMetadata,
                                    ParseContext context, ZipParserConfig config) throws IOException {
        // If user specified an encoding, decode raw bytes with that charset
        // This avoids needing to reopen the ZipFile with a different charset
        if (config.getEntryEncoding() != null) {
            return new String(entry.getRawName(), config.getEntryEncoding());
        }

        // If charset detection is enabled, try to detect and decode
        if (config.isDetectCharsetsInEntryNames()) {
            byte[] entryName = entry.getRawName();
            byte[] extendedEntryName = entryName;
            if (0 < entryName.length && entryName.length < MIN_BYTES_FOR_DETECTING_CHARSET) {
                int len = entryName.length * (MIN_BYTES_FOR_DETECTING_CHARSET / entryName.length);
                extendedEntryName = new byte[len];
                for (int i = 0; i < len; i++) {
                    extendedEntryName[i] = entryName[i % entryName.length];
                }
            }

            try (TikaInputStream detectStream = TikaInputStream.get(extendedEntryName)) {
                Charset candidate = getEncodingDetector().detect(detectStream, parentMetadata, context);
                if (candidate != null) {
                    return new String(entry.getRawName(), candidate);
                }
            }
        }

        // Fall back to default decoding
        return entry.getName();
    }

    private void handleEncryptedEntry(String name, Metadata parentMetadata,
                                       XHTMLContentHandler xhtml) throws SAXException {
        EmbeddedDocumentUtil.recordEmbeddedStreamException(
                new EncryptedDocumentException("stream (" + name + ") is encrypted"),
                parentMetadata);
        if (name != null && !name.isEmpty()) {
            xhtml.element("p", name);
        }
    }

    private Metadata buildEntryMetadata(ZipArchiveEntry entry, String name, ParseContext context)
            throws IOException, TikaException, SAXException {
        Metadata entryMetadata = Metadata.newInstance(context);

        if (name != null && name.length() > 0) {
            name = name.replace("\\", "/");
            entryMetadata.set(TikaCoreProperties.RESOURCE_NAME_KEY, name);
            entryMetadata.set(TikaCoreProperties.INTERNAL_PATH, name);
        }

        FileTime creationTime = entry.getCreationTime();
        if (creationTime != null) {
            entryMetadata.set(TikaCoreProperties.CREATED, creationTime.toInstant().toString());
        }
        FileTime modifiedTime = entry.getLastModifiedTime();
        if (modifiedTime != null) {
            entryMetadata.set(TikaCoreProperties.MODIFIED, modifiedTime.toInstant().toString());
        }

        long size = entry.getSize();
        if (size >= 0) {
            entryMetadata.set(Metadata.CONTENT_LENGTH, Long.toString(size));
            entryMetadata.set(Zip.UNCOMPRESSED_SIZE, Long.toString(size));
        }
        long compressedSize = entry.getCompressedSize();
        if (compressedSize >= 0) {
            entryMetadata.set(Zip.COMPRESSED_SIZE, Long.toString(compressedSize));
        }

        entryMetadata.set(Zip.COMPRESSION_METHOD, entry.getMethod());

        long crc = entry.getCrc();
        if (crc >= 0) {
            entryMetadata.set(Zip.CRC32, Long.toString(crc));
        }

        int unixMode = entry.getUnixMode();
        if (unixMode != 0) {
            entryMetadata.set(Zip.UNIX_MODE, unixMode);
        }

        entryMetadata.set(Zip.PLATFORM, entry.getPlatform());
        entryMetadata.set(Zip.VERSION_MADE_BY, entry.getVersionMadeBy());

        String entryComment = entry.getComment();
        if (entryComment != null && !entryComment.isEmpty()) {
            entryMetadata.set(Zip.COMMENT, entryComment);
        }

        return entryMetadata;
    }

    private void writeEntryXhtml(String name, XHTMLContentHandler xhtml) throws SAXException {
        if (name != null && name.length() > 0) {
            org.xml.sax.helpers.AttributesImpl attributes = new org.xml.sax.helpers.AttributesImpl();
            attributes.addAttribute("", "class", "class", "CDATA", "embedded");
            attributes.addAttribute("", "id", "id", "CDATA", name);
            xhtml.startElement("div", attributes);
            xhtml.endElement("div");
        }
    }

    /**
     * Performs integrity check by streaming through the ZIP and comparing
     * local file headers against the central directory entries.
     *
     * @param tis                     the TikaInputStream (must be rewound)
     * @param metadata                the parent metadata to record results
     * @param centralDirectoryEntries entry names from the central directory
     * @param config                  the parser configuration
     */
    private void performIntegrityCheck(TikaInputStream tis, Metadata metadata,
                                        Set<String> centralDirectoryEntries,
                                        ZipParserConfig config) throws IOException {

        String encoding = config.getEntryEncoding() != null
                ? config.getEntryEncoding().name()
                : null;

        Set<String> seenInStream = new LinkedHashSet<>();
        List<String> duplicates = new ArrayList<>();
        List<String> localHeaderOnly = new ArrayList<>();

        try (ZipArchiveInputStream zis = new ZipArchiveInputStream(tis, encoding, true, true)) {
            ZipArchiveEntry entry;
            while ((entry = zis.getNextZipEntry()) != null) {
                String name = entry.getName();

                // Check for duplicates
                if (seenInStream.contains(name)) {
                    if (duplicates.size() < MAX_INTEGRITY_CHECK_ENTRIES) {
                        duplicates.add(name);
                    }
                } else {
                    seenInStream.add(name);
                }

                // Check for entries not in central directory
                if (!centralDirectoryEntries.contains(name)) {
                    if (localHeaderOnly.size() < MAX_INTEGRITY_CHECK_ENTRIES) {
                        localHeaderOnly.add(name);
                    }
                }
            }
        } catch (IOException e) {
            // If streaming fails, we still record what we found
        }

        // Find entries in central directory but not in local headers
        List<String> centralOnly = new ArrayList<>();
        for (String cdEntry : centralDirectoryEntries) {
            if (!seenInStream.contains(cdEntry)) {
                if (centralOnly.size() < MAX_INTEGRITY_CHECK_ENTRIES) {
                    centralOnly.add(cdEntry);
                }
            }
        }

        // Record results
        boolean passed = duplicates.isEmpty() && localHeaderOnly.isEmpty() && centralOnly.isEmpty();
        metadata.set(Zip.INTEGRITY_CHECK_RESULT, passed ? "PASS" : "FAIL");

        for (String dup : duplicates) {
            metadata.add(Zip.DUPLICATE_ENTRY_NAMES, dup);
        }
        for (String local : localHeaderOnly) {
            metadata.add(Zip.LOCAL_HEADER_ONLY_ENTRIES, local);
        }
        for (String cd : centralOnly) {
            metadata.add(Zip.CENTRAL_DIRECTORY_ONLY_ENTRIES, cd);
        }
    }
}
