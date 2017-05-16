package org.apache.tika.parser.pkg;
    /*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.Locale;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;

import org.apache.commons.compress.compressors.CompressorException;
import org.apache.commons.compress.compressors.CompressorInputStream;
import org.apache.commons.compress.compressors.CompressorOutputStream;
import org.apache.commons.compress.compressors.CompressorStreamProvider;
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream;
import org.apache.commons.compress.compressors.deflate.DeflateCompressorInputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
import org.apache.commons.compress.compressors.lzma.LZMAUtils;
import org.apache.commons.compress.compressors.pack200.Pack200CompressorInputStream;
import org.apache.commons.compress.compressors.snappy.FramedSnappyCompressorInputStream;
import org.apache.commons.compress.compressors.snappy.SnappyCompressorInputStream;
import org.apache.commons.compress.compressors.xz.XZCompressorInputStream;
import org.apache.commons.compress.compressors.xz.XZUtils;
import org.apache.commons.compress.compressors.z.ZCompressorInputStream;
import org.apache.commons.compress.utils.IOUtils;
import org.apache.commons.compress.utils.Lists;
import org.apache.commons.compress.utils.ServiceLoaderIterator;
import org.apache.commons.compress.utils.Sets;
import org.apache.tika.exception.TikaMemoryLimitException;
import org.tukaani.xz.LZMAInputStream;
import org.tukaani.xz.MemoryLimitException;

/**
 * This is a temporary copy/paste hack from commons-compress for Tika 1.15
 * that 1) allows detection without initialization of a stream and
 * 2) prevents easily preventable OOM on two file formats.
 *
 * Once commons-compress 1.14 is released, we will delete this class
 * and go back to commons-compress's CompressorStreamFactory.
 */
@Deprecated
class TikaCompressorStreamFactory implements CompressorStreamProvider {



        private static final TikaCompressorStreamFactory SINGLETON = new TikaCompressorStreamFactory(true, -1);

        /**
         * Constant (value {@value}) used to identify the BZIP2 compression
         * algorithm.
         *
         * @since 1.1
         */
        public static final String BZIP2 = "bzip2";

        /**
         * Constant (value {@value}) used to identify the GZIP compression
         * algorithm.
         *
         * @since 1.1
         */
        public static final String GZIP = "gz";

        /**
         * Constant (value {@value}) used to identify the PACK200 compression
         * algorithm.
         *
         * @since 1.3
         */
        public static final String PACK200 = "pack200";

        /**
         * Constant (value {@value}) used to identify the XZ compression method.
         *
         * @since 1.4
         */
        public static final String XZ = "xz";

        /**
         * Constant (value {@value}) used to identify the LZMA compression method.
         *
         * @since 1.6
         */
        public static final String LZMA = "lzma";

        /**
         * Constant (value {@value}) used to identify the "framed" Snappy
         * compression method.
         *
         * @since 1.7
         */
        public static final String SNAPPY_FRAMED = "snappy-framed";

        /**
         * Constant (value {@value}) used to identify the "raw" Snappy compression
         * method. Not supported as an output stream type.
         *
         * @since 1.7
         */
        public static final String SNAPPY_RAW = "snappy-raw";

        /**
         * Constant (value {@value}) used to identify the traditional Unix compress
         * method. Not supported as an output stream type.
         *
         * @since 1.7
         */
        public static final String Z = "z";

        /**
         * Constant (value {@value}) used to identify the Deflate compress method.
         *
         * @since 1.9
         */
        public static final String DEFLATE = "deflate";


        private final int memoryLimitInKb;

    private SortedMap<String, CompressorStreamProvider> compressorInputStreamProviders;


    public static String getBzip2() {
            return BZIP2;
        }

        public static String getDeflate() {
            return DEFLATE;
        }

        public static String getGzip() {
            return GZIP;
        }

        public static String getLzma() {
            return LZMA;
        }

        public static String getPack200() {
            return PACK200;
        }

        public static TikaCompressorStreamFactory getSingleton() {
            return SINGLETON;
        }

        public static String getSnappyFramed() {
            return SNAPPY_FRAMED;
        }

        public static String getSnappyRaw() {
            return SNAPPY_RAW;
        }

        public static String getXz() {
            return XZ;
        }

        public static String getZ() {
            return Z;
        }

        static void putAll(final Set<String> names, final CompressorStreamProvider provider,
                           final TreeMap<String, CompressorStreamProvider> map) {
            for (final String name : names) {
                map.put(toKey(name), provider);
            }
        }

        private static String toKey(final String name) {
            return name.toUpperCase(Locale.ROOT);
        }

        /**
         * If true, decompress until the end of the input. If false, stop after the
         * first stream and leave the input position to point to the next byte after
         * the stream
         */
        private final Boolean decompressUntilEOF;

        /**
         * If true, decompress until the end of the input. If false, stop after the
         * first stream and leave the input position to point to the next byte after
         * the stream
         */
        private volatile boolean decompressConcatenated = false;

        /**
         * Create an instance with the provided decompress Concatenated option.
         *
         * @param decompressUntilEOF
         *            if true, decompress until the end of the input; if false, stop
         *            after the first stream and leave the input position to point
         *            to the next byte after the stream. This setting applies to the
         *            gzip, bzip2 and xz formats only.
         * @since 1.10
         */
        public TikaCompressorStreamFactory(final boolean decompressUntilEOF, final int memoryLimitInKb) {
            this.decompressUntilEOF = Boolean.valueOf(decompressUntilEOF);
            // Also copy to existing variable so can continue to use that as the
            // current value
            this.decompressConcatenated = decompressUntilEOF;
            this.memoryLimitInKb = memoryLimitInKb;
        }

        /**
         * Try to detect the type of compressor stream.
         *
         * @param in input stream
         * @return type of compressor stream detected
         * @throws CompressorException if no compressor stream type was detected
         *                             or if something else went wrong
         * @throws IllegalArgumentException if stream is null or does not support mark
         *
         * @since 1.14
         */
        public static String detect(final InputStream in) throws CompressorException {
            if (in == null) {
                throw new IllegalArgumentException("Stream must not be null.");
            }

            if (!in.markSupported()) {
                throw new IllegalArgumentException("Mark is not supported.");
            }

            final byte[] signature = new byte[12];
            in.mark(signature.length);
            int signatureLength = -1;
            try {
                signatureLength = IOUtils.readFully(in, signature);
                in.reset();
            } catch (IOException e) {
                throw new CompressorException("IOException while reading signature.", e);
            }

            if (BZip2CompressorInputStream.matches(signature, signatureLength)) {
                return BZIP2;
            }

            if (GzipCompressorInputStream.matches(signature, signatureLength)) {
                return GZIP;
            }

            if (Pack200CompressorInputStream.matches(signature, signatureLength)) {
                return PACK200;
            }

            if (FramedSnappyCompressorInputStream.matches(signature, signatureLength)) {
                return SNAPPY_FRAMED;
            }

            if (ZCompressorInputStream.matches(signature, signatureLength)) {
                return Z;
            }

            if (DeflateCompressorInputStream.matches(signature, signatureLength)) {
                return DEFLATE;
            }

            if (XZUtils.matches(signature, signatureLength)) {
                return XZ;
            }

            if (LZMAUtils.matches(signature, signatureLength)) {
                return LZMA;
            }

/*            if (FramedLZ4CompressorInputStream.matches(signature, signatureLength)) {
                return LZ4_FRAMED;
            }*/

            throw new CompressorException("No Compressor found for the stream signature.");
        }

    public SortedMap<String, CompressorStreamProvider> getCompressorInputStreamProviders() {
        if (compressorInputStreamProviders == null) {
            compressorInputStreamProviders = Collections
                    .unmodifiableSortedMap(findAvailableCompressorInputStreamProviders());
        }
        return compressorInputStreamProviders;
    }

    public static SortedMap<String, CompressorStreamProvider> findAvailableCompressorInputStreamProviders() {
        return AccessController.doPrivileged(new PrivilegedAction<SortedMap<String, CompressorStreamProvider>>() {
            @Override
            public SortedMap<String, CompressorStreamProvider> run() {
                final TreeMap<String, CompressorStreamProvider> map = new TreeMap<>();
                putAll(SINGLETON.getInputStreamCompressorNames(), SINGLETON, map);
                for (final CompressorStreamProvider provider : findCompressorStreamProviders()) {
                    putAll(provider.getInputStreamCompressorNames(), provider, map);
                }
                return map;
            }
        });
    }

    private static ArrayList<CompressorStreamProvider> findCompressorStreamProviders() {
        return Lists.newArrayList(serviceLoaderIterator());
    }

    private static Iterator<CompressorStreamProvider> serviceLoaderIterator() {
        return new ServiceLoaderIterator<>(CompressorStreamProvider.class);
    }

        /**
         * Create an compressor input stream from an input stream, autodetecting the
         * compressor type from the first few bytes of the stream. The InputStream
         * must support marks, like BufferedInputStream.
         *
         * @param in
         *            the input stream
         * @return the compressor input stream
         * @throws CompressorException
         *             if the compressor name is not known
         * @throws IllegalArgumentException
         *             if the stream is null or does not support mark
         * @since 1.1
         */
        public CompressorInputStream createCompressorInputStream(final InputStream in) throws CompressorException,
                TikaMemoryLimitException {
            return createCompressorInputStream(detect(in), in);
        }

        /**
         * Creates a compressor input stream from a compressor name and an input
         * stream.
         *
         * @param name
         *            of the compressor, i.e. {@value #GZIP}, {@value #BZIP2},
         *            {@value #XZ}, {@value #LZMA}, {@value #PACK200},
         *            {@value #SNAPPY_RAW}, {@value #SNAPPY_FRAMED}, {@value #Z},
         *            or {@value #DEFLATE}
         * @param in
         *            the input stream
         * @return compressor input stream
         * @throws CompressorException
         *             if the compressor name is not known or not available
         * @throws IllegalArgumentException
         *             if the name or input stream is null
         */
        public CompressorInputStream createCompressorInputStream(final String name, final InputStream in)
                throws CompressorException, TikaMemoryLimitException {
            return createCompressorInputStream(name, in, decompressConcatenated);
        }

        public CompressorInputStream createCompressorInputStream(final String name, final InputStream in,
                                                                 final boolean actualDecompressConcatenated) throws CompressorException {
            if (name == null || in == null) {
                throw new IllegalArgumentException("Compressor name and stream must not be null.");
            }

            try {

                if (GZIP.equalsIgnoreCase(name)) {
                    return new GzipCompressorInputStream(in, actualDecompressConcatenated);
                }

                if (BZIP2.equalsIgnoreCase(name)) {
                    return new BZip2CompressorInputStream(in, actualDecompressConcatenated);
                }

                if (XZ.equalsIgnoreCase(name)) {
                    if (!XZUtils.isXZCompressionAvailable()) {
                        throw new CompressorException("XZ compression is not available.");
                    }
                    return new XZCompressorInputStream(in, actualDecompressConcatenated);
                }

                if (LZMA.equalsIgnoreCase(name)) {
                    if (!LZMAUtils.isLZMACompressionAvailable()) {
                        throw new CompressorException("LZMA compression is not available");
                    }
                    try {
                        return new SaferLZMACompressorInputStream(in);
                    } catch (MemoryLimitException e) {
                        throw new CompressorException("MemoryLimitException: " + e.getMessage(), e);
                    }
                }

                if (PACK200.equalsIgnoreCase(name)) {
                    return new Pack200CompressorInputStream(in);
                }

                if (SNAPPY_RAW.equalsIgnoreCase(name)) {
                    return new SnappyCompressorInputStream(in);
                }

                if (SNAPPY_FRAMED.equalsIgnoreCase(name)) {
                    return new FramedSnappyCompressorInputStream(in);
                }

                if (Z.equalsIgnoreCase(name)) {
                    try {
                        return new SaferZCompressorInputStream(in);
                    } catch (TikaRuntimeMemoryLimitException e) {
                        throw new CompressorException("MemoryLimitException: " + e.getMessage(), e);
                    }
                }

                if (DEFLATE.equalsIgnoreCase(name)) {
                    return new DeflateCompressorInputStream(in);
                }
/*
not currently supported
                if (LZ4_BLOCK.equalsIgnoreCase(name)) {
                    return new BlockLZ4CompressorInputStream(in);
                }

                if (LZ4_FRAMED.equalsIgnoreCase(name)) {
                    return new FramedLZ4CompressorInputStream(in, actualDecompressConcatenated);
                }
 */

            } catch (final IOException e) {
                throw new CompressorException("Could not create CompressorInputStream.", e);
            }

            final CompressorStreamProvider compressorStreamProvider = getCompressorInputStreamProviders().get(toKey(name));
            if (compressorStreamProvider != null) {
                return compressorStreamProvider.createCompressorInputStream(name, in, actualDecompressConcatenated);
            }

            throw new CompressorException("Compressor: " + name + " not found.");
        }

    @Override
    public CompressorOutputStream createCompressorOutputStream(String s, OutputStream outputStream) throws CompressorException {
        throw new UnsupportedOperationException();
    }


    // For Unit tests
        boolean getDecompressConcatenated() {
            return decompressConcatenated;
        }

    public Set<String> getInputStreamCompressorNames() {
        return Sets.newHashSet(GZIP, BZIP2, XZ, LZMA, PACK200, DEFLATE, SNAPPY_RAW, SNAPPY_FRAMED, Z);
    }

    @Override
    public Set<String> getOutputStreamCompressorNames() {
        throw new UnsupportedOperationException();
    }

    public Boolean getDecompressUntilEOF() {
            return decompressUntilEOF;
        }

    private class SaferZCompressorInputStream extends ZCompressorInputStream {

        public SaferZCompressorInputStream(InputStream inputStream) throws IOException {
            super(inputStream);
        }

        @Override
        protected void initializeTables(int maxCodeSize) {
            int maxTableSize = 1 << maxCodeSize;
            if (memoryLimitInKb > -1 && maxTableSize > (memoryLimitInKb*1024)) {
                throw new TikaRuntimeMemoryLimitException("Calculated maxCodeSize ("+maxCodeSize+" bytes) is greater "+
                 "than the maximum allowable ("+ (memoryLimitInKb*1024) +" bytes).\n"+
                        "If the file is not corrupt, consider increasing " +
                        "the memoryLimitInKb parameter in the CompressorParser");
            }
            super.initializeTables(maxCodeSize);
        }
    }

    private static class TikaRuntimeMemoryLimitException extends RuntimeException {
        public TikaRuntimeMemoryLimitException(String msg) {
            super(msg);
        }
    }

    private class SaferLZMACompressorInputStream extends CompressorInputStream {
        private final InputStream in;

        /**
         * Creates a new input stream that decompresses LZMA-compressed data
         * from the specified input stream.
         *
         * @param       inputStream where to read the compressed data
         *
         * @throws      IOException if the input is not in the .lzma format,
         *                          the input is corrupt or truncated, the .lzma
         *                          headers specify sizes that are not supported
         *                          by this implementation, or the underlying
         *                          <code>inputStream</code> throws an exception
         */
        public SaferLZMACompressorInputStream(final InputStream inputStream) throws IOException {
            in = new LZMAInputStream(inputStream, memoryLimitInKb);
        }

        /** {@inheritDoc} */
        @Override
        public int read() throws IOException {
            final int ret = in.read();
            count(ret == -1 ? 0 : 1);
            return ret;
        }

        /** {@inheritDoc} */
        @Override
        public int read(final byte[] buf, final int off, final int len) throws IOException {
            final int ret = in.read(buf, off, len);
            count(ret);
            return ret;
        }

        /** {@inheritDoc} */
        @Override
        public long skip(final long n) throws IOException {
            return in.skip(n);
        }

        /** {@inheritDoc} */
        @Override
        public int available() throws IOException {
            return in.available();
        }

        /** {@inheritDoc} */
        @Override
        public void close() throws IOException {
            in.close();
        }
    }
}
