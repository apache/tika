/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.tika.parser;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.PipedReader;
import java.io.PipedWriter;
import java.io.Reader;

import org.apache.tika.metadata.Metadata;
import org.apache.tika.sax.BodyContentHandler;
import org.xml.sax.ContentHandler;

/**
 * Reader for the text content from a given binary stream. This class
 * starts a background thread and uses a {@link Parser}
 * ({@link AutoDetectParser) by default) to parse the text content from
 * a given input stream. The {@link BodyContentHandler} class and a pipe
 * is used to convert the push-based SAX event stream to the pull-based
 * character stream defined by the {@link Reader} interface.
 *
 * @since Apache Tika 0.2
 */
public class ParsingReader extends Reader {

    /**
     * Parser instance used for parsing the given binary stream.
     */
    private final Parser parser;

    /**
     * Read end of the pipe.
     */
    private final PipedReader reader;

    /**
     * Write end of the pipe.
     */
    private final PipedWriter writer;

    /**
     * The binary stream being parsed.
     */
    private final InputStream stream;

    /**
     * Metadata associated with the document being parsed.
     */
    private final Metadata metadata;

    /**
     * An exception (if any) thrown by the parsing thread.
     */
    private Throwable throwable;

    /**
     * Utility method that returns a {@link Metadata} instance
     * for a document with the given name.
     *
     * @param name resource name (or <code>null</code>)
     * @return metadata instance
     */
    private static Metadata getMetadata(String name) {
        Metadata metadata = new Metadata();
        if (name != null && name.length() > 0) {
            metadata.set(Metadata.RESOURCE_NAME_KEY, name);
        }
        return metadata;
    }

    /**
     * Creates a reader for the text content of the given binary stream.
     *
     * @param stream binary stream
     */
    public ParsingReader(InputStream stream) {
        this(new AutoDetectParser(), stream, new Metadata());
    }

    /**
     * Creates a reader for the text content of the given binary stream
     * with the given name.
     *
     * @param stream binary stream
     * @param name document name
     */
    public ParsingReader(InputStream stream, String name) {
        this(new AutoDetectParser(), stream, getMetadata(name));
    }

    /**
     * Creates a reader for the text content of the given file.
     *
     * @param file file
     */
    public ParsingReader(File file) throws FileNotFoundException {
        this(new FileInputStream(file), file.getName());
    }

    /**
     * Creates a reader for the text content of the given binary stream
     * with the given document metadata. The given parser is used for
     * parsing.
     *
     * @param parser parser instance
     * @param stream binary stream
     * @param metadata document metadata
     */
    public ParsingReader(Parser parser, InputStream stream, Metadata metadata) {
        this.parser = parser;
        this.reader = new PipedReader();
        try {
            this.writer = new PipedWriter(reader);
        } catch (IOException e) {
            throw new IllegalStateException(e); // Should never happen
        }
        this.stream = stream;
        this.metadata = metadata;

        String name = metadata.get(Metadata.RESOURCE_NAME_KEY);
        if (name != null) {
            name = "Apache Tika: " + name;
        } else {
            name = "Apache Tika";
        }
        new Thread(new ParsingThread(), name).start();
    }

    /**
     * The background parsing thread.
     */
    private class ParsingThread implements Runnable {

        /**
         * Parses the given binary stream and writes the text content
         * to the write end of the pipe. Potential exceptions (including
         * the one caused if the read end is closed unexpectedly) are
         * stored before the input stream is closed and processing is stopped.
         */
        public void run() {
            try {
                ContentHandler handler = new BodyContentHandler(writer);
                parser.parse(stream, handler, metadata);
            } catch (Throwable t) {
                throwable = t;
            }

            try {
                stream.close();
            } catch (Throwable t) {
                if (throwable == null) {
                    throwable = t;
                }
            }

            try {
                writer.close();
            } catch (Throwable t) {
                if (throwable == null) {
                    throwable = t;
                }
            }
        }

    }

    /**
     * Reads parsed text from the pipe connected to the parsing thread.
     * Fails if the parsing thread has thrown an exception.
     *
     * @param cbuff character buffer
     * @param off start offset within the buffer
     * @param len maximum number of characters to read
     * @throws IOException if the parsing thread has failed or
     *                     if for some reason the pipe does not work properly
     */
    @Override
    public int read(char[] cbuf, int off, int len) throws IOException {
        if (throwable instanceof IOException) {
            throw (IOException) throwable;
        } else if (throwable != null) {
            IOException exception = new IOException("");
            exception.initCause(throwable);
            throw exception;
        }
        return reader.read(cbuf, off, len);
    }

    /**
     * Closes the read end of the pipe. If the parsing thread is still
     * running, next write to the pipe will fail and cause the thread
     * to stop. Thus there is no need to explicitly terminate the thread.
     *
     * @throws IOException if the pipe can not be closed
     */
    @Override
    public void close() throws IOException {
        reader.close();
    }

}
