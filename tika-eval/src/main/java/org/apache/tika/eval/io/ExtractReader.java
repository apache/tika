package org.apache.tika.eval.io;

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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
import org.apache.commons.compress.compressors.z.ZCompressorInputStream;
import org.apache.tika.config.TikaConfig;
import org.apache.tika.exception.TikaException;
import org.apache.tika.io.IOUtils;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.serialization.JsonMetadataList;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.RecursiveParserWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

public class ExtractReader {
    private static final Logger LOG = LoggerFactory.getLogger(ExtractReader.class);

    public static final long IGNORE_LENGTH = -1L;

    public enum ALTER_METADATA_LIST {
        AS_IS,  //leave the metadata list as is
        FIRST_ONLY, //take only the metadata list for the "container" document
        CONCATENATE_CONTENT_INTO_FIRST // concatenate all of the content into the first
    }

    private TikaConfig tikaConfig = TikaConfig.getDefaultConfig();

    private final ALTER_METADATA_LIST alterMetadataList;
    private final long minExtractLength;
    private final long maxExtractLength;

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
            throw new IllegalArgumentException("minExtractLength("+minExtractLength+
                    ") must be < maxExtractLength("+maxExtractLength+")");
        }
    }
    public List<Metadata> loadExtract(Path extractFile) throws ExtractReaderException {

        List<Metadata> metadataList = null;
        if (extractFile == null || !Files.isRegularFile(extractFile)) {
            throw new ExtractReaderException(ExtractReaderException.TYPE.NO_EXTRACT_FILE);
        }

        FileSuffixes fileSuffixes = parseSuffixes(extractFile.getFileName().toString());
        if (fileSuffixes.txtOrJson == null) {
            throw new ExtractReaderException(ExtractReaderException.TYPE.INCORRECT_EXTRACT_FILE_SUFFIX);
        }
        if (! Files.isRegularFile(extractFile)) {
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
                if (fileSuffixes.compression.equals("bz2")) {
                    is = new BZip2CompressorInputStream(is);
                } else if (fileSuffixes.compression.equals("gz")
                        || fileSuffixes.compression.equals("gzip")) {
                    is = new GzipCompressorInputStream(is);
                } else if (fileSuffixes.compression.equals("zip")) {
                    is = new ZCompressorInputStream(is);
                } else {
                    LOG.warn("Can't yet process compression of type: {}", fileSuffixes.compression);
                    return metadataList;
                }
            }
            reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new ExtractReaderException(ExtractReaderException.TYPE.IO_EXCEPTION);
        }

        try {
            if (fileSuffixes.txtOrJson.equals("json")) {
                metadataList = JsonMetadataList.fromJson(reader);
                if (alterMetadataList.equals(ALTER_METADATA_LIST.FIRST_ONLY) && metadataList.size() > 1) {
                    while (metadataList.size() > 1) {
                        metadataList.remove(metadataList.size()-1);
                    }
                } else if (alterMetadataList.equals(ALTER_METADATA_LIST.AS_IS.CONCATENATE_CONTENT_INTO_FIRST) &&
                        metadataList.size() > 1) {
                    StringBuilder sb = new StringBuilder();
                    Metadata containerMetadata = metadataList.get(0);
                    for (int i = 0; i < metadataList.size(); i++) {
                        Metadata m = metadataList.get(i);
                        String c = m.get(RecursiveParserWrapper.TIKA_CONTENT);
                        if (c != null) {
                            sb.append(c);
                            sb.append(" ");
                        }
                    }
                    containerMetadata.set(RecursiveParserWrapper.TIKA_CONTENT, sb.toString());
                    while (metadataList.size() > 1) {
                        metadataList.remove(metadataList.size()-1);
                    }
                }
            } else {
                metadataList = generateListFromTextFile(reader, fileSuffixes);
            }
        } catch (IOException e) {
            throw new ExtractReaderException(ExtractReaderException.TYPE.IO_EXCEPTION);
        } catch (TikaException e) {
            throw new ExtractReaderException(ExtractReaderException.TYPE.EXTRACT_PARSE_EXCEPTION);
        } finally {
            IOUtils.closeQuietly(reader);
            IOUtils.closeQuietly(is);
        }
        return metadataList;
    }

    private List<Metadata> generateListFromTextFile(Reader reader,
                                                           FileSuffixes fileSuffixes) throws IOException {
        List<Metadata> metadataList = new ArrayList<>();
        String content = IOUtils.toString(reader);
        Metadata m = new Metadata();
        m.set(RecursiveParserWrapper.TIKA_CONTENT, content);
        //Let's hope the file name has a suffix that can
        //be used to determine the mime.  Could be wrong or missing,
        //but better than nothing.
        m.set(Metadata.RESOURCE_NAME_KEY, fileSuffixes.originalFileName);

        MediaType mimeType = tikaConfig.getMimeRepository().detect(null, m);
        if (mimeType != null) {
            m.set(Metadata.CONTENT_TYPE, mimeType.toString());
        }
        metadataList.add(m);
        return metadataList;

    }

    protected static FileSuffixes parseSuffixes(String fName) {
        FileSuffixes fileSuffixes = new FileSuffixes();
        if (fName == null) {
            return fileSuffixes;
        }
        Matcher m = Pattern.compile("^(.*?)\\.(json|txt)(?:\\.(bz2|gz(?:ip)?|zip))?$").matcher(fName);
        if (m.find()) {
            fileSuffixes.originalFileName = m.group(1);
            fileSuffixes.txtOrJson = m.group(2);
            fileSuffixes.compression = m.group(3);
        }
        return fileSuffixes;
    }

    private static class FileSuffixes {
        String compression;
        String txtOrJson;
        String originalFileName;
    }
}
