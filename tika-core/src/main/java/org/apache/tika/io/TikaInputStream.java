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
public class TikaInputStream extends ProxyInputStream {

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
     *
     * @param stream normal input stream
     * @return a TikaInputStream instance
     */
    public static TikaInputStream get(InputStream stream) {
        if (stream instanceof TikaInputStream) {
            return (TikaInputStream) stream;
        } else {
            return new TikaInputStream(
                    new BufferedInputStream(stream), null, -1);
        }
    }

    /**
     * Creates a TikaInputStream from the given array of bytes.
     *
     * @param data input data
     * @return a TikaInputStream instance
     * @throws IOException
     */
    public static TikaInputStream get(byte[] data) {
        return get(data, new Metadata());
    }

    /**
     * Creates a TikaInputStream from the given array of bytes. The length of
     * the array is stored as input metadata in the given metadata instance.
     *
     * @param data input data
     * @param metadata metadata instance
     * @return a TikaInputStream instance
     * @throws IOException
     */
    public static TikaInputStream get(byte[] data, Metadata metadata) {
        metadata.set(Metadata.CONTENT_LENGTH, Integer.toString(data.length));

        return new TikaInputStream(
                new ByteArrayInputStream(data), null, data.length);
    }

    /**
     * Creates a TikaInputStream from the given file.
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

        return new TikaInputStream(
                new BufferedInputStream(new FileInputStream(file)),
                file, file.length());
    }

    /**
     * Creates a TikaInputStream from the given database BLOB.
     * <p>
     * Note that the result set containing the BLOB may need to be kept open
     * until the returned TikaInputStream has been processed and closed.
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
                    null, length);
        }
    }

    /**
     * Creates a TikaInputStream from the resource at the given URI.
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
                null, length);
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
     * Flag to indicate that {@link #file} is a temporary file that should
     * be removed when this stream is {@link #close() closed}.
     */
    private boolean temporary;

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
     * @param stream <em>buffered</em> stream (must support the mark feature)
     * @param file the file that contains the stream, or <code>null</code>
     * @param length total length of the stream, or -1 if unknown
     */
    private TikaInputStream(InputStream stream, File file, long length) {
        super(stream);
        this.file = file;
        this.temporary = (file == null);
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
    }

    public boolean hasFile() {
        return file != null;
    }

    public File getFile() throws IOException {
        if (file == null) {
            if (in == null) {
                throw new IOException("Stream has already been read");
            } else if (position > 0) {
                throw new IOException("Stream is already being read");
            } else {
                file = File.createTempFile("apache-tika-", ".tmp");
                OutputStream out = new FileOutputStream(file);
                try {
                    IOUtils.copy(in, out);
                } finally {
                    out.close();
                }
                in.close();
                // Re-point the stream at the file now we have it
                in = new BufferedInputStream(new FileInputStream(file));
            }
        }
        return file;
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

    @Override
    public int available() throws IOException {
        if (in == null && file == null) {
            return 0;
        } else {
            return super.available();
        }
    }

    @Override
    public long skip(long ln) throws IOException {
        if (in == null && file == null) {
            return 0;
        } else {
            long n = super.skip(ln);
            position += n;
            return n;
        }
    }

    @Override
    public int read() throws IOException {
        if (in == null && file == null) {
            return -1;
        } else {
            return super.read();
        }
    }

    @Override
    public int read(byte[] bts, int off, int len) throws IOException {
        if (in == null && file == null) {
            return -1;
        } else {
            return super.read(bts, off, len);
        }
    }

    @Override
    public int read(byte[] bts) throws IOException {
        return read(bts, 0, bts.length);
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
        if (openContainer != null) {
            openContainer = null;
        }
        if (in != null) {
            in.close();
            in = null;
        }
        if (file != null) {
            if (temporary) {
                file.delete();
            }
            file = null;
        }
    }

    @Override
    protected void beforeRead(int n) throws IOException {
        if (in == null) {
            if (file != null) {
                in = new FileInputStream(file);
            } else {
                throw new IOException("End of the stream reached");
            }
        }
    }

    @Override
    protected void afterRead(int n) throws IOException {
        if (n != -1) {
            position += n;
        } else if (mark == -1) {
            close();
        }
    }

}
