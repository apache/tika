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
package org.apache.tika.parser.external;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.apache.commons.io.output.NullOutputStream.NULL_OUTPUT_STREAM;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.Reader;
import java.io.Serializable;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import org.apache.tika.exception.TikaException;
import org.apache.tika.io.TemporaryResources;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.AbstractParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.XHTMLContentHandler;

/**
 * Parser that uses an external program (like catdoc or pdf2txt) to extract
 * text content and metadata from a given document.
 */
public class ExternalParser extends AbstractParser {

    private static final Logger LOG = LoggerFactory.getLogger(ExternalParser.class);

    /**
     * The token, which if present in the Command string, will
     * be replaced with the input filename.
     * Alternately, the input data can be streamed over STDIN.
     */
    public static final String INPUT_FILE_TOKEN = "${INPUT}";
    /**
     * The token, which if present in the Command string, will
     * be replaced with the output filename.
     * Alternately, the output data can be collected on STDOUT.
     */
    public static final String OUTPUT_FILE_TOKEN = "${OUTPUT}";
    private static final long serialVersionUID = -1079128990650687037L;
    //make this parameterizable
    private final long timeoutMs = 60000;
    /**
     * Media types supported by the external program.
     */
    private Set<MediaType> supportedTypes = Collections.emptySet();

    /**
     * Regular Expressions to run over STDOUT to
     * extract Metadata.
     */
    private Map<Pattern, String> metadataPatterns = null;
    /**
     * The external command to invoke.
     *
     * @see Runtime#exec(String[])
     */
    private String[] command = new String[]{"cat"};
    /**
     * A consumer for ignored Lines
     */
    private LineConsumer ignoredLineConsumer = LineConsumer.NULL;

    /**
     * Starts a thread that reads and discards the contents of the
     * standard stream of the given process. Potential exceptions
     * are ignored, and the stream is closed once fully processed.
     * Note: calling this starts a new thread and blocks the current(caller)
     * thread until the new thread dies
     *
     * @param stream stream to be ignored
     */
    private static void ignoreStream(final InputStream stream) {
        ignoreStream(stream, true);
    }

    /**
     * Starts a thread that reads and discards the contents of the
     * standard stream of the given process. Potential exceptions
     * are ignored, and the stream is closed once fully processed.
     *
     * @param stream       stream to sent to black hole (a k a null)
     * @param waitForDeath when {@code true} the caller thread will be
     *                     blocked till the death of new thread.
     * @return The thread that is created and started
     */
    private static Thread ignoreStream(final InputStream stream, boolean waitForDeath) {
        Thread t = new Thread(() -> {
            try {
                IOUtils.copy(stream, NULL_OUTPUT_STREAM);
            } catch (IOException e) {
                //swallow
            } finally {
                IOUtils.closeQuietly(stream);
            }
        });
        t.start();
        if (waitForDeath) {
            try {
                t.join();
            } catch (InterruptedException ignore) {
            }
        }
        return t;
    }

    /**
     * Checks to see if the command can be run. Typically used with
     * something like "myapp --version" to check to see if "myapp"
     * is installed and on the path.
     *
     * @param checkCmd   The check command to run
     * @param errorValue What is considered an error value?
     */
    public static boolean check(String checkCmd, int... errorValue) {
        return check(new String[]{checkCmd}, errorValue);
    }

    public static boolean check(String[] checkCmd, int... errorValue) {
        if (errorValue.length == 0) {
            errorValue = new int[]{127};
        }

        Process process = null;
        try {
            process = Runtime.getRuntime().exec(checkCmd);
            Thread stdErrSuckerThread = ignoreStream(process.getErrorStream(), false);
            Thread stdOutSuckerThread = ignoreStream(process.getInputStream(), false);
            stdErrSuckerThread.join();
            stdOutSuckerThread.join();
            //make the timeout parameterizable
            boolean finished = process.waitFor(60000, TimeUnit.MILLISECONDS);
            if (!finished) {
                throw new TimeoutException();
            }
            int result = process.exitValue();
            LOG.debug("exit value for {}: {}", checkCmd[0], result);
            for (int err : errorValue) {
                if (result == err) {
                    return false;
                }
            }
            return true;
        } catch (IOException | InterruptedException | TimeoutException e) {
            LOG.debug("exception trying to run  " + checkCmd[0], e);
            // Some problem, command is there or is broken
            return false;
        } catch (SecurityException se) {
            // External process execution is banned by the security manager
            throw se;
        } catch (Error err) {
            if (err.getMessage() != null && (err.getMessage().contains("posix_spawn") ||
                    err.getMessage().contains("UNIXProcess"))) {
                LOG.debug("(TIKA-1526): exception trying to run: " + checkCmd[0], err);
                //"Error forking command due to JVM locale bug
                //(see TIKA-1526 and SOLR-6387)"
                return false;
            }
            //throw if a different kind of error
            throw err;
        } finally {
            if (process != null) {
                process.destroyForcibly();
            }
        }
    }

    public Set<MediaType> getSupportedTypes(ParseContext context) {
        return getSupportedTypes();
    }

    public Set<MediaType> getSupportedTypes() {
        return supportedTypes;
    }

    public void setSupportedTypes(Set<MediaType> supportedTypes) {
        this.supportedTypes = Collections.unmodifiableSet(new HashSet<>(supportedTypes));
    }

    public String[] getCommand() {
        return command;
    }

    /**
     * Sets the command to be run. This can include either of
     * {@link #INPUT_FILE_TOKEN} or {@link #OUTPUT_FILE_TOKEN}
     * if the command needs filenames.
     *
     * @see Runtime#exec(String[])
     */
    public void setCommand(String... command) {
        this.command = command;
    }

    /**
     * Gets lines consumer
     *
     * @return consumer instance
     */
    public LineConsumer getIgnoredLineConsumer() {
        return ignoredLineConsumer;
    }

    /**
     * Set a consumer for the lines ignored by the parse functions
     *
     * @param ignoredLineConsumer consumer instance
     */
    public void setIgnoredLineConsumer(LineConsumer ignoredLineConsumer) {
        this.ignoredLineConsumer = ignoredLineConsumer;
    }

    public Map<Pattern, String> getMetadataExtractionPatterns() {
        return metadataPatterns;
    }

    /**
     * Sets the map of regular expression patterns and Metadata
     * keys. Any matching patterns will have the matching
     * metadata entries set.
     * Set this to null to disable Metadata extraction.
     */
    public void setMetadataExtractionPatterns(Map<Pattern, String> patterns) {
        this.metadataPatterns = patterns;
    }

    /**
     * Executes the configured external command and passes the given document
     * stream as a simple XHTML document to the given SAX content handler.
     * Metadata is only extracted if {@link #setMetadataExtractionPatterns(Map)}
     * has been called to set patterns.
     */
    public void parse(InputStream stream, ContentHandler handler, Metadata metadata,
                      ParseContext context) throws IOException, SAXException, TikaException {
        XHTMLContentHandler xhtml = new XHTMLContentHandler(handler, metadata);

        TemporaryResources tmp = new TemporaryResources();
        try {
            parse(TikaInputStream.get(stream, tmp, metadata), xhtml, metadata, tmp);
        } finally {
            tmp.dispose();
        }
    }

    private void parse(TikaInputStream stream, XHTMLContentHandler xhtml, Metadata metadata,
                       TemporaryResources tmp) throws IOException, SAXException, TikaException {
        boolean inputToStdIn = true;
        boolean outputFromStdOut = true;
        boolean hasPatterns = (metadataPatterns != null && !metadataPatterns.isEmpty());

        File output = null;

        // Build our command
        String[] cmd;
        if (command.length == 1) {
            cmd = command[0].split(" ");
        } else {
            cmd = new String[command.length];
            System.arraycopy(command, 0, cmd, 0, command.length);
        }
        for (int i = 0; i < cmd.length; i++) {
            if (cmd[i].contains(INPUT_FILE_TOKEN)) {
                cmd[i] = cmd[i].replace(INPUT_FILE_TOKEN, stream.getFile().getPath());
                inputToStdIn = false;
            }
            if (cmd[i].contains(OUTPUT_FILE_TOKEN)) {
                output = tmp.createTemporaryFile();
                outputFromStdOut = false;
                cmd[i] = cmd[i].replace(OUTPUT_FILE_TOKEN, output.getPath());
            }
        }

        // Execute
        Process process = null;
        try {
            if (cmd.length == 1) {
                process = Runtime.getRuntime().exec(cmd[0]);
            } else {
                process = Runtime.getRuntime().exec(cmd);
            }
        } catch (Exception e) {
            LOG.warn("problem with process exec", e);
        }

        try {
            if (inputToStdIn) {
                sendInput(process, stream);
            } else {
                process.getOutputStream().close();
            }

            InputStream out = process.getInputStream();
            InputStream err = process.getErrorStream();

            if (hasPatterns) {
                extractMetadata(err, metadata);

                if (outputFromStdOut) {
                    extractOutput(out, xhtml);
                } else {
                    extractMetadata(out, metadata);
                }
            } else {
                ignoreStream(err);

                if (outputFromStdOut) {
                    extractOutput(out, xhtml);
                } else {
                    ignoreStream(out);
                }
            }
        } finally {
            try {
                process.waitFor();
            } catch (InterruptedException ignore) {
            }
        }

        // Grab the output if we haven't already
        if (!outputFromStdOut) {
            try (FileInputStream fileInputStream = new FileInputStream(output)) {
                extractOutput(fileInputStream, xhtml);
            }
        }
    }

    /**
     * Starts a thread that extracts the contents of the standard output
     * stream of the given process to the given XHTML content handler.
     * The standard output stream is closed once fully processed.
     *
     * @param stream
     * @param xhtml  XHTML content handler
     * @throws SAXException if the XHTML SAX events could not be handled
     * @throws IOException  if an input error occurred
     */
    private void extractOutput(InputStream stream, XHTMLContentHandler xhtml)
            throws SAXException, IOException {
        try (Reader reader = new InputStreamReader(stream, UTF_8)) {
            xhtml.startDocument();
            xhtml.startElement("p");
            char[] buffer = new char[1024];
            for (int n = reader.read(buffer); n != -1; n = reader.read(buffer)) {
                xhtml.characters(buffer, 0, n);
            }
            xhtml.endElement("p");
            xhtml.endDocument();
        }
    }

    /**
     * Starts a thread that sends the contents of the given input stream
     * to the standard input stream of the given process. Potential
     * exceptions are ignored, and the standard input stream is closed
     * once fully processed. Note that the given input stream is <em>not</em>
     * closed by this method.
     *
     * @param process process
     * @param stream  input stream
     */
    private void sendInput(final Process process, final InputStream stream) {
        Thread t = new Thread(() -> {
            OutputStream stdin = process.getOutputStream();
            try {
                IOUtils.copy(stream, stdin);
            } catch (IOException e) {
                //swallow
            }
        });
        t.start();
        try {
            t.join();
        } catch (InterruptedException ignore) {
        }
    }

    private void extractMetadata(final InputStream stream, final Metadata metadata) {
        Thread t = new Thread(() -> {
            BufferedReader reader;
            reader = new BufferedReader(new InputStreamReader(stream, UTF_8));
            try {
                String line;
                while ((line = reader.readLine()) != null) {
                    boolean consumed = false;
                    for (Map.Entry<Pattern, String> entry : metadataPatterns.entrySet()) {
                        Matcher m = entry.getKey().matcher(line);
                        if (m.find()) {
                            consumed = true;
                            if (entry.getValue() != null &&
                                    !entry.getValue().equals("")) {
                                metadata.add(entry.getValue(), m.group(1));
                            } else {
                                metadata.add(m.group(1), m.group(2));
                            }
                        }
                    }
                    if (!consumed) {
                        ignoredLineConsumer.consume(line);
                    }
                }
            } catch (IOException e) {
                // Ignore
            } finally {
                IOUtils.closeQuietly(reader);
                IOUtils.closeQuietly(stream);
            }
        });
        t.start();
        try {
            t.join();
        } catch (InterruptedException ignore) {
        }
    }

    /**
     * Consumer contract
     *
     * @since Apache Tika 1.14
     */
    public interface LineConsumer extends Serializable {
        /**
         * A null consumer
         */
        LineConsumer NULL = line -> {
            // ignores
        };

        /**
         * Consume a line
         *
         * @param line a line of string
         */
        void consume(String line);
    }


}
