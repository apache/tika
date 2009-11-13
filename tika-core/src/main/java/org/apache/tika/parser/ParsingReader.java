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

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.PipedReader;
import java.io.PipedWriter;
import java.io.Reader;
import java.io.Writer;
import java.util.concurrent.Executor;

import org.apache.tika.metadata.Metadata;
import org.apache.tika.sax.BodyContentHandler;
import org.xml.sax.ContentHandler;

/**
 * Reader for the text content from a given binary stream. This class
 * uses a background parsing task with a {@link Parser}
 * ({@link AutoDetectParser} by default) to parse the text content from
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
     * Buffered read end of the pipe.
     */
    private final Reader reader;

    /**
     * Write end of the pipe.
     */
    private final Writer writer;

    /**
     * The binary stream being parsed.
     */
    private final InputStream stream;

    /**
     * Metadata associated with the document being parsed.
     */
    private final Metadata metadata;

    /**
     * The parse context.
     */
    private final ParseContext context;

    /**
     * An exception (if any) thrown by the parsing thread.
     */
    private transient Throwable throwable;

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
     * @throws IOException if the document can not be parsed
     */
    public ParsingReader(InputStream stream) throws IOException {
        this(new AutoDetectParser(), stream, new Metadata());
    }

    /**
     * Creates a reader for the text content of the given binary stream
     * with the given name.
     *
     * @param stream binary stream
     * @param name document name
     * @throws IOException if the document can not be parsed
     */
    public ParsingReader(InputStream stream, String name) throws IOException {
        this(new AutoDetectParser(), stream, getMetadata(name));
    }

    /**
     * Creates a reader for the text content of the given file.
     *
     * @param file file
     * @throws FileNotFoundException if the given file does not exist
     * @throws IOException if the document can not be parsed
     */
    public ParsingReader(File file) throws FileNotFoundException, IOException {
        this(new FileInputStream(file), file.getName());
    }

    /**
     * Creates a reader for the text content of the given binary stream
     * with the given document metadata. The given parser is used for
     * parsing. A new background thread is started for the parsing task.
     *
     * @param parser parser instance
     * @param stream binary stream
     * @param metadata document metadata
     * @throws IOException if the document can not be parsed
     */
    public ParsingReader(
            Parser parser, InputStream stream, final Metadata metadata,
            ParseContext context) throws IOException {
        this(parser, stream, metadata, context, new Executor() {
            public void execute(Runnable command) {
                String name = metadata.get(Metadata.RESOURCE_NAME_KEY);
                if (name != null) {
                    name = "Apache Tika: " + name;
                } else {
                    name = "Apache Tika";
                }
                Thread thread = new Thread(command, name);
                thread.setDaemon(true);
                thread.start();
            }
        });
    }

    /**
     * Creates a reader for the text content of the given binary stream
     * with the given document metadata. The given parser is used for the
     * parsing task that is run with the given executor. The given executor
     * <em>must</em> run the parsing task asynchronously in a separate thread,
     * since the current thread must return to the caller that can then
     * consume the parsed text through the {@link Reader} interface.
     *
     * @param parser parser instance
     * @param stream binary stream
     * @param metadata document metadata
     * @param context parsing context
     * @param executor executor for the parsing task
     * @throws IOException if the document can not be parsed
     * @since Apache Tika 0.4
     */
    public ParsingReader(
            Parser parser, InputStream stream, Metadata metadata,
            ParseContext context, Executor executor) throws IOException {
        this.parser = parser;
        PipedReader pipedReader = new PipedReader();
        this.reader = new BufferedReader(pipedReader);
        try {
            this.writer = new PipedWriter(pipedReader);
        } catch (IOException e) {
            throw new IllegalStateException(e); // Should never happen
        }
        this.stream = stream;
        this.metadata = metadata;
        this.context = context;

        executor.execute(new ParsingTask());

        // TIKA-203: Buffer first character to force metadata extraction
        reader.mark(1);
        reader.read();
        reader.reset();
    }

    /**
     * @deprecated This method will be removed in Apache Tika 1.0
     * @see <a href="https://issues.apache.org/jira/browse/TIKA-275">TIKA-275</a>
     */
    public ParsingReader(Parser parser, InputStream stream, Metadata metadata)
            throws IOException {
        this(parser, stream, metadata, new ParseContext());
        context.set(Parser.class, parser);
    }

    /**
     * @deprecated This method will be removed in Apache Tika 1.0
     * @see <a href="https://issues.apache.org/jira/browse/TIKA-275">TIKA-275</a>
     */
    public ParsingReader(
            Parser parser, InputStream stream, Metadata metadata,
            Executor executor) throws IOException {
        this(parser, stream, metadata, new ParseContext(), executor);
        context.set(Parser.class, parser);
    }

    /**
     * The background parsing task.
     */
    private class ParsingTask implements Runnable {

        /**
         * Parses the given binary stream and writes the text content
         * to the write end of the pipe. Potential exceptions (including
         * the one caused if the read end is closed unexpectedly) are
         * stored before the input stream is closed and processing is stopped.
         */
        public void run() {
            try {
                ContentHandler handler = new BodyContentHandler(writer);
                parser.parse(stream, handler, metadata, context);
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
     * @param cbuf character buffer
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
