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

import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.utils.StringUtils;

/**
 * Input stream with extended capabilities for detection and parsing.
 * <p>
 * This implementation uses backing strategies to handle different input types:
 * <ul>
 *   <li>{@link ByteArrayBackedStrategy} for byte[] inputs - no caching needed</li>
 *   <li>{@link FileBackedStrategy} for Path/File inputs - direct file access</li>
 *   <li>{@link StreamBackedStrategy} for InputStream inputs - caches bytes as read</li>
 * </ul>
 *
 * @since Apache Tika 0.8
 */
public class TikaInputStream extends TaggedInputStream {

    private static final int MAX_CONSECUTIVE_EOFS = 1000;
    private static final int BLOB_SIZE_THRESHOLD = 1024 * 1024;

    protected TemporaryResources tmp;

    // The backing strategy handles read, seek, and file access
    private InputStreamBackingStrategy strategy;

    private long position = 0;
    private long mark = -1;
    private Object openContainer;
    private int consecutiveEOFs = 0;
    private byte[] skipBuffer;
    private int closeShieldDepth = 0;
    private String suffix = null;
    private long overrideLength = -1;  // For getFromContainer() to set explicit length

    // ========== Constructors ==========

    /**
     * Protected constructor for subclasses.
     */
    protected TikaInputStream(InputStream stream, long length) {
        super(stream);
        this.tmp = null;
        this.strategy = null;
    }

    /**
     * Strategy-based constructor.
     */
    private TikaInputStream(InputStreamBackingStrategy strategy, TemporaryResources tmp, String suffix) {
        super(new StrategyInputStream(strategy));
        this.strategy = strategy;
        this.tmp = tmp;
        this.suffix = suffix;
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
        InputStreamBackingStrategy strategy = new StreamBackedStrategy(stream, tmp, -1);
        return new TikaInputStream(strategy, tmp, ext);
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
        String ext = getExtension(metadata);
        TemporaryResources tmp = new TemporaryResources();
        InputStreamBackingStrategy strategy = new ByteArrayBackedStrategy(data);
        return new TikaInputStream(strategy, tmp, ext);
    }

    public static TikaInputStream get(Path path) throws IOException {
        return get(path, new Metadata());
    }

    public static TikaInputStream get(Path path, Metadata metadata) throws IOException {
        if (StringUtils.isBlank(metadata.get(TikaCoreProperties.RESOURCE_NAME_KEY))) {
            metadata.set(TikaCoreProperties.RESOURCE_NAME_KEY, path.getFileName().toString());
        }
        metadata.set(Metadata.CONTENT_LENGTH, Long.toString(Files.size(path)));
        String ext = FilenameUtils.getSuffixFromPath(path.getFileName().toString());
        TemporaryResources tmp = new TemporaryResources();
        InputStreamBackingStrategy strategy = new FileBackedStrategy(path);
        return new TikaInputStream(strategy, tmp, ext);
    }

    public static TikaInputStream get(Path path, Metadata metadata, TemporaryResources tmp)
            throws IOException {
        long length = Files.size(path);
        if (StringUtils.isBlank(metadata.get(TikaCoreProperties.RESOURCE_NAME_KEY))) {
            metadata.set(TikaCoreProperties.RESOURCE_NAME_KEY, path.getFileName().toString());
        }
        metadata.set(Metadata.CONTENT_LENGTH, Long.toString(length));
        String ext = FilenameUtils.getSuffixFromPath(path.getFileName().toString());
        InputStreamBackingStrategy strategy = new FileBackedStrategy(path);
        return new TikaInputStream(strategy, tmp, ext);
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
            String ext = getExtension(metadata);
            TemporaryResources tmp = new TemporaryResources();
            InputStreamBackingStrategy strategy = new StreamBackedStrategy(
                    new BufferedInputStream(blob.getBinaryStream()), tmp, length);
            return new TikaInputStream(strategy, tmp, ext);
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

        String ext = getExtension(metadata);
        TemporaryResources tmp = new TemporaryResources();
        InputStreamBackingStrategy strategy = new StreamBackedStrategy(
                new BufferedInputStream(connection.getInputStream()), tmp, length);
        return new TikaInputStream(strategy, tmp, ext);
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

        if (strategy != null) {
            strategy.seekTo(mark);
        } else {
            throw new IOException("Cannot reset: no strategy available");
        }

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

        if (strategy != null) {
            tmp.addResource(strategy);
        } else {
            tmp.addResource(in);
        }
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
        return strategy != null && strategy.hasPath();
    }

    public Path getPath() throws IOException {
        if (strategy == null) {
            throw new IOException("No strategy available");
        }
        return strategy.getPath(tmp, suffix);
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
        return strategy != null && strategy.getLength() != -1;
    }

    public long getLength() throws IOException {
        if (overrideLength >= 0) {
            return overrideLength;
        }
        if (strategy == null) {
            return -1;
        }
        long len = strategy.getLength();
        if (len == -1) {
            // Force spill to get length
            getPath();
            len = strategy.getLength();
        }
        return len;
    }

    public long getPosition() {
        return position;
    }

    protected void setPosition(long position) {
        this.position = position;
    }

    private void setLength(long length) {
        this.overrideLength = length;
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
            try {
                str += getPath().toString();
            } catch (IOException e) {
                str += "unknown path";
            }
        } else {
            str += in.toString();
        }
        if (openContainer != null) {
            str += " (in " + openContainer + ")";
        }
        return str;
    }
}
