package org.apache.tika.parser.utils;

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

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.io.IOUtils;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.parser.DigestingParser;
import org.apache.tika.parser.ParseContext;

/**
 * Implementation of {@link org.apache.tika.parser.DigestingParser.Digester}
 * that relies on commons.codec.digest.DigestUtils to calculate digest hashes.
 * <p>
 * This digester tries to use the regular mark/reset protocol on the InputStream.
 * However, this wraps an internal BoundedInputStream, and if the InputStream
 * is not fully read, then this will reset the stream and
 * spool the InputStream to disk (via TikaInputStream) and then digest the file.
 * <p>
 * If a TikaInputStream is passed in and it has an underlying file that is longer
 * than the {@link #markLimit}, then this digester digests the file directly.
 *
 */
public class CommonsDigester implements DigestingParser.Digester {

    public enum DigestAlgorithm {
        //those currently available in commons.digest
        MD2,
        MD5,
        SHA1,
        SHA256,
        SHA384,
        SHA512;

        String getMetadataKey() {
            return TikaCoreProperties.TIKA_META_PREFIX+
                    "digest"+Metadata.NAMESPACE_PREFIX_DELIMITER+this.toString();
        }
    }

    private final List<DigestAlgorithm> algorithms = new ArrayList<DigestAlgorithm>();
    private final int markLimit;

    public CommonsDigester(int markLimit, DigestAlgorithm... algorithms) {
        Collections.addAll(this.algorithms, algorithms);
        if (markLimit < 0) {
            throw new IllegalArgumentException("markLimit must be >= 0");
        }
        this.markLimit = markLimit;
    }

    @Override
    public void digest(InputStream is, Metadata m, ParseContext parseContext) throws IOException {
        InputStream tis = TikaInputStream.get(is);
        long sz = -1;
        if (((TikaInputStream)tis).hasFile()) {
            sz = ((TikaInputStream)tis).getLength();
        }
        //if the file is definitely a file,
        //and its size is greater than its mark limit,
        //just digest the underlying file.
        if (sz > markLimit) {
            digestFile(((TikaInputStream)tis).getFile(), m);
            return;
        }

        //try the usual mark/reset stuff.
        //however, if you actually hit the bound,
        //then stop and spool to file via TikaInputStream
        SimpleBoundedInputStream bis = new SimpleBoundedInputStream(markLimit, tis);
        boolean finishedStream = false;
        for (DigestAlgorithm algorithm : algorithms) {
            bis.mark(markLimit + 1);
            finishedStream = digestEach(algorithm, bis, m);
            bis.reset();
            if (!finishedStream) {
                break;
            }
        }
        if (!finishedStream) {
            digestFile(((TikaInputStream)tis).getFile(), m);
        }
    }

    private void digestFile(File f, Metadata m) throws IOException {
        for (DigestAlgorithm algorithm : algorithms) {
            InputStream is = new FileInputStream(f);
            try {
                digestEach(algorithm, is, m);
            } finally {
                IOUtils.closeQuietly(is);
            }
        }
    }

    /**
     *
     * @param algorithm algo to use
     * @param is input stream to read from
     * @param metadata metadata for reporting the digest
     * @return whether or not this finished the input stream
     * @throws IOException
     */
    private boolean digestEach(DigestAlgorithm algorithm,
                            InputStream is, Metadata metadata) throws IOException {
        String digest = null;
        try {
            switch (algorithm) {
                case MD2:
                    digest = DigestUtils.md2Hex(is);
                    break;
                case MD5:
                    digest = DigestUtils.md5Hex(is);
                    break;
                case SHA1:
                    digest = DigestUtils.sha1Hex(is);
                    break;
                case SHA256:
                    digest = DigestUtils.sha256Hex(is);
                    break;
                case SHA384:
                    digest = DigestUtils.sha384Hex(is);
                    break;
                case SHA512:
                    digest = DigestUtils.sha512Hex(is);
                    break;
                default:
                    throw new IllegalArgumentException("Sorry, not aware of algorithm: " + algorithm.toString());
            }
        } catch (IOException e) {
            e.printStackTrace();
            //swallow, or should we throw this?
        }
        if (is instanceof SimpleBoundedInputStream) {
            if (((SimpleBoundedInputStream)is).hasHitBound()) {
                return false;
            }
        }
        metadata.set(algorithm.getMetadataKey(), digest);
        return true;
    }

    /**
     *
     * @param s comma-delimited (no space) list of algorithms to use: md5,sha256
     * @return
     */
    public static DigestAlgorithm[] parse(String s) {
        assert(s != null);

        List<DigestAlgorithm> ret = new ArrayList<DigestAlgorithm>();
        for (String algoString : s.split(",")) {
            String uc = algoString.toUpperCase(Locale.ROOT);
            if (uc.equals(DigestAlgorithm.MD2.toString())) {
                ret.add(DigestAlgorithm.MD2);
            } else if (uc.equals(DigestAlgorithm.MD5.toString())) {
                ret.add(DigestAlgorithm.MD5);
            } else if (uc.equals(DigestAlgorithm.SHA1.toString())) {
                ret.add(DigestAlgorithm.SHA1);
            } else if (uc.equals(DigestAlgorithm.SHA256.toString())) {
                ret.add(DigestAlgorithm.SHA256);
            } else if (uc.equals(DigestAlgorithm.SHA384.toString())) {
                ret.add(DigestAlgorithm.SHA384);
            } else if (uc.equals(DigestAlgorithm.SHA512.toString())) {
                ret.add(DigestAlgorithm.SHA512);
            } else {
                StringBuilder sb = new StringBuilder();
                int i = 0;
                for (DigestAlgorithm algo : DigestAlgorithm.values()) {
                    if (i++ > 0) {
                        sb.append(", ");
                    }
                    sb.append(algo.toString());
                }
                throw new IllegalArgumentException("Couldn't match " + s + " with any of: " + sb.toString());
            }
        }
        return ret.toArray(new DigestAlgorithm[ret.size()]);
    }

    /**
     * Very slight modification of Commons' BoundedInputStream
     * so that we can figure out if this hit the bound or not.
     */
    private class SimpleBoundedInputStream extends InputStream {
        private final static int EOF = -1;
        private final long max;
        private final InputStream in;
        private long pos;
        boolean hitBound = false;

        private SimpleBoundedInputStream(long max, InputStream in) {
            this.max = max;
            this.in = in;
        }

        @Override
        public int read() throws IOException {
            if (max >= 0 && pos >= max) {
                hitBound = true;
                return EOF;
            }
            final int result = in.read();
            pos++;
            return result;
        }

        /**
         * Invokes the delegate's <code>read(byte[])</code> method.
         * @param b the buffer to read the bytes into
         * @return the number of bytes read or -1 if the end of stream or
         * the limit has been reached.
         * @throws IOException if an I/O error occurs
         */
        @Override
        public int read(final byte[] b) throws IOException {
            return this.read(b, 0, b.length);
        }

        /**
         * Invokes the delegate's <code>read(byte[], int, int)</code> method.
         * @param b the buffer to read the bytes into
         * @param off The start offset
         * @param len The number of bytes to read
         * @return the number of bytes read or -1 if the end of stream or
         * the limit has been reached.
         * @throws IOException if an I/O error occurs
         */
        @Override
        public int read(final byte[] b, final int off, final int len) throws IOException {
            if (max>=0 && pos>=max) {
                return EOF;
            }
            final long maxRead = max>=0 ? Math.min(len, max-pos) : len;
            final int bytesRead = in.read(b, off, (int)maxRead);

            if (bytesRead==EOF) {
                return EOF;
            }

            pos+=bytesRead;
            return bytesRead;
        }

        /**
         * Invokes the delegate's <code>skip(long)</code> method.
         * @param n the number of bytes to skip
         * @return the actual number of bytes skipped
         * @throws IOException if an I/O error occurs
         */
        @Override
        public long skip(final long n) throws IOException {
            final long toSkip = max>=0 ? Math.min(n, max-pos) : n;
            final long skippedBytes = in.skip(toSkip);
            pos+=skippedBytes;
            return skippedBytes;
        }

        @Override
        public void reset() throws IOException {
            in.reset();
        }

        @Override
        public void mark(int readLimit) {
            in.mark(readLimit);
        }

        public boolean hasHitBound() {
            return hitBound;
        }
    }
}
