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
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Blob;
import java.sql.SQLException;

import org.apache.commons.io.input.TaggedInputStream;
import org.apache.commons.io.input.UnsynchronizedByteArrayInputStream;

import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.utils.StringUtils;

/**
 * Input stream with extended capabilities for detection and parsing.
 * <p>
 * This implementation uses a {@link CachingInputStream} for stream-backed
 * sources, which automatically caches all bytes read and supports seeking
 * to any previously-read position. File-backed sources read directly from
 * the file without caching.
 *
 * @since Apache Tika 0.8
 */
public class TikaInputStream extends TaggedInputStream {

    private static final int MAX_CONSECUTIVE_EOFS = 1000;
    private static final int BLOB_SIZE_THRESHOLD = 1024 * 1024;

    private final TemporaryResources tmp;

    // Non-null only for stream-backed (until getPath() spills to file)
    private CachingInputStream cachingStream;

    // Path to backing file (original or temp)
    private Path path;

    private long length;
    private long position = 0;
    private long mark = -1;
    private Object openContainer;
    private int consecutiveEOFs = 0;
    private byte[] skipBuffer;
    private int closeShieldDepth = 0;
    private String suffix = null;

    // ========== Private Constructors ==========

    /**
     * File-backed constructor.
     */
    private TikaInputStream(Path path) throws IOException {
        super(new BufferedInputStream(Files.newInputStream(path)));
        this.path = path;
        this.tmp = new TemporaryResources();
        this.length = Files.size(path);
        this.suffix = FilenameUtils.getSuffixFromPath(path.getFileName().toString());
        this.cachingStream = null;
    }

    private TikaInputStream(Path path, TemporaryResources tmp, long length) throws IOException {
        super(new BufferedInputStream(Files.newInputStream(path)));
        this.path = path;
        this.tmp = tmp;
        this.length = length;
        this.suffix = FilenameUtils.getSuffixFromPath(path.getFileName().toString());
        this.cachingStream = null;
    }

    /**
     * Stream-backed constructor.
     */
    private TikaInputStream(InputStream stream, TemporaryResources tmp, long length, String suffix) {
        super(createCachingStream(stream, tmp));
        this.path = null;
        this.tmp = tmp;
        this.length = length;
        this.suffix = suffix;
        this.cachingStream = (CachingInputStream) in;
    }

    private static CachingInputStream createCachingStream(InputStream stream, TemporaryResources tmp) {
        StreamCache cache = new StreamCache(tmp);
        return new CachingInputStream(
                stream instanceof BufferedInputStream ? stream : new BufferedInputStream(stream),
                cache
        );
    }

    // ========== Static Factory Methods ==========

    public static TikaInputStream get(InputStream stream, TemporaryResources tmp, Metadata metadata) {
        if (stream == null) {
            throw new NullPointerException("The Stream must not be null");
        }
        if (stream instanceof TikaInputStream) {
            return (TikaInputStream) stream;
        }
        return new TikaInputStream(stream, tmp, -1, getExtension(metadata));
    }

    public static TikaInputStream get(InputStream stream) {
        return get(stream, new TemporaryResources(), null);
    }

    public static TikaInputStream get(InputStream stream, Metadata metadata) {
        return get(stream, new TemporaryResources(), metadata);
    }

    public static TikaInputStream cast(InputStream stream) {
        if (stream instanceof TikaInputStream) {
            return (TikaInputStream) stream;
        }
        return null;
    }

    public static TikaInputStream get(byte[] data) throws IOException {
        return get(data, new Metadata());
    }

    public static TikaInputStream get(byte[] data, Metadata metadata) throws IOException {
        metadata.set(Metadata.CONTENT_LENGTH, Integer.toString(data.length));
        return new TikaInputStream(
                UnsynchronizedByteArrayInputStream.builder().setByteArray(data).get(),
                new TemporaryResources(),
                data.length,
                getExtension(metadata)
        );
    }

    public static TikaInputStream get(Path path) throws IOException {
        return get(path, new Metadata());
    }

    public static TikaInputStream get(Path path, Metadata metadata) throws IOException {
        if (StringUtils.isBlank(metadata.get(TikaCoreProperties.RESOURCE_NAME_KEY))) {
            metadata.set(TikaCoreProperties.RESOURCE_NAME_KEY, path.getFileName().toString());
        }
        metadata.set(Metadata.CONTENT_LENGTH, Long.toString(Files.size(path)));
        return new TikaInputStream(path);
    }

    public static TikaInputStream get(Path path, Metadata metadata, TemporaryResources tmp)
            throws IOException {
        long length = Files.size(path);
        if (StringUtils.isBlank(metadata.get(TikaCoreProperties.RESOURCE_NAME_KEY))) {
            metadata.set(TikaCoreProperties.RESOURCE_NAME_KEY, path.getFileName().toString());
        }
        metadata.set(Metadata.CONTENT_LENGTH, Long.toString(length));
        return new TikaInputStream(path, tmp, length);
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
            metadata.set(Metadata.CONTENT_LENGTH, Long.toString(length));
        } catch (SQLException ignore) {
        }

        if (0 <= length && length <= BLOB_SIZE_THRESHOLD) {
            return get(blob.getBytes(1, (int) length), metadata);
        } else {
            return new TikaInputStream(
                    new BufferedInputStream(blob.getBinaryStream()),
                    new TemporaryResources(),
                    length,
                    getExtension(metadata)
            );
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
            metadata.set(Metadata.CONTENT_TYPE, type);
        }

        String encoding = connection.getContentEncoding();
        if (encoding != null) {
            metadata.set(Metadata.CONTENT_ENCODING, encoding);
        }

        int length = connection.getContentLength();
        if (length >= 0) {
            metadata.set(Metadata.CONTENT_LENGTH, Integer.toString(length));
        }

        return new TikaInputStream(
                new BufferedInputStream(connection.getInputStream()),
                new TemporaryResources(),
                length,
                getExtension(metadata)
        );
    }

    public static TikaInputStream getFromContainer(Object openContainer, long length, Metadata metadata)
            throws IOException {
        TikaInputStream tis = TikaInputStream.get(new byte[0], metadata);
        tis.setOpenContainer(openContainer);
        tis.setLength(length);
        metadata.set(Metadata.CONTENT_LENGTH, Long.toString(length));
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

    @Override
    public long skip(long ln) throws IOException {
        if (skipBuffer == null) {
            skipBuffer = new byte[4096];
        }
        long n = IOUtils.skip(in, ln, skipBuffer);
        position += n;
        return n;
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

        if (path != null) {
            // File-backed: close and reopen, skip to mark position
            in.close();
            InputStream newStream = Files.newInputStream(path);
            tmp.addResource(newStream);
            in = new BufferedInputStream(newStream);

            if (mark > 0) {
                if (skipBuffer == null) {
                    skipBuffer = new byte[4096];
                }
                IOUtils.skip(in, mark, skipBuffer);
            }
        } else if (cachingStream != null) {
            // Stream-backed: seek within the cache
            cachingStream.seekTo(mark);
        } else {
            throw new IOException("Cannot reset: no cache and no file backing");
        }

        position = mark;
        mark = -1;
        consecutiveEOFs = 0;
    }

    @Override
    public void close() throws IOException {
        if (closeShieldDepth > 0) {
            return;
        }
        path = null;
        mark = -1;

        tmp.addResource(in);
        tmp.close();
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

        int m = read(buffer);
        while (m != -1) {
            n += m;
            if (n < buffer.length) {
                m = read(buffer, n, buffer.length - n);
            } else {
                m = -1;
            }
        }

        reset();
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

    public boolean hasFile() {
        return path != null;
    }

    public Path getPath() throws IOException {
        if (path != null) {
            return path;
        }

        if (cachingStream == null) {
            throw new IOException("No caching stream available");
        }

        // Spill to file and switch to file-backed mode
        path = cachingStream.spillToFile();

        // Reopen from file at current position
        long savedPosition = position;
        in.close();

        InputStream newStream = Files.newInputStream(path);
        tmp.addResource(newStream);
        in = new BufferedInputStream(newStream);

        // Skip to saved position
        if (savedPosition > 0) {
            if (skipBuffer == null) {
                skipBuffer = new byte[4096];
            }
            IOUtils.skip(in, savedPosition, skipBuffer);
        }

        // Update length
        long sz = Files.size(path);
        if (openContainer != null && sz == 0 && length > -1) {
            // Don't update if open container with 0 size
        } else {
            length = sz;
        }

        // No longer using caching stream
        cachingStream = null;

        return path;
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
        return length != -1;
    }

    public long getLength() throws IOException {
        if (length == -1) {
            getPath();
        }
        return length;
    }

    public long getPosition() {
        return position;
    }

    private void setLength(long length) {
        this.length = length;
    }

    public void setCloseShield() {
        this.closeShieldDepth++;
    }

    public void removeCloseShield() {
        this.closeShieldDepth--;
    }

    public boolean isCloseShield() {
        return closeShieldDepth > 0;
    }

    /**
     * Rewind the stream to the beginning.
     */
    public void rewind() throws IOException {
        mark = 0;
        reset();
        mark = -1;
    }

    @Override
    public String toString() {
        String str = "TikaInputStream of ";
        if (hasFile()) {
            str += path.toString();
        } else {
            str += in.toString();
        }
        if (openContainer != null) {
            str += " (in " + openContainer + ")";
        }
        return str;
    }
}
