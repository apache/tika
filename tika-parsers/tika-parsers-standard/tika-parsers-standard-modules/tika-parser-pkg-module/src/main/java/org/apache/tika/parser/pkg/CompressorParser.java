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

import static org.apache.tika.detect.zip.CompressorConstants.BROTLI;
import static org.apache.tika.detect.zip.CompressorConstants.BZIP;
import static org.apache.tika.detect.zip.CompressorConstants.BZIP2;
import static org.apache.tika.detect.zip.CompressorConstants.COMPRESS;
import static org.apache.tika.detect.zip.CompressorConstants.DEFLATE64;
import static org.apache.tika.detect.zip.CompressorConstants.GZIP;
import static org.apache.tika.detect.zip.CompressorConstants.GZIP_ALT;
import static org.apache.tika.detect.zip.CompressorConstants.LZ4_BLOCK;
import static org.apache.tika.detect.zip.CompressorConstants.LZ4_FRAMED;
import static org.apache.tika.detect.zip.CompressorConstants.LZMA;
import static org.apache.tika.detect.zip.CompressorConstants.PACK;
import static org.apache.tika.detect.zip.CompressorConstants.SNAPPY_FRAMED;
import static org.apache.tika.detect.zip.CompressorConstants.XZ;
import static org.apache.tika.detect.zip.CompressorConstants.ZLIB;
import static org.apache.tika.detect.zip.CompressorConstants.ZSTD;
import static org.apache.tika.metadata.HttpHeaders.CONTENT_TYPE;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.apache.commons.compress.MemoryLimitException;
import org.apache.commons.compress.compressors.CompressorException;
import org.apache.commons.compress.compressors.CompressorInputStream;
import org.apache.commons.compress.compressors.CompressorStreamFactory;
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream;
import org.apache.commons.compress.compressors.deflate.DeflateCompressorInputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
import org.apache.commons.compress.compressors.gzip.GzipUtils;
import org.apache.commons.compress.compressors.lzma.LZMACompressorInputStream;
import org.apache.commons.compress.compressors.pack200.Pack200CompressorInputStream;
import org.apache.commons.compress.compressors.snappy.FramedSnappyCompressorInputStream;
import org.apache.commons.compress.compressors.snappy.SnappyCompressorInputStream;
import org.apache.commons.compress.compressors.xz.XZCompressorInputStream;
import org.apache.commons.compress.compressors.z.ZCompressorInputStream;
import org.apache.commons.io.input.CloseShieldInputStream;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import org.apache.tika.config.Field;
import org.apache.tika.exception.TikaException;
import org.apache.tika.exception.TikaMemoryLimitException;
import org.apache.tika.extractor.EmbeddedDocumentExtractor;
import org.apache.tika.extractor.EmbeddedDocumentUtil;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.AbstractParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.XHTMLContentHandler;

/**
 * Parser for various compression formats.
 */
public class CompressorParser extends AbstractParser {

    /**
     * Serial version UID
     */
    private static final long serialVersionUID = 2793565792967222459L;


    private static Set<MediaType> SUPPORTED_TYPES;
    private static Map<String, String> MIMES_TO_NAME;

    static {
        Set<MediaType> TMP_SET = new HashSet<>(MediaType
                .set(BZIP, BZIP2, DEFLATE64, GZIP, GZIP_ALT, LZ4_FRAMED, COMPRESS, XZ, PACK,
                        SNAPPY_FRAMED, ZLIB, LZMA));
        try {
            Class.forName("org.brotli.dec.BrotliInputStream");
            TMP_SET.add(BROTLI);
        } catch (NoClassDefFoundError | ClassNotFoundException e) {
            //swallow
        }
        try {
            Class.forName("com.github.luben.zstd.ZstdInputStream");
            TMP_SET.add(ZSTD);
        } catch (NoClassDefFoundError | ClassNotFoundException e) {
            //swallow
        }
        SUPPORTED_TYPES = Collections.unmodifiableSet(TMP_SET);
    }

    static {
        //map the mime type strings to the compressor stream names
        Map<String, String> tmpMimesToName = new HashMap<>();
        tmpMimesToName.put(BZIP2.toString(), CompressorStreamFactory.BZIP2);
        tmpMimesToName.put(GZIP.toString(), CompressorStreamFactory.GZIP);
        tmpMimesToName.put(LZ4_FRAMED.toString(), CompressorStreamFactory.LZ4_FRAMED);
        tmpMimesToName.put(LZ4_BLOCK.toString(), CompressorStreamFactory.LZ4_BLOCK);
        tmpMimesToName.put(XZ.toString(), CompressorStreamFactory.XZ);
        tmpMimesToName.put(PACK.toString(), CompressorStreamFactory.PACK200);
        tmpMimesToName.put(SNAPPY_FRAMED.toString(), CompressorStreamFactory.SNAPPY_FRAMED);
        tmpMimesToName.put(ZLIB.toString(), CompressorStreamFactory.DEFLATE);
        tmpMimesToName.put(COMPRESS.toString(), CompressorStreamFactory.Z);
        tmpMimesToName.put(LZMA.toString(), CompressorStreamFactory.LZMA);
        tmpMimesToName.put(BROTLI.toString(), CompressorStreamFactory.BROTLI);
        tmpMimesToName.put(ZSTD.toString(), CompressorStreamFactory.ZSTANDARD);
        MIMES_TO_NAME = Collections.unmodifiableMap(tmpMimesToName);
    }


    private int memoryLimitInKb = 100000;//100MB

    /**
     * @param stream stream
     * @return MediaType
     */
    private static MediaType getMediaType(CompressorInputStream stream) {
        // TODO Add support for the remaining CompressorInputStream formats:
        //   LZ4
        //   LZWInputStream -> UnshrinkingInputStream
        if (stream instanceof BZip2CompressorInputStream) {
            return BZIP2;
        } else if (stream instanceof GzipCompressorInputStream) {
            return GZIP;
        } else if (stream instanceof XZCompressorInputStream) {
            return XZ;
        } else if (stream instanceof DeflateCompressorInputStream) {
            return ZLIB;
        } else if (stream instanceof ZCompressorInputStream) {
            return COMPRESS;
        } else if (stream instanceof Pack200CompressorInputStream) {
            return PACK;
        } else if (stream instanceof FramedSnappyCompressorInputStream ||
                stream instanceof SnappyCompressorInputStream) {
            // TODO Add unit tests for this format
            return SNAPPY_FRAMED;
        } else if (stream instanceof LZMACompressorInputStream) {
            return LZMA;
        } else {
            return MediaType.OCTET_STREAM;
        }
    }


    public Set<MediaType> getSupportedTypes(ParseContext context) {
        return SUPPORTED_TYPES;
    }

    public void parse(InputStream stream, ContentHandler handler, Metadata metadata,
                      ParseContext context) throws IOException, SAXException, TikaException {
        // At the end we want to close the compression stream to release
        // any associated resources, but the underlying document stream
        // should not be closed
        if (stream.markSupported()) {
            stream = CloseShieldInputStream.wrap(stream);
        } else {
            // Ensure that the stream supports the mark feature
            stream = new BufferedInputStream(CloseShieldInputStream.wrap(stream));
        }

        CompressorInputStream cis;
        try {
            CompressorParserOptions options =
                    context.get(CompressorParserOptions.class, metadata1 -> false);
            CompressorStreamFactory factory =
                    new CompressorStreamFactory(options.decompressConcatenated(metadata),
                            memoryLimitInKb);
            //if we've already identified it via autodetect
            //trust that and go with the appropriate name
            //to avoid calling CompressorStreamFactory.detect() twice
            String name = getStreamName(metadata);
            if (name != null) {
                cis = factory.createCompressorInputStream(name, stream);
            } else {
                cis = factory.createCompressorInputStream(stream);
                MediaType type = getMediaType(cis);
                if (!type.equals(MediaType.OCTET_STREAM)) {
                    metadata.set(CONTENT_TYPE, type.toString());
                }
            }
        } catch (CompressorException e) {
            if (e.getCause() != null && e.getCause() instanceof MemoryLimitException) {
                throw new TikaMemoryLimitException(e.getMessage());
            }
            throw new TikaException("Unable to uncompress document stream", e);
        }


        XHTMLContentHandler xhtml = new XHTMLContentHandler(handler, metadata);
        xhtml.startDocument();

        try {
            Metadata entrydata = new Metadata();
            String name = metadata.get(TikaCoreProperties.RESOURCE_NAME_KEY);
            if (name != null) {
                if (name.endsWith(".tbz") || name.endsWith(".tbz2")) {
                    name = name.substring(0, name.lastIndexOf(".")) + ".tar";
                } else if (name.endsWith(".bz") || name.endsWith(".bz2") || name.endsWith(".xz") ||
                        name.endsWith(".zlib") || name.endsWith(".pack") || name.endsWith(".br")) {
                    name = name.substring(0, name.lastIndexOf("."));
                } else if (name.length() > 0) {
                    name = GzipUtils.getUncompressedFilename(name);
                }
                entrydata.set(TikaCoreProperties.RESOURCE_NAME_KEY, name);
            }

            // Use the delegate parser to parse the compressed document
            EmbeddedDocumentExtractor extractor =
                    EmbeddedDocumentUtil.getEmbeddedDocumentExtractor(context);
            if (extractor.shouldParseEmbedded(entrydata)) {
                extractor.parseEmbedded(cis, xhtml, entrydata, true);
            }
        } finally {
            cis.close();
        }

        xhtml.endDocument();
    }

    /**
     * @param metadata
     * @return CompressorStream name based on the content-type value
     * in metadata or <code>null</code> if not found
     * ind
     */
    private String getStreamName(Metadata metadata) {
        String mimeString = metadata.get(Metadata.CONTENT_TYPE);
        if (mimeString == null) {
            return null;
        }
        return MIMES_TO_NAME.get(mimeString);
    }

    @Field
    public void setMemoryLimitInKb(int memoryLimitInKb) {
        this.memoryLimitInKb = memoryLimitInKb;
    }

}
