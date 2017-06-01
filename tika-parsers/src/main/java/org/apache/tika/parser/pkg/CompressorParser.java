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

import static org.apache.tika.metadata.HttpHeaders.CONTENT_TYPE;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
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
import org.apache.tika.config.Field;
import org.apache.tika.exception.TikaException;
import org.apache.tika.exception.TikaMemoryLimitException;
import org.apache.tika.extractor.EmbeddedDocumentExtractor;
import org.apache.tika.extractor.EmbeddedDocumentUtil;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.AbstractParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.XHTMLContentHandler;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

/**
 * Parser for various compression formats.
 */
public class CompressorParser extends AbstractParser {

    /** Serial version UID */
    private static final long serialVersionUID = 2793565792967222459L;

    private static final MediaType BROTLI = MediaType.application("x-brotli");
    private static final MediaType LZ4_BLOCK = MediaType.application("x-lz4-block");
    private static final MediaType SNAPPY_RAW = MediaType.application("x-snappy-raw");


    private static final MediaType BZIP = MediaType.application("x-bzip");
    private static final MediaType BZIP2 = MediaType.application("x-bzip2");
    private static final MediaType GZIP = MediaType.application("gzip");
    private static final MediaType GZIP_ALT = MediaType.application("x-gzip");
    private static final MediaType COMPRESS = MediaType.application("x-compress");
    private static final MediaType XZ = MediaType.application("x-xz");
    private static final MediaType PACK = MediaType.application("x-java-pack200");
    private static final MediaType SNAPPY_FRAMED = MediaType.application("x-snappy");
    private static final MediaType ZLIB = MediaType.application("zlib");
    private static final MediaType LZMA = MediaType.application("x-lzma");
    private static final MediaType LZ4_FRAMED = MediaType.application("x-lz4");

    private static final Set<MediaType> SUPPORTED_TYPES =
            MediaType.set(BZIP, BZIP2, GZIP, GZIP_ALT, LZ4_FRAMED, COMPRESS,
                    XZ, PACK, SNAPPY_FRAMED, ZLIB, LZMA);

    private int memoryLimitInKb = 100000;//100MB

    /**
     *
     * @deprecated use {@link #getMediaType(String)}
     * @param stream stream
     * @return MediaType
     */
    @Deprecated
    static MediaType getMediaType(CompressorInputStream stream) {
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

    static MediaType getMediaType(String name) {
        if (CompressorStreamFactory.BROTLI.equals(name)) {
            return BROTLI;
        } else if (CompressorStreamFactory.LZ4_BLOCK.equals(name)) {
            return LZ4_BLOCK;
        } else if (CompressorStreamFactory.LZ4_FRAMED.equals(name)) {
            return LZ4_FRAMED;
        } else if (CompressorStreamFactory.BZIP2.equals(name)) {
            return BZIP2;
        } else if (CompressorStreamFactory.GZIP.equals(name)) {
            return GZIP;
        } else if (CompressorStreamFactory.XZ.equals(name)) {
            return XZ;
        } else if (CompressorStreamFactory.DEFLATE.equals(name)) {
            return ZLIB;
        } else if (CompressorStreamFactory.Z.equals(name)) {
            return COMPRESS;
        } else if (CompressorStreamFactory.PACK200.equals(name)) {
            return PACK;
        } else if (CompressorStreamFactory.SNAPPY_FRAMED.equals(name)) {
            return SNAPPY_FRAMED;
        } else if (CompressorStreamFactory.SNAPPY_RAW.equals(name)) {
            return SNAPPY_RAW;
        } else if (CompressorStreamFactory.LZMA.equals(name)) {
            return LZMA;
        } else {
            return MediaType.OCTET_STREAM;
        }

    }

    public Set<MediaType> getSupportedTypes(ParseContext context) {
        return SUPPORTED_TYPES;
    }

    public void parse(
            InputStream stream, ContentHandler handler,
            Metadata metadata, ParseContext context)
            throws IOException, SAXException, TikaException {
        // At the end we want to close the compression stream to release
        // any associated resources, but the underlying document stream
        // should not be closed
        if (stream.markSupported()) {
            stream = new CloseShieldInputStream(stream);
        } else {
            // Ensure that the stream supports the mark feature
            stream = new BufferedInputStream(new CloseShieldInputStream(stream));
        }

        CompressorInputStream cis;
        try {
            CompressorParserOptions options =
                 context.get(CompressorParserOptions.class, new CompressorParserOptions() {
                     public boolean decompressConcatenated(Metadata metadata) {
                         return false;
                     }
                 });
            CompressorStreamFactory factory =
                    new CompressorStreamFactory(options.decompressConcatenated(metadata), memoryLimitInKb);
            cis = factory.createCompressorInputStream(stream);
        } catch (CompressorException e) {
            if (e.getCause() != null && e.getCause() instanceof MemoryLimitException) {
                throw new TikaMemoryLimitException(e.getMessage());
            }
            throw new TikaException("Unable to uncompress document stream", e);
        }

        MediaType type = getMediaType(cis);
        if (!type.equals(MediaType.OCTET_STREAM)) {
            metadata.set(CONTENT_TYPE, type.toString());
        }

        XHTMLContentHandler xhtml = new XHTMLContentHandler(handler, metadata);
        xhtml.startDocument();

        try {
            Metadata entrydata = new Metadata();
            String name = metadata.get(Metadata.RESOURCE_NAME_KEY);
            if (name != null) {
                if (name.endsWith(".tbz")) {
                    name = name.substring(0, name.length() - 4) + ".tar";
                } else if (name.endsWith(".tbz2")) {
                    name = name.substring(0, name.length() - 5) + ".tar";
                } else if (name.endsWith(".bz")) {
                    name = name.substring(0, name.length() - 3);
                } else if (name.endsWith(".bz2")) {
                    name = name.substring(0, name.length() - 4);
                } else if (name.endsWith(".xz")) {
                    name = name.substring(0, name.length() - 3);
                } else if (name.endsWith(".zlib")) {
                    name = name.substring(0, name.length() - 5);
                } else if (name.endsWith(".pack")) {
                    name = name.substring(0, name.length() - 5);
                } else if (name.length() > 0) {
                    name = GzipUtils.getUncompressedFilename(name);
                }
                entrydata.set(Metadata.RESOURCE_NAME_KEY, name);
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

    @Field
    public void setMemoryLimitInKb(int memoryLimitInKb) {
        this.memoryLimitInKb = memoryLimitInKb;
    }

}
