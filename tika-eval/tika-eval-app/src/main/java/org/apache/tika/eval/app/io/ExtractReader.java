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
package org.apache.tika.eval.app.io;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
import org.apache.commons.compress.compressors.z.ZCompressorInputStream;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.tika.metadata.HttpHeaders;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.mime.MediaType;
import org.apache.tika.mime.MimeTypes;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.BasicContentHandlerFactory.HANDLER_TYPE;
import org.apache.tika.serialization.JsonMetadataList;


public class ExtractReader {
    public static final long IGNORE_LENGTH = -1L;
    private static final Logger LOG = LoggerFactory.getLogger(ExtractReader.class);
    private final ALTER_METADATA_LIST alterMetadataList;
    private final long minExtractLength;
    private final long maxExtractLength;
    private final MimeTypes mimeTypes = MimeTypes.getDefaultMimeTypes();

    /**
     * Reads full extract, no modification of metadata list, no min or max extract length checking
     */
    public ExtractReader() {
        this(ALTER_METADATA_LIST.AS_IS, IGNORE_LENGTH, IGNORE_LENGTH);
    }

    public ExtractReader(ALTER_METADATA_LIST alterMetadataList) {
        this(alterMetadataList, IGNORE_LENGTH, IGNORE_LENGTH);
    }

    public ExtractReader(ALTER_METADATA_LIST alterMetadataList, long minExtractLength, long maxExtractLength) {
        this.alterMetadataList = alterMetadataList;
        this.minExtractLength = minExtractLength;
        this.maxExtractLength = maxExtractLength;
        if (maxExtractLength > IGNORE_LENGTH && minExtractLength >= maxExtractLength) {
            throw new IllegalArgumentException("minExtractLength(" + minExtractLength + ") must be < maxExtractLength(" + maxExtractLength + ")");
        }
    }

    protected static FileSuffixes parseSuffixes(String fName) {
        FileSuffixes fileSuffixes = new FileSuffixes();
        if (fName == null) {
            return fileSuffixes;
        }
        Matcher m = Pattern
                .compile("(?i)^(.*?)\\.(json|txt|x?html)(?:\\.(bz2|gz(?:ip)?|zip))?$")
                .matcher(fName);
        if (m.find()) {
            fileSuffixes.originalFileName = m.group(1);
            fileSuffixes.setFormat(m.group(2));
            fileSuffixes.compression = m.group(3);
        }
        return fileSuffixes;
    }

    public List<Metadata> loadExtract(Path extractFile) throws ExtractReaderException {

        List<Metadata> metadataList = null;
        if (extractFile == null || !Files.isRegularFile(extractFile)) {
            throw new ExtractReaderException(ExtractReaderException.TYPE.NO_EXTRACT_FILE);
        }

        FileSuffixes fileSuffixes = parseSuffixes(extractFile
                .getFileName()
                .toString());
        if (fileSuffixes.format == null) {
            throw new ExtractReaderException(ExtractReaderException.TYPE.INCORRECT_EXTRACT_FILE_SUFFIX);
        }
        if (!Files.isRegularFile(extractFile)) {
            throw new ExtractReaderException(ExtractReaderException.TYPE.NO_EXTRACT_FILE);
        }

        long length = -1L;
        try {
            length = Files.size(extractFile);
        } catch (IOException e) {
            throw new ExtractReaderException(ExtractReaderException.TYPE.IO_EXCEPTION, e);
        }

        if (length == 0L) {
            throw new ExtractReaderException(ExtractReaderException.TYPE.ZERO_BYTE_EXTRACT_FILE);
        }

        if (minExtractLength > IGNORE_LENGTH && length < minExtractLength) {
            LOG.info("minExtractLength {} > IGNORE_LENGTH {} and length {} < minExtractLength {} for file '{}'", 
                    minExtractLength, IGNORE_LENGTH, length, minExtractLength, extractFile);
            throw new ExtractReaderException(ExtractReaderException.TYPE.EXTRACT_FILE_TOO_SHORT);
        }
        if (maxExtractLength > IGNORE_LENGTH && length > maxExtractLength) {
            LOG.info("maxExtractLength {} > IGNORE_LENGTH {} and length {} > maxExtractLength {} for file '{}'", 
                    maxExtractLength, IGNORE_LENGTH, length, maxExtractLength, extractFile);
            throw new ExtractReaderException(ExtractReaderException.TYPE.EXTRACT_FILE_TOO_LONG);
        }

        Reader reader = null;
        InputStream is = null;
        try {
            is = Files.newInputStream(extractFile);
            if (fileSuffixes.compression != null) {
                switch (fileSuffixes.compression) {
                    case "bz2":
                        is = new BZip2CompressorInputStream(is);
                        break;
                    case "gz":
                    case "gzip":
                        is = new GzipCompressorInputStream(is);
                        break;
                    case "zip":
                        is = new ZCompressorInputStream(is);
                        break;
                    default:
                        LOG.warn("Can't yet process compression of type: {}", fileSuffixes.compression);
                        return metadataList;
                }
            }
            reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new ExtractReaderException(ExtractReaderException.TYPE.IO_EXCEPTION, e);
        }

        try {
            if (fileSuffixes.format == FileSuffixes.FORMAT.JSON) {
                metadataList = JsonMetadataList.fromJson(reader);
                for (Metadata m : metadataList) {
                    normalizeLegacyKeys(m);
                }
                if (alterMetadataList.equals(ALTER_METADATA_LIST.FIRST_ONLY) && metadataList.size() > 1) {
                    while (metadataList.size() > 1) {
                        metadataList.remove(metadataList.size() - 1);
                    }
                } else if (alterMetadataList.equals(ALTER_METADATA_LIST.AS_IS.CONCATENATE_CONTENT_INTO_FIRST) && metadataList.size() > 1) {
                    StringBuilder sb = new StringBuilder();
                    Metadata containerMetadata = metadataList.get(0);
                    for (Metadata m : metadataList) {
                        String c = m.get(TikaCoreProperties.TIKA_CONTENT);
                        if (c != null) {
                            sb.append(c);
                            sb.append(" ");
                        }
                    }
                    containerMetadata.set(TikaCoreProperties.TIKA_CONTENT, sb.toString());
                    while (metadataList.size() > 1) {
                        metadataList.remove(metadataList.size() - 1);
                    }
                }
            } else {
                metadataList = generateListFromTextFile(reader, fileSuffixes);
            }
        } catch (IOException e) {
            throw new ExtractReaderException(ExtractReaderException.TYPE.IO_EXCEPTION, e);
        } finally {
            IOUtils.closeQuietly(reader);
            IOUtils.closeQuietly(is);
        }
        return metadataList;
    }

    private List<Metadata> generateListFromTextFile(Reader reader, FileSuffixes fileSuffixes) throws IOException {
        List<Metadata> metadataList = new ArrayList<>();
        String content = IOUtils.toString(reader);
        Metadata m = new Metadata();
        m.set(TikaCoreProperties.TIKA_CONTENT, content);
        if (fileSuffixes.format == FileSuffixes.FORMAT.HTML) {
            m.set(TikaCoreProperties.TIKA_CONTENT_HANDLER_TYPE, HANDLER_TYPE.XML.name());
        } else if (fileSuffixes.format == FileSuffixes.FORMAT.TXT) {
            m.set(TikaCoreProperties.TIKA_CONTENT_HANDLER_TYPE, HANDLER_TYPE.TEXT.name());
        }
        //Let's hope the file name has a suffix that can
        //be used to determine the mime.  Could be wrong or missing,
        //but better than nothing.
        m.set(TikaCoreProperties.RESOURCE_NAME_KEY, fileSuffixes.originalFileName);

        MediaType mimeType = mimeTypes.detect(null, m, new ParseContext());
        if (mimeType != null) {
            m.set(HttpHeaders.CONTENT_TYPE, mimeType.toString());
        }
        metadataList.add(m);
        return metadataList;

    }

    // Pre-4.0 extract key -> 4.0 key, for the Tika-native fields tika-eval reads. Digest keys are
    // handled by the prefix rule in normalizeLegacyKeys; Content-Type/Content-Length are standard
    // names (unchanged) so they are not listed. New-side keys come from the live constants so this
    // can't drift from the 4.0 declarations. X-TIKA:content_handler (the handler's simple class
    // name) has no 4.0 equivalent -- tk:content-handler-type carries a different value shape
    // (a HANDLER_TYPE enum name), so it is deliberately not remapped here.
    private static final Map<String, String> LEGACY_KEY_MAP = Map.ofEntries(
            Map.entry("X-TIKA:content", TikaCoreProperties.TIKA_CONTENT.getName()),
            Map.entry("X-TIKA:embedded_depth", TikaCoreProperties.EMBEDDED_DEPTH.getName()),
            Map.entry("X-TIKA:embedded_resource_path", TikaCoreProperties.EMBEDDED_RESOURCE_PATH.getName()),
            Map.entry("X-TIKA:final_embedded_resource_path", TikaCoreProperties.FINAL_EMBEDDED_RESOURCE_PATH.getName()),
            Map.entry("X-TIKA:parse_time_millis", TikaCoreProperties.PARSE_TIME_MILLIS.getName()),
            Map.entry("X-TIKA:resourceName", TikaCoreProperties.RESOURCE_NAME_KEY.getName()),
            Map.entry("X-TIKA:detectedEncoding", TikaCoreProperties.DETECTED_ENCODING.getName()),
            Map.entry("X-TIKA:encodingDetector", TikaCoreProperties.ENCODING_DETECTOR.getName()),
            Map.entry("Content-Type-Hint", TikaCoreProperties.CONTENT_TYPE_HINT.getName()),
            Map.entry("embeddedResourceType", TikaCoreProperties.EMBEDDED_RESOURCE_TYPE.getName()),
            Map.entry("X-TIKA:EXCEPTION:container_exception", TikaCoreProperties.CONTAINER_EXCEPTION.getName()),
            Map.entry("X-TIKA:EXCEPTION:embedded_exception", TikaCoreProperties.EMBEDDED_EXCEPTION.getName()));

    private static final String LEGACY_DIGEST_PREFIX = TikaCoreProperties.LEGACY_TIKA_META_PREFIX
            + "digest" + TikaCoreProperties.NAMESPACE_PREFIX_DELIMITER;

    /**
     * Pre-4.0 extracts (e.g. 4.0.0-beta-1) key Tika-native fields under X-TIKA:/camelCase names.
     * Normalize the fields tika-eval reads to their 4.0 tk: keys so a cross-version compare reflects
     * real diffs, not the rename. Harmless on 4.0 extracts: the legacy keys are simply absent.
     */
    private static void normalizeLegacyKeys(Metadata m) {
        for (Map.Entry<String, String> e : LEGACY_KEY_MAP.entrySet()) {
            remapLegacyKey(m, e.getKey(), e.getValue());
        }
        // digest keys: X-TIKA:digest:<alg> -> tk:digest:<alg> (algorithm unchanged; MD5 drives
        // embedded-doc matching). names() is a snapshot, so remapping while iterating is safe.
        for (String name : m.names()) {
            if (name.startsWith(LEGACY_DIGEST_PREFIX)) {
                remapLegacyKey(m, name, TikaCoreProperties.TIKA_META_PREFIX
                        + name.substring(TikaCoreProperties.LEGACY_TIKA_META_PREFIX.length()));
            }
        }
    }

    private static void remapLegacyKey(Metadata m, String legacyKey, String modernKey) {
        String[] legacyVals = m.getValues(legacyKey);
        if (legacyVals.length == 0) {
            return;
        }
        String[] modernVals = m.getValues(modernKey);
        if (modernVals.length > 0) {
            // Both present: safe only if identical. Fail loud rather than silently clobber a value.
            if (!Arrays.equals(legacyVals, modernVals)) {
                throw new IllegalStateException("Extract has both legacy key '" + legacyKey
                        + "' and modern key '" + modernKey + "' with different values; legacy-key "
                        + "normalization would clobber. Extract is inconsistent.");
            }
        } else {
            for (int i = 0; i < legacyVals.length; i++) {
                if (i == 0) {
                    m.setTrusted(modernKey, legacyVals[i]);
                } else {
                    m.addTrusted(modernKey, legacyVals[i]);
                }
            }
        }
        m.remove(legacyKey);
    }

    public enum ALTER_METADATA_LIST {
        AS_IS,  //leave the metadata list as is
        FIRST_ONLY, //take only the metadata list for the "container" document
        CONCATENATE_CONTENT_INTO_FIRST // concatenate all of the content into the first
    }

    private static class FileSuffixes {

        String compression;
        FORMAT format;
        String originalFileName;

        public void setFormat(String fmt) {
            String lc = fmt.toLowerCase(Locale.ENGLISH);
            if (lc.equals("json")) {
                format = FORMAT.JSON;
            } else if (lc.equals("txt")) {
                format = FORMAT.TXT;
            } else if (lc.contains("html")) {
                format = FORMAT.HTML;
            } else {
                throw new IllegalArgumentException("extract must end in .json, .txt or .xhtml");
            }
        }

        enum FORMAT {
            TXT, HTML, JSON
        }
    }
}
