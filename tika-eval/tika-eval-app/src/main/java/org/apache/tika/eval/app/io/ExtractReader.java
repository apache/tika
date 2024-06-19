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
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
import org.apache.commons.compress.compressors.z.ZCompressorInputStream;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.tika.config.TikaConfig;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.mime.MediaType;
import org.apache.tika.sax.ToTextContentHandler;
import org.apache.tika.sax.ToXMLContentHandler;
import org.apache.tika.serialization.JsonMetadataList;


public class ExtractReader {
    public static final long IGNORE_LENGTH = -1L;
    private static final Logger LOG = LoggerFactory.getLogger(ExtractReader.class);
    private final ALTER_METADATA_LIST alterMetadataList;
    private final long minExtractLength;
    private final long maxExtractLength;
    private TikaConfig tikaConfig = TikaConfig.getDefaultConfig();

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
            throw new ExtractReaderException(ExtractReaderException.TYPE.IO_EXCEPTION);
        }

        if (length == 0L) {
            throw new ExtractReaderException(ExtractReaderException.TYPE.ZERO_BYTE_EXTRACT_FILE);
        }

        if (minExtractLength > IGNORE_LENGTH && length < minExtractLength) {
            throw new ExtractReaderException(ExtractReaderException.TYPE.EXTRACT_FILE_TOO_SHORT);
        }
        if (maxExtractLength > IGNORE_LENGTH && length > maxExtractLength) {
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
            throw new ExtractReaderException(ExtractReaderException.TYPE.IO_EXCEPTION);
        }

        try {
            if (fileSuffixes.format == FileSuffixes.FORMAT.JSON) {
                metadataList = JsonMetadataList.fromJson(reader);
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
            throw new ExtractReaderException(ExtractReaderException.TYPE.IO_EXCEPTION);
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
            m.set(TikaCoreProperties.TIKA_CONTENT_HANDLER, ToXMLContentHandler.class.getSimpleName());
        } else if (fileSuffixes.format == FileSuffixes.FORMAT.TXT) {
            m.set(TikaCoreProperties.TIKA_CONTENT_HANDLER, ToTextContentHandler.class.getSimpleName());
        }
        //Let's hope the file name has a suffix that can
        //be used to determine the mime.  Could be wrong or missing,
        //but better than nothing.
        m.set(TikaCoreProperties.RESOURCE_NAME_KEY, fileSuffixes.originalFileName);

        MediaType mimeType = tikaConfig
                .getMimeRepository()
                .detect(null, m);
        if (mimeType != null) {
            m.set(Metadata.CONTENT_TYPE, mimeType.toString());
        }
        metadataList.add(m);
        return metadataList;

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
