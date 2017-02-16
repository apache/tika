package org.apache.tika.eval.io;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
import org.apache.commons.compress.compressors.z.ZCompressorInputStream;
import org.apache.commons.io.IOUtils;
import org.apache.tika.config.TikaConfig;
import org.apache.tika.exception.TikaException;
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

    public enum ALTER_METADATA_LIST {
        AS_IS,  //leave the metadata list as is
        FIRST_ONLY, //take only the metadata list for the "container" document
        CONCATENATE_CONTENT_INTO_FIRST // concatenate all of the content into the first
    }
    private final static Logger LOGGER = LoggerFactory.getLogger(ExtractReader.class);
    TikaConfig tikaConfig = TikaConfig.getDefaultConfig();

    public List<Metadata> loadExtract(Path thisFile, ALTER_METADATA_LIST alterExtractList) {
        List<Metadata> metadataList = null;
        if (thisFile == null || !Files.isRegularFile(thisFile)) {
            return metadataList;
        }
        Reader reader = null;
        InputStream is = null;
        FileSuffixes fileSuffixes = parseSuffixes(thisFile.getFileName().toString());
        if (fileSuffixes.txtOrJson == null) {
            LOGGER.warn("file must end with .txt or .json: "+thisFile.getFileName().toString());
            return metadataList;
        }

        try {
            is = Files.newInputStream(thisFile);
            if (fileSuffixes.compression != null) {
                if (fileSuffixes.compression.equals("bz2")) {
                    is = new BZip2CompressorInputStream(is);
                } else if (fileSuffixes.compression.equals("gz")) {
                    is = new GzipCompressorInputStream(is);
                } else if (fileSuffixes.compression.equals("zip")) {
                    is = new ZCompressorInputStream(is);
                } else {
                    LOGGER.warn("Can't yet process compression of type: "+fileSuffixes.compression);
                }
            }
                reader = new BufferedReader(new InputStreamReader(is, "UTF-8"));

            if (fileSuffixes.txtOrJson.equals("json")) {
                metadataList = JsonMetadataList.fromJson(reader);
                if (alterExtractList.equals(ALTER_METADATA_LIST.FIRST_ONLY) && metadataList.size() > 1) {
                    while (metadataList.size() > 1) {
                        metadataList.remove(metadataList.size()-1);
                    }
                } else if (alterExtractList.equals(ALTER_METADATA_LIST.AS_IS.CONCATENATE_CONTENT_INTO_FIRST) &&
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
            LOGGER.warn("couldn't open:" + thisFile.toAbsolutePath(), e);
        } catch (TikaException e) {
            LOGGER.warn("couldn't open:" + thisFile.toAbsolutePath(), e);
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
