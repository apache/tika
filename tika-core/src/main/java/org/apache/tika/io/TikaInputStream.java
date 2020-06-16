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

import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
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

import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.parser.Parser;

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
 * <p>
 * TikaInputStream includes a few safety features to protect against parsers
 * that may fail to check for an EOF or may incorrectly rely on the unreliable
 * value returned from {@link FileInputStream#skip}.  These parser failures
 * can lead to infinite loops.  We strongly encourage the use of
 * TikaInputStream.
 *
 * @since Apache Tika 0.8
 */
public class TikaInputStream extends TaggedInputStream {

    private static final int MAX_CONSECUTIVE_EOFS = 1000;

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
     * try (TemporaryResources tmp = new TemporaryResources()) {
     *     TikaInputStream stream = TikaInputStream.get(..., tmp);
     *     // process stream but don't close it
     * }
     * </pre>
     * <p>
     * The given stream instance will <em>not</em> be closed when the
     * {@link TemporaryResources#close()} method is called by the
     * try-with-resources statement. The caller is expected to explicitly
     * close the original stream when it's no longer used.
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
            if (!(stream.markSupported())) {
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
     * try (TikaInputStream stream = TikaInputStream.get(...)) {
     *     // process stream
     * }
     * </pre>
     * <p>
     * The given stream instance will be closed along with any other resources
     * associated with the returned TikaInputStream instance when the
     * {@link #close()} method is called by the try-with-resources statement.
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
     * Creates a TikaInputStream from the file at the given path.
     * <p>
     * Note that you must always explicitly close the returned stream to
     * prevent leaking open file handles.
     *
     * @param path input file
     * @return a TikaInputStream instance
     * @throws IOException if an I/O error occurs
     */
    public static TikaInputStream get(Path path) throws IOException {
        return get(path, new Metadata());
    }

    /**
     * Creates a TikaInputStream from the file at the given path. The file name
     * and length are stored as input metadata in the given metadata instance.
     * <p>
     * Note that you must always explicitly close the returned stream to
     * prevent leaking open file handles.
     *
     * @param path input file
     * @param metadata metadata instance
     * @return a TikaInputStream instance
     * @throws IOException if an I/O error occurs
     */
    public static TikaInputStream get(Path path, Metadata metadata)
            throws IOException {
        metadata.set(TikaCoreProperties.RESOURCE_NAME_KEY, path.getFileName().toString());
        metadata.set(Metadata.CONTENT_LENGTH, Long.toString(Files.size(path)));
        return new TikaInputStream(path);
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
     * @deprecated use {@link #get(Path)}. In Tika 2.0, this will be removed
     * or modified to throw an IOException.
     */
    @Deprecated
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
     * or cannot be opened for reading
     * @deprecated use {@link #get(Path, Metadata)}. In Tika 2.0,
     * this will be removed or modified to throw an IOException.
     */
    @Deprecated
    public static TikaInputStream get(File file, Metadata metadata)
            throws FileNotFoundException {
        metadata.set(TikaCoreProperties.RESOURCE_NAME_KEY, file.getName());
        metadata.set(Metadata.CONTENT_LENGTH, Long.toString(file.length()));
        return new TikaInputStream(file);
    }
    
    /**
     * Creates a TikaInputStream from a Factory which can create
     *  fresh {@link InputStream}s for the same resource multiple times.
     * <p>This is typically desired when working with {@link Parser}s that
     *  need to re-read the stream multiple times, where other forms
     *  of buffering (eg File) are slower than just getting a fresh
     *  new stream each time.
     */
    public static TikaInputStream get(InputStreamFactory factory) throws IOException {
        return get(factory, new TemporaryResources());
    }
    /**
     * Creates a TikaInputStream from a Factory which can create
     *  fresh {@link InputStream}s for the same resource multiple times.
     * <p>This is typically desired when working with {@link Parser}s that
     *  need to re-read the stream multiple times, where other forms
     *  of buffering (eg File) are slower than just getting a fresh
     *  new stream each time.
     */
    public static TikaInputStream get(InputStreamFactory factory, TemporaryResources tmp) throws IOException {
        TikaInputStream stream = get(factory.getInputStream(), tmp);
        stream.steamFactory = factory;
        return stream;
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
            Path path = Paths.get(uri);
            if (Files.isRegularFile(path)) {
                return get(path, metadata);
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
                Path path = Paths.get(url.toURI());
                if (Files.isRegularFile(path)) {
                    return get(path, metadata);
                }
            } catch (URISyntaxException e) {
                // fall through
            }
        }

        URLConnection connection = url.openConnection();

        String path = url.getPath();
        int slash = path.lastIndexOf('/');
        if (slash + 1 < path.length()) { // works even with -1!
            metadata.set(TikaCoreProperties.RESOURCE_NAME_KEY, path.substring(slash + 1));
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
     * The Factory that can create fresh {@link InputStream}s for
     *  the resource this reads for, eg when needing to re-read.
     */
    private InputStreamFactory steamFactory;

    /**
     * The path to the file that contains the contents of this stream.
     * This is either the original file passed to the
     * {@link #TikaInputStream(Path)} constructor or a temporary file created
     * by a call to the {@link #getPath()} method. If neither has been called,
     * then the value is <code>null</code>.
     */
    private Path path;

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

    private int consecutiveEOFs = 0;

    private byte[] skipBuffer;
    /**
     * Creates a TikaInputStream instance. This private constructor is used
     * by the static factory methods based on the available information.
     *
     * @param path the path to the file that contains the stream
     * @throws IOException if an I/O error occurs
     */
    private TikaInputStream(Path path) throws IOException {
        super(new BufferedInputStream(Files.newInputStream(path)));
        this.path = path;
        this.tmp = new TemporaryResources();
        this.length = Files.size(path);
    }

    /**
     * Creates a TikaInputStream instance. This private constructor is used
     * by the static factory methods based on the available information.
     *
     * @param file the file that contains the stream
     * @throws FileNotFoundException if the file does not exist
     * @deprecated use {@link #TikaInputStream(Path)}
     */
    @Deprecated
    private TikaInputStream(File file) throws FileNotFoundException {
        super(new BufferedInputStream(new FileInputStream(file)));
        this.path = file.toPath();
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
        this.path = null;
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
     * Returns the open container object if any, such as a
     *  POIFS FileSystem in the event of an OLE2 document 
     *  being detected and processed by the OLE2 detector.
     * @return Open Container for this stream, or <code>null</code> if none 
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
    
    public boolean hasInputStreamFactory() {
        return steamFactory != null;
    }
    
    /**
     * If the Stream was created from an {@link InputStreamFactory},
     *  return that, otherwise <code>null</code>.
     */
    public InputStreamFactory getInputStreamFactory() {
        return steamFactory;
    }

    public boolean hasFile() {
        return path != null;
    }


    /**
     * If the user created this TikaInputStream with a file,
     * the original file will be returned.  If not, the entire stream
     * will be spooled to a temporary file which will be deleted
     * upon the close of this TikaInputStream
     * @return
     * @throws IOException
     */
    public Path getPath() throws IOException {
        return getPath(-1);
    }

    /**
     *
     * @param maxBytes if this is less than 0 and if an underlying file doesn't already exist,
     *                 the full file will be spooled to disk
     * @return the original path used in the initialization of this TikaInputStream,
     * a temporary file if the stream was shorter than <code>maxBytes</code>, or <code>null</code>
     * if the underlying stream was longer than maxBytes.
     * @throws IOException
     */
    public Path getPath(int maxBytes) throws IOException {
        if (path == null) {
            if (position > 0) {
                throw new IOException("Stream is already being read");
            } else {
                Path tmpFile = tmp.createTempFile();
                if (maxBytes > -1) {
                    try (InputStream lookAhead = new LookaheadInputStream(in, maxBytes)) {
                        Files.copy(lookAhead, tmpFile, REPLACE_EXISTING);
                        if (Files.size(tmpFile) >= maxBytes) {
                            //tmpFile will be cleaned up when this TikaInputStream is closed
                            return null;
                        }
                    }
                } else {
                    // Spool the entire stream into a temporary file
                    Files.copy(in, tmpFile, REPLACE_EXISTING);
                }
                //successful so far, set tis' path to tmpFile
                path = tmpFile;

                // Create a new input stream and make sure it'll get closed
                InputStream newStream = Files.newInputStream(path);
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

                length = Files.size(path);
            }
        }
        return path;
    }

    /**
     * @see #getPath()
     */
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

    /**
     * Returns the length (in bytes) of this stream. Note that if the length
     * was not available when this stream was instantiated, then this method
     * will use the {@link #getPath()} method to buffer the entire stream to
     * a temporary file in order to calculate the stream length. This case
     * will only work if the stream has not yet been consumed.
     *
     * @return stream length
     * @throws IOException if the length can not be determined
     */
    public long getLength() throws IOException {
        if (length == -1) {
            getPath(); // updates length internally
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

    /**
     * This relies on {@link IOUtils#skip(InputStream, long)} to ensure
     * that the alleged bytes skipped were actually skipped.
     *
     * @param ln the number of bytes to skip
     * @return the number of bytes skipped
     * @throws IOException if the number of bytes requested to be skipped does not match the number of bytes skipped
     *      or if there's an IOException during the read.
     */
    @Override
    public long skip(long ln) throws IOException {
        //On TIKA-3092, we found that using the static byte array buffer
        //caused problems with multithreading with the FlateInputStream
        //from a POIFS document stream
        if (skipBuffer == null) {
            skipBuffer = new byte[4096];
        }
        long n = IOUtils.skip(super.in, ln, skipBuffer);
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
        consecutiveEOFs = 0;
    }

    @Override
    public void close() throws IOException {
        path = null;
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
    protected void afterRead(int n) throws IOException {
        if (n != -1) {
            position += n;
        } else {
            consecutiveEOFs++;
            if (consecutiveEOFs > MAX_CONSECUTIVE_EOFS) {
                throw new IOException("Read too many -1 (EOFs); there could be an infinite loop." +
                        "If you think your file is not corrupt, please open an issue on Tika's JIRA");
            }
        }
    }

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
