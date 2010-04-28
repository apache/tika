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
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLConnection;

import org.apache.tika.metadata.Metadata;

/**
 *
 * @since Apache Tika 0.8
 */
public class TikaInputStream extends ProxyInputStream {

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

    public static TikaInputStream get(byte[] data) throws IOException {
        return new TikaInputStream(
                new ByteArrayInputStream(data), null, data.length);
    }

    public static TikaInputStream get(File file) throws IOException {
        return get(file, new Metadata());
    }

    public static TikaInputStream get(File file, Metadata metadata)
            throws IOException {
        metadata.set(Metadata.RESOURCE_NAME_KEY, file.getName());
        metadata.set(Metadata.CONTENT_LENGTH, Long.toString(file.length()));

        return new TikaInputStream(
                new BufferedInputStream(new FileInputStream(file)),
                file, file.length());
    }

    /**
     * 
     * @param uri
     * @return
     * @throws IOException
     */
    public static TikaInputStream get(URI uri) throws IOException {
        return get(uri, new Metadata());
    }

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

    public static TikaInputStream get(URL url) throws IOException {
        return get(url, new Metadata());
    }

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
            metadata.set(Metadata.CONTENT_TYPE, encoding);
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

    private long length;

    /**
     * Current read position within this stream.
     */
    private long position = 0;

    /**
     * 
     * @param stream <em>buffered</em> stream (must support the mark feature)
     * @param file
     * @param length
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
                in = null;
            }
        }
        return file;
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
    public void close() throws IOException {
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
        } else {
            close();
        }
    }

}
