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
import java.io.ByteArrayInputStream;
import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLConnection;
import java.nio.channels.FileChannel;
import java.sql.Blob;
import java.sql.SQLException;

import org.apache.tika.metadata.Metadata;

/**
 * Input stream with extended capabilities. The purpose of this class is
 * to allow files and other resources and information to be associated with
 * the {@link InputStream} instance passed through the
 * {@link org.apache.tika.parser.Parser} interface and other similar APIs.
 * <p>
 * TikaInputStream instances can be created using the various static
 * <code>get()</code> factory methods. Most of these methods take an optional
 * {@link Metadata} argument that is then filled with the available input
 * metadata from the given resource. The created TikaInputStream instance
 * keeps track of the original resource used to create it, while behaving
 * otherwise just like a normal, buffered {@link InputStream}.
 * A TikaInputStream instance is also guaranteed to support the
 * {@link #mark(int)} feature.
 * <p>
 * Code that wants to access the underlying file or other resources
 * associated with a TikaInputStream should first use the
 * {@link #get(InputStream)} factory method to cast or wrap a given
 * {@link InputStream} into a TikaInputStream instance.
 *
 * @since Apache Tika 0.8
 */
public class TikaInputStream extends TaggedInputStream {

    /**
     * Checks whether the given stream is a TikaInputStream instance.
     * The given stream can be <code>null</code>, in which case the return
     * value is <code>false</code>.
     * 
     * @param stream input stream, possibly <code>null</code>
     * @return <code>true</code> if the stream is a TikaInputStream instance,
     *         <code>false</code> otherwise
     */
    public static boolean isTikaInputStream(InputStream stream) {
        return stream instanceof TikaInputStream;
    }

    /**
     * Casts or wraps the given stream to a TikaInputStream instance.
     * This method can be used to access the functionality of this class
     * even when given just a normal input stream instance.
     * <p>
     * The given temporary file provider is used for any temporary files,
     * and should be disposed when the returned stream is no longer used.
     * <p>
     * Use this method instead of the {@link #get(InputStream)} alternative
     * when you <em>don't</em> explicitly close the returned stream. The
     * recommended access pattern is:
     * <pre>
     * TemporaryResources tmp = new TemporaryResources();
     * try {
     *     TikaInputStream stream = TikaInputStream.get(..., tmp);
     *     // process stream but don't close it
     * } finally {
     *     tmp.close();
     * }
     * </pre>
     * <p>
     * The given stream instance will <em>not</em> be closed when the
     * {@link TemporaryResources#close()} method is called. The caller
     * is expected to explicitly close the original stream when it's no
     * longer used.
     *
     * @since Apache Tika 0.10
     * @param stream normal input stream
     * @return a TikaInputStream instance
     */
    public static TikaInputStream get(
            InputStream stream, TemporaryResources tmp) {
        if (stream == null) {
            throw new NullPointerException("The Stream must not be null");
        }
        if (stream instanceof TikaInputStream) {
            return (TikaInputStream) stream;
        } else {
            // Make sure that the stream is buffered and that it
            // (properly) supports the mark feature
            if (!(stream instanceof BufferedInputStream)
                    && !(stream instanceof ByteArrayInputStream)) {
                stream = new BufferedInputStream(stream);
            }
            return new TikaInputStream(stream, tmp, -1);
        }
    }

    /**
     * Casts or wraps the given stream to a TikaInputStream instance.
     * This method can be used to access the functionality of this class
     * even when given just a normal input stream instance.
     * <p>
     * Use this method instead of the
     * {@link #get(InputStream, TemporaryResources)} alternative when you
     * <em>do</em> explicitly close the returned stream. The recommended
     * access pattern is:
     * <pre>
     * TikaInputStream stream = TikaInputStream.get(...);
     * try {
     *     // process stream
     * } finally {
     *     stream.close();
     * }
     * </pre>
     * <p>
     * The given stream instance will be closed along with any other resources
     * associated with the returned TikaInputStream instance when the
     * {@link #close()} method is called.
     *
     * @param stream normal input stream
     * @return a TikaInputStream instance
     */
    public static TikaInputStream get(InputStream stream) {
        return get(stream, new TemporaryResources());
    }

    /**
     * Returns the given stream casts to a TikaInputStream, or
     * <code>null</code> if the stream is not a TikaInputStream.
     *
     * @since Apache Tika 0.10
     * @param stream normal input stream
     * @return a TikaInputStream instance
     */
    public static TikaInputStream cast(InputStream stream) {
        if (stream instanceof TikaInputStream) {
            return (TikaInputStream) stream;
        } else {
            return null;
        }
    }

    /**
     * Creates a TikaInputStream from the given array of bytes.
     * <p>
     * Note that you must always explicitly close the returned stream as in
     * some cases it may end up writing the given data to a temporary file.
     *
     * @param data input data
     * @return a TikaInputStream instance
     */
    public static TikaInputStream get(byte[] data) {
        return get(data, new Metadata());
    }

    /**
     * Creates a TikaInputStream from the given array of bytes. The length of
     * the array is stored as input metadata in the given metadata instance.
     * <p>
     * Note that you must always explicitly close the returned stream as in
     * some cases it may end up writing the given data to a temporary file.
     *
     * @param data input data
     * @param metadata metadata instance
     * @return a TikaInputStream instance
     * @throws IOException
     */
    public static TikaInputStream get(byte[] data, Metadata metadata) {
        metadata.set(Metadata.CONTENT_LENGTH, Integer.toString(data.length));
        return new TikaInputStream(
                new ByteArrayInputStream(data),
                new TemporaryResources(), data.length);
    }

    /**
     * Creates a TikaInputStream from the given file.
     * <p>
     * Note that you must always explicitly close the returned stream to
     * prevent leaking open file handles.
     *
     * @param file input file
     * @return a TikaInputStream instance
     * @throws FileNotFoundException if the file does not exist
     */
    public static TikaInputStream get(File file) throws FileNotFoundException {
        return get(file, new Metadata());
    }

    /**
     * Creates a TikaInputStream from the given file. The file name and
     * length are stored as input metadata in the given metadata instance.
     * <p>
     * Note that you must always explicitly close the returned stream to
     * prevent leaking open file handles.
     *
     * @param file input file
     * @param metadata metadata instance
     * @return a TikaInputStream instance
     * @throws FileNotFoundException if the file does not exist
     */
    public static TikaInputStream get(File file, Metadata metadata)
            throws FileNotFoundException {
        metadata.set(Metadata.RESOURCE_NAME_KEY, file.getName());
        metadata.set(Metadata.CONTENT_LENGTH, Long.toString(file.length()));
        return new TikaInputStream(file);
    }

    /**
     * Creates a TikaInputStream from the given database BLOB.
     * <p>
     * Note that the result set containing the BLOB may need to be kept open
     * until the returned TikaInputStream has been processed and closed.
     * You must also always explicitly close the returned stream as in
     * some cases it may end up writing the blob data to a temporary file.
     *
     * @param blob database BLOB
     * @return a TikaInputStream instance
     * @throws SQLException if BLOB data can not be accessed
     */
    public static TikaInputStream get(Blob blob) throws SQLException {
        return get(blob, new Metadata());
    }

    /**
     * Blob size threshold that limits the largest BLOB size to be
     * buffered fully in memory by the {@link #get(Blob, Metadata)}
     * method.
     */
    private static final int BLOB_SIZE_THRESHOLD = 1024 * 1024;

    /**
     * Creates a TikaInputStream from the given database BLOB. The BLOB
     * length (if available) is stored as input metadata in the given
     * metadata instance.
     * <p>
     * Note that the result set containing the BLOB may need to be kept open
     * until the returned TikaInputStream has been processed and closed.
     * You must also always explicitly close the returned stream as in
     * some cases it may end up writing the blob data to a temporary file.
     *
     * @param blob database BLOB
     * @param metadata metadata instance
     * @return a TikaInputStream instance
     * @throws SQLException if BLOB data can not be accessed
     */
    public static TikaInputStream get(Blob blob, Metadata metadata)
            throws SQLException {
        long length = -1;
        try {
            length = blob.length();
            metadata.set(Metadata.CONTENT_LENGTH, Long.toString(length));
        } catch (SQLException ignore) {
        }

        // Prefer an in-memory buffer for reasonably sized blobs to reduce
        // the likelihood of problems caused by long-lived database accesses
        if (0 <= length && length <= BLOB_SIZE_THRESHOLD) {
            // the offset in Blob.getBytes() starts at 1
            return get(blob.getBytes(1, (int) length), metadata);
        } else {
            return new TikaInputStream(
                    new BufferedInputStream(blob.getBinaryStream()),
                    new TemporaryResources(), length);
        }
    }

    /**
     * Creates a TikaInputStream from the resource at the given URI.
     * <p>
     * Note that you must always explicitly close the returned stream as in
     * some cases it may end up writing the resource to a temporary file.
     *
     * @param uri resource URI
     * @return a TikaInputStream instance
     * @throws IOException if the resource can not be accessed
     */
    public static TikaInputStream get(URI uri) throws IOException {
        return get(uri, new Metadata());
    }

    /**
     * Creates a TikaInputStream from the resource at the given URI. The
     * available input metadata is stored in the given metadata instance.
     * <p>
     * Note that you must always explicitly close the returned stream as in
     * some cases it may end up writing the resource to a temporary file.
     *
     * @param uri resource URI
     * @param metadata metadata instance
     * @return a TikaInputStream instance
     * @throws IOException if the resource can not be accessed
     */
    public static TikaInputStream get(URI uri, Metadata metadata)
            throws IOException {
        // Special handling for file:// URIs
        if ("file".equalsIgnoreCase(uri.getScheme())) {
            File file = new File(uri);
            if (file.isFile()) {
                return get(file, metadata);
            }
        }

        return get(uri.toURL(), metadata);
    }

    /**
     * Creates a TikaInputStream from the resource at the given URL.
     * <p>
     * Note that you must always explicitly close the returned stream as in
     * some cases it may end up writing the resource to a temporary file.
     *
     * @param url resource URL
     * @return a TikaInputStream instance
     * @throws IOException if the resource can not be accessed
     */
    public static TikaInputStream get(URL url) throws IOException {
        return get(url, new Metadata());
    }

    /**
     * Creates a TikaInputStream from the resource at the given URL. The
     * available input metadata is stored in the given metadata instance.
     * <p>
     * Note that you must always explicitly close the returned stream as in
     * some cases it may end up writing the resource to a temporary file.
     *
     * @param url resource URL
     * @param metadata metadata instance
     * @return a TikaInputStream instance
     * @throws IOException if the resource can not be accessed
     */
    public static TikaInputStream get(URL url, Metadata metadata)
            throws IOException {
        // Special handling for file:// URLs
        if ("file".equalsIgnoreCase(url.getProtocol())) {
            try {
                File file = new File(url.toURI());
                if (file.isFile()) {
                    return get(file, metadata);
                }
            } catch (URISyntaxException e) {
                // fall through
            }
        }

        URLConnection connection = url.openConnection();

        String path = url.getPath();
        int slash = path.lastIndexOf('/');
        if (slash + 1 < path.length()) { // works even with -1!
            metadata.set(Metadata.RESOURCE_NAME_KEY, path.substring(slash + 1));
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
                new TemporaryResources(), length);
    }

    /**
     * The file that contains the contents of this stream. This is either
     * the original file passed to the {@link #TikaInputStream(File)}
     * constructor or a temporary file created by a call to the
     * {@link #getFile()} method. If neither has been called, then
     * the value is <code>null</code>.
     */
    private File file;

    /**
     * Tracker of temporary resources.
     */
    private final TemporaryResources tmp;

    /**
     * Total length of the stream, or -1 if unknown.
     */
    private long length;

    /**
     * Current read position within this stream.
     */
    private long position = 0;

    /**
     * Marked position, or -1 if there is no current mark.
     */
    private long mark = -1;

    /**
     * A opened container, such as a POIFS FileSystem
     *  for an OLE2 document, or a Zip file for a
     *  zip based (eg ooxml, odf) document.
     */
    private Object openContainer;

    /**
     * Creates a TikaInputStream instance. This private constructor is used
     * by the static factory methods based on the available information.
     *
     * @param file the file that contains the stream
     * @throws FileNotFoundException if the file does not exist
     */
    private TikaInputStream(File file) throws FileNotFoundException {
        super(new BufferedInputStream(new FileInputStream(file)));
        this.file = file;
        this.tmp = new TemporaryResources();
        this.length = file.length();
    }

    /**
     * Creates a TikaInputStream instance. This private constructor is used
     * by the static factory methods based on the available information.
     * <p>
     * The given stream needs to be included in the given temporary resource
     * collection if the caller wants it also to get closed when the
     * {@link #close()} method is invoked.
     *
     * @param stream <em>buffered</em> stream (must support the mark feature)
     * @param tmp tracker for temporary resources associated with this stream
     * @param length total length of the stream, or -1 if unknown
     */
    private TikaInputStream(
            InputStream stream, TemporaryResources tmp, long length) {
        super(stream);
        this.file = null;
        this.tmp = tmp;
        this.length = length;
    }

    /**
     * Fills the given buffer with upcoming bytes from this stream without
     * advancing the current stream position. The buffer is filled up unless
     * the end of stream is encountered before that. This method will block
     * if not enough bytes are immediately available.
     *
     * @param buffer byte buffer
     * @return number of bytes written to the buffer
     * @throws IOException if the stream can not be read
     */
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
    
    /**
     * Returns the open container object, such as a
     *  POIFS FileSystem in the event of an OLE2
     *  document being detected and processed by
     *  the OLE2 detector. 
     */
    public Object getOpenContainer() {
        return openContainer;
    }
    
    /**
     * Stores the open container object against
     *  the stream, eg after a Zip contents 
     *  detector has loaded the file to decide
     *  what it contains.
     */
    public void setOpenContainer(Object container) {
        openContainer = container;
        if (container instanceof Closeable) {
            tmp.addResource((Closeable) container);
        }
    }

    public boolean hasFile() {
        return file != null;
    }

    public File getFile() throws IOException {
        if (file == null) {
            if (position > 0) {
                throw new IOException("Stream is already being read");
            } else {
                // Spool the entire stream into a temporary file
                file = tmp.createTemporaryFile();
                OutputStream out = new FileOutputStream(file);
                try {
                    IOUtils.copy(in, out);
                } finally {
                    out.close();
                }

                // Create a new input stream and make sure it'll get closed
                FileInputStream newStream = new FileInputStream(file);
                tmp.addResource(newStream);

                // Replace the spooled stream with the new stream in a way
                // that still ends up closing the old stream if or when the
                // close() method is called. The closing of the new stream
                // is already being handled as noted above.
                final InputStream oldStream = in;
                in = new BufferedInputStream(newStream) {
                    @Override
                    public void close() throws IOException {
                        oldStream.close();
                    }
                };

                length = file.length();
            }
        }
        return file;
    }

    public FileChannel getFileChannel() throws IOException {
        FileInputStream fis = new FileInputStream(getFile());
        tmp.addResource(fis);
        FileChannel channel = fis.getChannel();
        tmp.addResource(channel);
        return channel;
    }

    public boolean hasLength() {
        return length != -1;
    }

    /**
     * Returns the length (in bytes) of this stream. Note that if the length
     * was not available when this stream was instantiated, then this method
     * will use the {@link #getFile()} method to buffer the entire stream to
     * a temporary file in order to calculate the stream length. This case
     * will only work if the stream has not yet been consumed.
     *
     * @return stream length
     * @throws IOException if the length can not be determined
     */
    public long getLength() throws IOException {
        if (length == -1) {
            length = getFile().length();
        }
        return length;
    }

    /**
     * Returns the current position within the stream.
     *
     * @return stream position
     */
    public long getPosition() {
        return position;
    }

    @Override
    public long skip(long ln) throws IOException {
        long n = super.skip(ln);
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
        super.reset();
        position = mark;
        mark = -1;
    }

    @Override
    public void close() throws IOException {
        file = null;
        mark = -1;

        // The close method was explicitly called, so we indeed
        // are expected to close the input stream. Handle that
        // by adding that stream as a resource to be tracked before
        // closing all of them. This way also possible exceptions from
        // the close() calls get managed properly.
        tmp.addResource(in);
        tmp.close();
    }

    @Override
    protected void afterRead(int n) {
        if (n != -1) {
            position += n;
        }
    }

    public String toString() {
        String str = "TikaInputStream of ";
        if (hasFile()) {
            str += file.toString();
        } else {
            str += in.toString();
        }
        if (openContainer != null) {
            str += " (in " + openContainer + ")";
        }
        return str;
    }
}
