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

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.Arrays;
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
import org.apache.commons.compress.compressors.gzip.GzipParameters;
import org.apache.commons.compress.compressors.gzip.GzipUtils;
import org.apache.commons.compress.compressors.lzma.LZMACompressorInputStream;
import org.apache.commons.compress.compressors.pack200.Pack200CompressorInputStream;
import org.apache.commons.compress.compressors.snappy.FramedSnappyCompressorInputStream;
import org.apache.commons.compress.compressors.snappy.SnappyCompressorInputStream;
import org.apache.commons.compress.compressors.xz.XZCompressorInputStream;
import org.apache.commons.compress.compressors.z.ZCompressorInputStream;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import org.apache.tika.annotation.TikaComponent;
import org.apache.tika.config.ConfigDeserializer;
import org.apache.tika.config.JsonConfig;
import org.apache.tika.exception.TikaException;
import org.apache.tika.exception.TikaMemoryLimitException;
import org.apache.tika.extractor.EmbeddedDocumentExtractor;
import org.apache.tika.extractor.EmbeddedDocumentUtil;
import org.apache.tika.io.FilenameUtils;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.sax.XHTMLContentHandler;
import org.apache.tika.utils.StringUtils;

/**
 * Parser for various compression formats.
 */
@TikaComponent
public class CompressorParser implements Parser {

    /**
     * Serial version UID
     */
    private static final long serialVersionUID = 2793565792967222459L;

    /**
     * Configuration class for JSON deserialization.
     */
    public static class Config {
        private int memoryLimitInKb = 100000;
        private boolean decompressConcatenated = false;

        public int getMemoryLimitInKb() {
            return memoryLimitInKb;
        }

        public void setMemoryLimitInKb(int memoryLimitInKb) {
            this.memoryLimitInKb = memoryLimitInKb;
        }

        public boolean isDecompressConcatenated() {
            return decompressConcatenated;
        }

        public void setDecompressConcatenated(boolean decompressConcatenated) {
            this.decompressConcatenated = decompressConcatenated;
        }
    }

    private static Set<MediaType> SUPPORTED_TYPES;
    private static Map<String, String> MIMES_TO_NAME;

    //pack200 archives start with the 4-byte magic 0xCAFED00D
    private static final byte[] PACK200_MAGIC = {(byte) 0xCA, (byte) 0xFE, (byte) 0xD0, (byte) 0x0D};

    private Config defaultConfig = new Config();

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


    public CompressorParser() {
    }

    /**
     * Constructor with explicit Config object.
     *
     * @param config the configuration
     */
    public CompressorParser(Config config) {
        this.defaultConfig = config;
    }

    /**
     * Constructor for JSON configuration.
     * Requires Jackson on the classpath.
     *
     * @param jsonConfig JSON configuration
     */
    public CompressorParser(JsonConfig jsonConfig) {
        this(ConfigDeserializer.buildConfig(jsonConfig, Config.class));
    }

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


    @Override
    public Set<MediaType> getSupportedTypes(ParseContext context) {
        return SUPPORTED_TYPES;
    }

    @Override
    public void parse(TikaInputStream tis, ContentHandler handler, Metadata metadata,
                      ParseContext context) throws IOException, SAXException, TikaException {
        // At the end we want to close the compression stream to release
        // any associated resources, but the underlying document stream
        // should not be closed
        // TikaInputStream always supports mark
        tis.setCloseShield();

        CompressorInputStream cis;
        try {
            CompressorParserOptions options =
                    context.get(CompressorParserOptions.class,
                        metadata1 -> defaultConfig.isDecompressConcatenated());
            CompressorStreamFactory factory =
                    new CompressorStreamFactory(options.decompressConcatenated(metadata),
                            defaultConfig.getMemoryLimitInKb());
            //if we've already identified it via autodetect
            //trust that and go with the appropriate name
            //to avoid calling CompressorStreamFactory.detect() twice
            String name = getStreamName(metadata);
            boolean pack200 = CompressorStreamFactory.PACK200.equals(name);
            if (name == null) {
                //No content-type hint: peek to see whether this is pack200 so we can route it
                //through the workaround below. Anything else falls through to autodetect unchanged.
                pack200 = isPack200(tis);
            }
            if (pack200) {
                // TIKA-4221 / COMPRESS-721 workaround: commons-compress' Pack200CompressorInputStream
                // reflects into java.io internals (FilterInputStream.in / FileInputStream.path) to
                // bound its input, which throws InaccessibleObjectException on Java 17+. A
                // TikaInputStream is a FilterInputStream, so it triggers this. Spool to a file and
                // reopen via Files.newInputStream (a ChannelInputStream) -- the one input type
                // commons-compress does not reflect into. Pack200CompressorInputStream reads its
                // input fully in the constructor (IN_MEMORY) and then serves bytes from an in-memory
                // buffer, so the channel stream can be closed immediately afterward. Remove this once
                // Tika depends on a commons-compress release that contains the COMPRESS-721 fix.
                try (InputStream packStream = Files.newInputStream(tis.getPath())) {
                    cis = factory.createCompressorInputStream(CompressorStreamFactory.PACK200,
                            packStream);
                }
                if (name == null) {
                    metadata.set(CONTENT_TYPE, PACK.toString());
                }
            } else if (name != null) {
                cis = factory.createCompressorInputStream(name, tis);
            } else {
                cis = factory.createCompressorInputStream(tis);
                MediaType type = getMediaType(cis);
                if (!type.equals(MediaType.OCTET_STREAM)) {
                    metadata.set(CONTENT_TYPE, type.toString());
                }
            }
        } catch (CompressorException e) {
            tis.removeCloseShield();
            if (e.getCause() instanceof MemoryLimitException) {
                throw new TikaMemoryLimitException(e.getMessage());
            }
            throw new TikaException("Unable to uncompress document stream", e);
        } catch (IOException e) {
            //the pack200 workaround (getPath()/Files.newInputStream) can throw IOException;
            //make sure the close shield is removed before propagating
            tis.removeCloseShield();
            throw e;
        }


        XHTMLContentHandler xhtml = new XHTMLContentHandler(handler, metadata, context);
        xhtml.startDocument();
        try {
            Metadata entrydata = Metadata.newInstance(context);
            boolean foundName = false;
            if (cis instanceof GzipCompressorInputStream) {
                foundName = extractGzipMetadata((GzipCompressorInputStream) cis, entrydata);
            }
            if (! foundName) {
                setName(metadata, entrydata);
            }

            // Use the delegate parser to parse the compressed document
            EmbeddedDocumentExtractor extractor =
                    EmbeddedDocumentUtil.getEmbeddedDocumentExtractor(context);
            if (extractor.shouldParseEmbedded(entrydata)) {
                try (TikaInputStream inner = TikaInputStream.get(cis)) {
                    extractor.parseEmbedded(inner, xhtml, entrydata, context, true);
                }
            }
        } finally {
            cis.close();
            tis.removeCloseShield();
        }

        xhtml.endDocument();
    }

    private boolean extractGzipMetadata(GzipCompressorInputStream gzcis, Metadata metadata) {
        GzipParameters gzipParameters = gzcis.getMetaData();
        if (gzipParameters == null) {
            return false;
        }
        String name = gzipParameters.getFileName();
        if (!StringUtils.isBlank(name)) {
            metadata.set(TikaCoreProperties.INTERNAL_PATH, name);
            metadata.set(TikaCoreProperties.RESOURCE_NAME_KEY, name);
            return true;
        }
        //TODO: modification, OS, comment
        return false;
    }

    private void setName(Metadata parentMetadata, Metadata metadata) {
        String name = parentMetadata.get(TikaCoreProperties.RESOURCE_NAME_KEY);
        //if parent's name is blank stop now
        if (StringUtils.isBlank(name)) {
            return;
        }

        name = FilenameUtils.getName(name);

        if (name.endsWith(".tgz") || name.endsWith(".tbz") || name.endsWith(".tbz2")) {
            name = name.substring(0, name.lastIndexOf(".")) + ".tar";
        } else if (name.endsWith(".bz") || name.endsWith(".gz") || name.endsWith(".bz2") || name.endsWith(".xz") || name.endsWith(".zlib") || name.endsWith(".pack") ||
                name.endsWith(".br")) {
            name = name.substring(0, name.lastIndexOf("."));
        } else if (!name.isEmpty()) {
            name = GzipUtils.getUncompressedFileName(name);
        }
        metadata.set(TikaCoreProperties.RESOURCE_NAME_KEY, name);
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

    /**
     * Peeks at the stream signature to determine whether it is a pack200 archive, without
     * consuming the stream. Used so pack200 can be routed through the COMPRESS-721 workaround in
     * {@link #parse}.
     *
     * @param tis the input, which must support mark/reset (a TikaInputStream always does)
     * @return {@code true} if the signature matches pack200
     */
    private static boolean isPack200(TikaInputStream tis) {
        try {
            byte[] sig = new byte[PACK200_MAGIC.length];
            return tis.peek(sig) == PACK200_MAGIC.length && Arrays.equals(sig, PACK200_MAGIC);
        } catch (IOException e) {
            return false;
        }
    }

    public Config getDefaultConfig() {
        return defaultConfig;
    }

}
