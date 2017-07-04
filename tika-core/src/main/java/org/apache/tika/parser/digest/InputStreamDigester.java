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

package org.apache.tika.parser.digest;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.Provider;

import org.apache.tika.exception.TikaException;
import org.apache.tika.io.IOExceptionWithCause;
import org.apache.tika.io.TemporaryResources;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.parser.DigestingParser;
import org.apache.tika.parser.ParseContext;

public class InputStreamDigester implements DigestingParser.Digester {

    private final String algorithm;
    private final String algorithmKeyName;
    private final DigestingParser.Encoder encoder;
    private final int markLimit;

    public InputStreamDigester(int markLimit, String algorithm,
                               DigestingParser.Encoder encoder) {
        this(markLimit, algorithm, algorithm, encoder);
    }

    /**
     *
     * @param markLimit limit in bytes to allow for mark/reset.  If the inputstream is longer
     *                  than this limit, the stream will be reset and then spooled to a temporary file.
     *                  Throws IllegalArgumentException if < 0.
     * @param algorithm name of the digest algorithm to retrieve from the Provider
     * @param algorithmKeyName name of the algorithm to store
     *                         as part of the key in the metadata
     *                         when {@link #digest(InputStream, Metadata, ParseContext)} is called
     * @param encoder encoder to convert the byte array returned from the digester to a string
     */
    public InputStreamDigester(int markLimit, String algorithm, String algorithmKeyName,
                               DigestingParser.Encoder encoder) {
        this.algorithm = algorithm;
        this.algorithmKeyName = algorithmKeyName;
        this.encoder = encoder;
        this.markLimit = markLimit;

        if (markLimit < 0) {
            throw new IllegalArgumentException("markLimit must be >= 0");
        }
    }

    private MessageDigest newMessageDigest() {
        try {
            Provider provider = getProvider();
            if (provider == null) {
                return MessageDigest.getInstance(algorithm);
            } else {
                return MessageDigest.getInstance(algorithm, provider);
            }
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalArgumentException(e);
        }
    }

    /**
     *
     * When subclassing this, becare to ensure that your provider is
     * thread-safe (not likely) or return a new provider with each call.
     *
     *
     * @return provider to use to get the MessageDigest from the algorithm name.
     * Default is to return null.
     */
    protected Provider getProvider() {
        return null;
    }

    /**
     *
     * @param is InputStream to digest. Best to use a TikaInputStream because
     *           of potential need to spool to disk.  InputStream must
     *           support mark/reset.
     * @param metadata metadata in which to store the digest information
     * @param parseContext ParseContext -- not actually used yet, but there for future expansion
     * @throws IOException on IO problem or IllegalArgumentException if algorithm couldn't be found
     */
    @Override
    public void digest(InputStream is, Metadata metadata,
                       ParseContext parseContext) throws IOException {
        TikaInputStream tis = TikaInputStream.cast(is);
        if (tis != null && tis.hasFile()) {
            long sz = -1;
            if (tis.hasFile()) {
                sz = tis.getLength();
            }
            //if the inputstream has a file,
            //and its size is greater than its mark limit,
            //just digest the underlying file.
            if (sz > markLimit) {
                digestFile(tis.getFile(), metadata);
                return;
            }
        }


        //try the usual mark/reset stuff.
        //however, if you actually hit the bound,
        //then stop and spool to file via TikaInputStream
        SimpleBoundedInputStream bis = new SimpleBoundedInputStream(markLimit, is);
        boolean finishedStream = false;
        bis.mark(markLimit + 1);
        finishedStream = digestStream(bis, metadata);
        bis.reset();
        if (finishedStream) {
            return;
        }
        //if the stream wasn't finished -- if the stream was longer than the mark limit --
        //spool to File and digest that.
        if (tis != null) {
            digestFile(tis.getFile(), metadata);
        } else {
            TemporaryResources tmp = new TemporaryResources();
            try {
                TikaInputStream tmpTikaInputStream = TikaInputStream.get(is, tmp);
                digestFile(tmpTikaInputStream.getFile(), metadata);
            } finally {
                try {
                    tmp.dispose();
                } catch (TikaException e) {
                    throw new IOExceptionWithCause(e);
                }
            }
        }
    }


    private String getMetadataKey() {
        return TikaCoreProperties.TIKA_META_PREFIX +
                "digest" + Metadata.NAMESPACE_PREFIX_DELIMITER +
                algorithmKeyName;
    }

    private void digestFile(File f, Metadata m) throws IOException {
        try (InputStream is = new FileInputStream(f)) {
            digestStream(is, m);
        }
    }

    /**
     * @param is       input stream to read from
     * @param metadata metadata for reporting the digest
     * @return whether or not this finished the input stream
     * @throws IOException
     */
    private boolean digestStream(InputStream is, Metadata metadata) throws IOException {
        byte[] digestBytes;
        MessageDigest messageDigest = newMessageDigest();

        updateDigest(messageDigest, is);
        digestBytes = messageDigest.digest();

        if (is instanceof SimpleBoundedInputStream) {
            if (((SimpleBoundedInputStream) is).hasHitBound()) {
                return false;
            }
        }
        metadata.set(getMetadataKey(), encoder.encode(digestBytes));
        return true;
    }


    /**
     * Copied from commons-codec
     */
    private static MessageDigest updateDigest(MessageDigest digest, InputStream data) throws IOException {
        byte[] buffer = new byte[1024];

        for (int read = data.read(buffer, 0, 1024); read > -1; read = data.read(buffer, 0, 1024)) {
            digest.update(buffer, 0, read);
        }

        return digest;
    }


    /**
     * Very slight modification of Commons' BoundedInputStream
     * so that we can figure out if this hit the bound or not.
     */
    private static class SimpleBoundedInputStream extends InputStream {
        private final static int EOF = -1;
        private final long max;
        private final InputStream in;
        private long pos;

        private SimpleBoundedInputStream(long max, InputStream in) {
            this.max = max;
            this.in = in;
        }

        @Override
        public int read() throws IOException {
            if (max >= 0 && pos >= max) {
                return EOF;
            }
            final int result = in.read();
            pos++;
            return result;
        }

        /**
         * Invokes the delegate's <code>read(byte[])</code> method.
         *
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
         *
         * @param b   the buffer to read the bytes into
         * @param off The start offset
         * @param len The number of bytes to read
         * @return the number of bytes read or -1 if the end of stream or
         * the limit has been reached.
         * @throws IOException if an I/O error occurs
         */
        @Override
        public int read(final byte[] b, final int off, final int len) throws IOException {
            if (max >= 0 && pos >= max) {
                return EOF;
            }
            final long maxRead = max >= 0 ? Math.min(len, max - pos) : len;
            final int bytesRead = in.read(b, off, (int) maxRead);

            if (bytesRead == EOF) {
                return EOF;
            }

            pos += bytesRead;
            return bytesRead;
        }

        /**
         * Invokes the delegate's <code>skip(long)</code> method.
         *
         * @param n the number of bytes to skip
         * @return the actual number of bytes skipped
         * @throws IOException if an I/O error occurs
         */
        @Override
        public long skip(final long n) throws IOException {
            final long toSkip = max >= 0 ? Math.min(n, max - pos) : n;
            final long skippedBytes = in.skip(toSkip);
            pos += skippedBytes;
            return skippedBytes;
        }

        @Override
        public void reset() throws IOException {
            in.reset();
            pos = 0;
        }

        @Override
        public void mark(int readLimit) {
            in.mark(readLimit);
        }

        public boolean hasHitBound() {
            return pos >= max;
        }
    }
}
