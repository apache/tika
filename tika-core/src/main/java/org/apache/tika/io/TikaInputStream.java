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
package org.apache.tika.io;

import java.io.BufferedInputStream;
import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLConnection;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Blob;
import java.sql.SQLException;

import org.apache.commons.io.IOUtils;
import org.apache.commons.io.function.IOSupplier;
import org.apache.commons.io.input.TaggedInputStream;

import org.apache.tika.metadata.HttpHeaders;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.utils.StringUtils;

/**
 * Input stream with extended capabilities for detection and parsing.
 * <p>
 * This implementation uses backing strategies to handle different input types:
 * <ul>
 *   <li>{@link ByteArraySource} for byte[] inputs - no caching needed</li>
 *   <li>{@link FileSource} for Path/File inputs - direct file access</li>
 *   <li>{@link CachingSource} for InputStream inputs - passthrough by default;
 *       caches bytes only after {@link #enableRewind()}</li>
 * </ul>
 *
 * @since Apache Tika 0.8
 */
public class TikaInputStream extends TaggedInputStream {

    private static final int MAX_CONSECUTIVE_EOFS = 1000;
    private static final int BLOB_SIZE_THRESHOLD = 1024 * 1024;

    private final TemporaryResources tmp;

    private long position = 0;
    private long mark = -1;
    private Object openContainer;
    private int consecutiveEOFs = 0;
    private int closeShieldDepth = 0;
    private String suffix = null;
    private long overrideLength = -1;  // For getFromContainer() to set explicit length

    // ========== Constructors ==========

    /**
     * Strategy-based constructor.
     * TikaInputSource extends InputStream, so we pass it directly to super().
     */
    private TikaInputStream(TikaInputSource inputSource, TemporaryResources tmp, String suffix) {
        super((InputStream) inputSource);
        this.tmp = tmp;
        this.suffix = suffix;
    }

    /**
     * Returns the backing TikaInputSource, or null if using protected constructor.
     */
    private TikaInputSource inputSource() {
        return in instanceof TikaInputSource ? (TikaInputSource) in : null;
    }

    // ========== Static Factory Methods ==========

    public static TikaInputStream get(InputStream stream, TemporaryResources tmp, Metadata metadata) {
        if (stream == null) {
            throw new NullPointerException("The Stream must not be null");
        }
        if (stream instanceof TikaInputStream) {
            return (TikaInputStream) stream;
        }
        String ext = getExtension(metadata);
        TikaInputSource inputSource = new CachingSource(stream, tmp, -1, metadata, ext);
        return new TikaInputStream(inputSource, tmp, ext);
    }

    /**
     * Creates a TikaInputStream from a re-openable stream supplier. Unlike
     * {@link #get(InputStream, TemporaryResources, Metadata)} -- which caches a one-shot
     * stream to memory/disk so it can be rewound -- the supplier is re-invoked to re-read
     * the content, so rewinding (e.g. during digesting) never spills to disk. A temp file
     * is created only if {@link #getPath()} is later called (a parser/detector needing a
     * File) or {@link #getSeekableByteChannel()} is asked for content that does not fit
     * in memory.
     *
     * @param opener   supplies a fresh InputStream over the same content on each call
     * @param tmp      temporary resources for any on-demand {@link #getPath()} spill
     * @param metadata metadata used for extension/length hints; may be null
     */
    public static TikaInputStream get(IOSupplier<InputStream> opener, TemporaryResources tmp,
                                      Metadata metadata) {
        if (opener == null) {
            throw new NullPointerException("The opener must not be null");
        }
        String ext = getExtension(metadata);
        long length = -1;
        if (metadata != null) {
            String cl = metadata.get(HttpHeaders.CONTENT_LENGTH);
            if (cl != null) {
                try {
                    length = Long.parseLong(cl);
                } catch (NumberFormatException e) {
                    length = -1;
                }
            }
        }
        TikaInputSource inputSource = new ReopenableSource(opener, tmp, length, ext);
        return new TikaInputStream(inputSource, tmp, ext);
    }

    public static TikaInputStream get(InputStream stream) {
        return get(stream, new TemporaryResources(), null);
    }

    public static TikaInputStream get(InputStream stream, Metadata metadata) {
        return get(stream, new TemporaryResources(), metadata);
    }

    public static TikaInputStream get(byte[] data) {
        return get(data, new Metadata());
    }

    public static TikaInputStream get(byte[] data, Metadata metadata) {
        metadata.set(HttpHeaders.CONTENT_LENGTH, Integer.toString(data.length));
        String ext = getExtension(metadata);
        TemporaryResources tmp = new TemporaryResources();
        TikaInputSource inputSource = new ByteArraySource(data, tmp);
        return new TikaInputStream(inputSource, tmp, ext);
    }

    public static TikaInputStream get(Path path) throws IOException {
        return get(path, new Metadata());
    }

    public static TikaInputStream get(Path path, Metadata metadata) throws IOException {
        if (StringUtils.isBlank(metadata.get(TikaCoreProperties.RESOURCE_NAME_KEY))) {
            metadata.set(TikaCoreProperties.RESOURCE_NAME_KEY, path.getFileName().toString());
        }
        metadata.set(HttpHeaders.CONTENT_LENGTH, Long.toString(Files.size(path)));
        String ext = FilenameUtils.getSuffixFromPath(path.getFileName().toString());
        TemporaryResources tmp = new TemporaryResources();
        TikaInputSource inputSource = new FileSource(path);
        return new TikaInputStream(inputSource, tmp, ext);
    }

    public static TikaInputStream get(Path path, Metadata metadata, TemporaryResources tmp)
            throws IOException {
        long length = Files.size(path);
        if (StringUtils.isBlank(metadata.get(TikaCoreProperties.RESOURCE_NAME_KEY))) {
            metadata.set(TikaCoreProperties.RESOURCE_NAME_KEY, path.getFileName().toString());
        }
        metadata.set(HttpHeaders.CONTENT_LENGTH, Long.toString(length));
        String ext = FilenameUtils.getSuffixFromPath(path.getFileName().toString());
        TikaInputSource inputSource = new FileSource(path);
        return new TikaInputStream(inputSource, tmp, ext);
    }

    public static TikaInputStream get(File file) throws IOException {
        return get(file.toPath(), new Metadata());
    }

    public static TikaInputStream get(File file, Metadata metadata) throws IOException {
        return get(file.toPath(), metadata);
    }

    public static TikaInputStream get(Blob blob) throws SQLException, IOException {
        return get(blob, new Metadata());
    }

    public static TikaInputStream get(Blob blob, Metadata metadata) throws SQLException, IOException {
        long length = -1;
        try {
            length = blob.length();
            metadata.set(HttpHeaders.CONTENT_LENGTH, Long.toString(length));
        } catch (SQLException ignore) {
        }

        if (0 <= length && length <= BLOB_SIZE_THRESHOLD) {
            return get(blob.getBytes(1, (int) length), metadata);
        } else {
            String ext = getExtension(metadata);
            TemporaryResources tmp = new TemporaryResources();
            TikaInputSource inputSource = new CachingSource(
                    new BufferedInputStream(blob.getBinaryStream()), tmp, length, metadata, ext);
            return new TikaInputStream(inputSource, tmp, ext);
        }
    }

    public static TikaInputStream get(URI uri) throws IOException {
        return get(uri, new Metadata());
    }

    public static TikaInputStream get(URI uri, Metadata metadata) throws IOException {
        if ("file".equalsIgnoreCase(uri.getScheme())) {
            Path path = Paths.get(uri);
            if (Files.isRegularFile(path)) {
                return get(path, metadata);
            }
        }
        return get(uri.toURL(), metadata);
    }

    public static TikaInputStream get(URL url) throws IOException {
        return get(url, new Metadata());
    }

    public static TikaInputStream get(URL url, Metadata metadata) throws IOException {
        if ("file".equalsIgnoreCase(url.getProtocol())) {
            try {
                Path path = Paths.get(url.toURI());
                if (Files.isRegularFile(path)) {
                    return get(path, metadata);
                }
            } catch (URISyntaxException e) {
                // fall through
            }
        }

        URLConnection connection = url.openConnection();

        String urlPath = url.getPath();
        int slash = urlPath.lastIndexOf('/');
        if (slash + 1 < urlPath.length()) {
            metadata.set(TikaCoreProperties.RESOURCE_NAME_KEY, urlPath.substring(slash + 1));
        }

        String type = connection.getContentType();
        if (type != null) {
            metadata.set(HttpHeaders.CONTENT_TYPE, type);
        }

        String encoding = connection.getContentEncoding();
        if (encoding != null) {
            metadata.set(HttpHeaders.CONTENT_ENCODING, encoding);
        }

        int length = connection.getContentLength();
        if (length >= 0) {
            metadata.set(HttpHeaders.CONTENT_LENGTH, Integer.toString(length));
        }

        String ext = getExtension(metadata);
        TemporaryResources tmp = new TemporaryResources();
        TikaInputSource inputSource = new CachingSource(
                new BufferedInputStream(connection.getInputStream()), tmp, length, metadata, ext);
        return new TikaInputStream(inputSource, tmp, ext);
    }

    public static TikaInputStream getFromContainer(Object openContainer, long length, Metadata metadata) {
        TikaInputStream tis = TikaInputStream.get(new byte[0], metadata);
        tis.setOpenContainer(openContainer);
        tis.setLength(length);
        metadata.set(HttpHeaders.CONTENT_LENGTH, Long.toString(length));
        return tis;
    }

    private static String getExtension(Metadata metadata) {
        if (metadata == null) {
            return StringUtils.EMPTY;
        }
        String name = metadata.get(TikaCoreProperties.RESOURCE_NAME_KEY);
        return FilenameUtils.getSuffixFromPath(name);
    }

    // ========== InputStream Methods ==========

    /**
     * Skips up to {@code n} bytes. Returns the actual number of bytes skipped,
     * which may be less than requested if the end of stream is reached.
     * <p>
     * This method does NOT throw {@link java.io.EOFException} if fewer bytes
     * are available. Callers must check the return value to determine how many
     * bytes were actually skipped.
     *
     * @param n the number of bytes to skip
     * @return the actual number of bytes skipped (may be less than {@code n})
     */
    @Override
    public long skip(long n) throws IOException {
        long skipped = IOUtils.skip(in, n);
        position += skipped;
        return skipped;
    }

    @Override
    public void mark(int readlimit) {
        super.mark(readlimit);
        mark = position;
    }

    @Override
    public boolean markSupported() {
        return true;
    }

    @Override
    public void reset() throws IOException {
        if (mark < 0) {
            throw new IOException("Resetting to invalid mark");
        }

        // Delegate to underlying stream's reset (handles passthrough and caching modes)
        super.reset();

        position = mark;
        // Don't invalidate mark - allow multiple reset() calls to same mark
        consecutiveEOFs = 0;
    }

    @Override
    public void close() throws IOException {
        if (closeShieldDepth > 0) {
            return;
        }
        mark = -1;

        if (tmp != null) {
            tmp.addResource(in);
            tmp.close();
        }
    }

    @Override
    protected void afterRead(int n) throws IOException {
        if (n != -1) {
            position += n;
            consecutiveEOFs = 0;
        } else {
            consecutiveEOFs++;
            if (consecutiveEOFs > MAX_CONSECUTIVE_EOFS) {
                throw new IOException("Read too many -1 (EOFs); there could be an infinite loop. " +
                        "If you think your file is not corrupt, please open an issue on Tika's JIRA");
            }
        }
    }

    // ========== TikaInputStream-specific Methods ==========

    public int peek(byte[] buffer) throws IOException {
        int n = 0;
        mark(buffer.length);
        // reset in finally: a throw mid-read must not leave the stream advanced
        try {
            int m = read(buffer);
            while (m != -1) {
                n += m;
                if (n < buffer.length) {
                    m = read(buffer, n, buffer.length - n);
                } else {
                    m = -1;
                }
            }
        } finally {
            reset();
        }
        return n;
    }

    public Object getOpenContainer() {
        return openContainer;
    }

    public void setOpenContainer(Object container) {
        openContainer = container;
        if (container instanceof Closeable) {
            tmp.addResource((Closeable) container);
        }
    }

    public void addCloseableResource(Closeable closeable) {
        tmp.addResource(closeable);
    }

    /**
     * Whether the content is already on disk: a real file, or a stream cache that spilled.
     * {@link #getPath()} then returns that file without re-copying anything already written
     * to it -- but it is not free, and it is not a getter: for a cache that spilled
     * mid-stream it first drains the rest of the source into the file, switches this stream
     * to reading from that file, and sets {@code Content-Length} on the Metadata this stream
     * was created with. Use {@link #hasLength()} if you only need the size.
     */
    public boolean hasFile() {
        TikaInputSource source = inputSource();
        return source != null && source.hasPath();
    }

    public Path getPath() throws IOException {
        TikaInputSource source = inputSource();
        if (source == null) {
            throw new IOException("No TikaInputSource available");
        }
        return source.getPath(suffix);
    }

    public File getFile() throws IOException {
        return getPath().toFile();
    }

    public FileChannel getFileChannel() throws IOException {
        FileChannel channel = FileChannel.open(getPath());
        tmp.addResource(channel);
        return channel;
    }

    public boolean hasLength() {
        if (overrideLength >= 0) {
            return true;
        }
        TikaInputSource source = inputSource();
        return source != null && source.getLength() != -1;
    }

    /**
     * The stream length. For a stream-backed instance with no declared length this
     * spools the entire remaining stream to a temporary file to measure it.
     */
    public long getLength() throws IOException {
        if (overrideLength >= 0) {
            return overrideLength;
        }
        TikaInputSource source = inputSource();
        if (source == null) {
            return -1;
        }
        long len = source.getLength();
        if (len == -1) {
            // Force spill to get length
            getPath();
            len = source.getLength();
        }
        return len;
    }

    public long getPosition() {
        return position;
    }

    private void setLength(long length) {
        this.overrideLength = length;
    }

    public void setCloseShield() {
        this.closeShieldDepth++;
    }

    public void removeCloseShield() {
        // floored: an unmatched remove must not cancel a later caller's shield
        if (closeShieldDepth > 0) {
            closeShieldDepth--;
        }
    }

    public boolean isCloseShield() {
        return closeShieldDepth > 0;
    }

    /**
     * Rewind the stream to the beginning.
     * <p>
     * For streams created from byte arrays or files, this always works.
     * For streams created from raw InputStreams, this requires
     * {@link #enableRewind()} to have been called first.
     */
    public void rewind() throws IOException {
        TikaInputSource source = inputSource();
        if (source != null) {
            source.seekTo(0);
        } else {
            throw new IOException("Cannot rewind: no TikaInputSource available");
        }
        position = 0;
        mark = -1;
        consecutiveEOFs = 0;
    }

    /**
     * Enables full rewind capability for this stream.
     * <p>
     * For streams backed by byte arrays or files, this is a no-op since they
     * are inherently rewindable. For streams backed by raw InputStreams, this
     * switches from passthrough mode to caching mode, enabling subsequent
     * {@link #rewind()}, {@link #mark(int)}/{@link #reset()}, and random access.
     * <p>
     * Must be called when position is 0 (before any reading), otherwise
     * throws IOException.
     * <p>
     * Use this method when you know you'll need to rewind the stream later
     * (e.g., for detection followed by parsing, or digest calculation).
     * For streaming-only operations (e.g., HTML parsing), skip this call
     * to avoid unnecessary caching overhead.
     *
     * @throws IOException if bytes have already been read from the stream
     *         (position is not 0); rewind support cannot be enabled retroactively
     */
    public void enableRewind() throws IOException {
        enableRewind(null);
    }

    /**
     * Like {@link #enableRewind()}, but supplies a shared {@link CacheMemoryBudget} governing
     * how much may be held in memory before spilling to disk (used only by stream-backed
     * sources); {@code null} falls back to the per-object default.
     *
     * @param budget shared memory budget, or {@code null}
     * @throws IOException if bytes have already been read (position is not 0)
     */
    public void enableRewind(CacheMemoryBudget budget) throws IOException {
        TikaInputSource source = inputSource();
        if (source != null) {
            source.enableRewind(budget);
        }
    }

    /**
     * Returns a read-only random-access {@link SeekableByteChannel} over this stream's full
     * content. Unlike {@link #getPath()}/{@link #getFile()}, this never forces content that is
     * already in memory onto disk: in-memory content is served from memory, file-backed or
     * spilled content from a file channel, and unread stream content is drained through the
     * cache which decides memory-vs-disk as it goes. Use this when random access is needed
     * (e.g. reading a zip central directory); reserve {@code getFile()} for callers that truly
     * need a {@link java.io.File}. The caller owns closing the returned channel. Does not
     * disturb this stream's read position.
     *
     * @throws IOException if this stream has been partially read without rewind enabled
     */
    public SeekableByteChannel getSeekableByteChannel() throws IOException {
        TikaInputSource source = inputSource();
        if (source == null) {
            throw new IOException("No TikaInputSource available");
        }
        return source.getSeekableByteChannel();
    }

    /**
     * Zero-copy, read-only view of the content behind a channel from
     * {@link #getSeekableByteChannel()}, or {@code null} when that content is on disk. Lets a
     * consumer that wants random access (PDFBox, metadata-extractor) read what is already in
     * memory without a second copy; when this returns null the caller should use the file.
     * <p>
     * The view aliases the cache's own array and is valid exactly while {@code channel} is
     * open: the channel pins the array, and the content is fully drained before any channel
     * is handed out. Keep the channel open for as long as the view is in use, then close it
     * -- a view that outlives its channel still reads correctly but is no longer counted
     * against the memory budget.
     */
    public static ByteBuffer inMemoryContent(SeekableByteChannel channel) throws IOException {
        if (channel instanceof MemorySeekableByteChannel) {
            return ((MemorySeekableByteChannel) channel).buffer();
        }
        return null;
    }

    @Override
    public String toString() {
        String str = "TikaInputStream of ";
        // materializedPath(), never getPath(): on a spilled cache the latter drains the
        // source, reopens it and writes metadata -- toString() must not do that
        TikaInputSource source = inputSource();
        Path materialized = source == null ? null : source.materializedPath();
        str += materialized != null ? materialized.toString() : in.toString();
        if (openContainer != null) {
            str += " (in " + openContainer + ")";
        }
        return str;
    }
}
