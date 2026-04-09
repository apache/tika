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

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import org.apache.tika.config.ConfigDeserializer;
import org.apache.tika.config.JsonConfig;
import org.apache.tika.config.TikaComponent;
import org.apache.tika.config.TikaProgressTracker;
import org.apache.tika.config.TimeoutLimits;
import org.apache.tika.exception.TikaException;
import org.apache.tika.io.TemporaryResources;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.ExternalProcess;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.sax.BodyContentHandler;
import org.apache.tika.sax.XHTMLContentHandler;
import org.apache.tika.utils.FileProcessResult;
import org.apache.tika.utils.ProcessUtils;

/**
 * Parser that uses an external program (like ffmpeg, exiftool or sox)
 * to extract text content and metadata from a given document.
 * <p>
 * This parser relies on JSON configuration rather than classpath auto-discovery.
 * Users can specify independent handlers for each process stream:
 * <ul>
 *   <li>{@code stdoutHandler} — processes stdout</li>
 *   <li>{@code stderrHandler} — processes stderr</li>
 *   <li>{@code outputFileHandler} — processes the output file</li>
 * </ul>
 * The {@code contentSource} field controls which stream provides the XHTML
 * content output. An optional {@code checkCommandLine} lazily verifies the
 * external tool is available.
 */
@TikaComponent
public class ExternalParser implements Parser {

    public static final long DEFAULT_TIMEOUT_MS = 60000;

    public static final String INPUT_FILE_TOKEN = "${INPUT_FILE}";

    public static final String OUTPUT_FILE_TOKEN = "${OUTPUT_FILE}";

    private static final Pattern INPUT_TOKEN_MATCHER =
            Pattern.compile("\\$\\{INPUT_FILE}");
    private static final Pattern OUTPUT_TOKEN_MATCHER =
            Pattern.compile("\\$\\{OUTPUT_FILE}");

    private static final Logger LOG = LoggerFactory.getLogger(ExternalParser.class);

    private static final ContentHandler DISCARD_HANDLER =
            new org.xml.sax.helpers.DefaultHandler();

    private final ExternalParserConfig config;

    // Cached values derived from config
    private final Set<MediaType> supportedTypes;
    private final List<String> commandLine;
    private final Parser stdoutHandler;
    private final Parser stderrHandler;
    private final Parser outputFileHandler;

    // Lazy check state
    private final String[] checkCmd;
    private final int[] checkErrorCodes;
    private volatile Boolean checkResult;

    /**
     * Default constructor - not typically useful since ExternalParser requires configuration.
     */
    public ExternalParser() {
        this(new ExternalParserConfig());
    }

    /**
     * Programmatic constructor with typed config.
     */
    public ExternalParser(ExternalParserConfig config) {
        this.config = config;
        this.supportedTypes = new HashSet<>();
        for (String s : config.getSupportedTypes()) {
            this.supportedTypes.add(MediaType.parse(s));
        }
        this.commandLine = new ArrayList<>(config.getCommandLine());
        this.stdoutHandler = config.getStdoutHandler();
        this.stderrHandler = config.getStderrHandler();
        this.outputFileHandler = config.getOutputFileHandler();

        // Set up lazy check
        if (config.getCheckCommandLine() != null && !config.getCheckCommandLine().isEmpty()) {
            this.checkCmd = config.getCheckCommandLine().toArray(new String[0]);
            if (config.getCheckErrorCodes() != null &&
                    !config.getCheckErrorCodes().isEmpty()) {
                this.checkErrorCodes = config.getCheckErrorCodes().stream()
                        .mapToInt(Integer::intValue).toArray();
            } else {
                this.checkErrorCodes = new int[]{127};
            }
            this.checkResult = null; // will be lazily evaluated
        } else {
            this.checkCmd = null;
            this.checkErrorCodes = null;
            this.checkResult = Boolean.TRUE; // no check configured, always available
        }
    }

    /**
     * JSON config constructor - used for deserialization.
     */
    public ExternalParser(JsonConfig jsonConfig) {
        this(ConfigDeserializer.buildConfig(jsonConfig, ExternalParserConfig.class));
    }

    @Override
    public Set<MediaType> getSupportedTypes(ParseContext context) {
        if (checkResult == null) {
            synchronized (this) {
                if (checkResult == null) {
                    checkResult = ProcessUtils.checkCommand(checkCmd, checkErrorCodes);
                }
            }
        }
        return checkResult ? supportedTypes : Collections.emptySet();
    }

    @Override
    public void parse(TikaInputStream tis, ContentHandler handler, Metadata metadata,
                      ParseContext context) throws IOException, SAXException, TikaException {
        Path outFile = null;
        try (TemporaryResources tmp = new TemporaryResources()) {
            Path p = tis.getPath();
            List<String> thisCommandLine = new ArrayList<>();
            Matcher inputMatcher = INPUT_TOKEN_MATCHER.matcher("");
            Matcher outputMatcher = OUTPUT_TOKEN_MATCHER.matcher("");
            boolean hasOutputFile = false;
            for (String c : commandLine) {
                if (inputMatcher.reset(c).find()) {
                    String updated = c.replace(INPUT_FILE_TOKEN,
                            ProcessUtils.escapeCommandLine(
                                    p.toAbsolutePath().toString()));
                    thisCommandLine.add(updated);
                } else if (outputMatcher.reset(c).find()) {
                    outFile = Files.createTempFile("tika-external-", "");
                    String updated = c.replace(OUTPUT_FILE_TOKEN,
                            ProcessUtils.escapeCommandLine(
                                    outFile.toAbsolutePath().toString()));
                    thisCommandLine.add(updated);
                    hasOutputFile = true;
                } else {
                    thisCommandLine.add(c);
                }
            }

            // Always capture both stdout and stderr in memory
            long localTimeoutMillis = TimeoutLimits.getProcessTimeoutMillis(
                    context, config.getTimeoutMs());
            FileProcessResult result = ProcessUtils.execute(
                    new ProcessBuilder(thisCommandLine),
                    localTimeoutMillis, config.getMaxStdOut(), config.getMaxStdErr());

            // Set process metadata
            metadata.set(ExternalProcess.IS_TIMEOUT, result.isTimeout());
            metadata.set(ExternalProcess.EXIT_VALUE, result.getExitValue());
            TikaProgressTracker.update(context);
            metadata.set(ExternalProcess.STD_OUT_LENGTH, result.getStdoutLength());
            metadata.set(ExternalProcess.STD_OUT_IS_TRUNCATED,
                    result.isStdoutTruncated());
            metadata.set(ExternalProcess.STD_ERR_LENGTH, result.getStderrLength());
            metadata.set(ExternalProcess.STD_ERR_IS_TRUNCATED,
                    result.isStderrTruncated());

            if (config.isReturnStdout()) {
                metadata.set(ExternalProcess.STD_OUT, result.getStdout());
            }
            if (config.isReturnStderr()) {
                metadata.set(ExternalProcess.STD_ERR, result.getStderr());
            }

            // Determine content source
            String effectiveContentSource = config.getContentSource();
            if (effectiveContentSource == null) {
                effectiveContentSource = hasOutputFile ? "outputFile" : "stdout";
            }

            XHTMLContentHandler xhtml =
                    new XHTMLContentHandler(handler, metadata, context);
            xhtml.startDocument();

            // Process each stream through its handler
            handleStream(result.getStdout(), stdoutHandler,
                    "stdout".equals(effectiveContentSource),
                    xhtml, metadata, context);

            handleStream(result.getStderr(), stderrHandler,
                    "stderr".equals(effectiveContentSource),
                    xhtml, metadata, context);

            if (hasOutputFile && outFile != null) {
                handleOutputFile(outFile, outputFileHandler,
                        "outputFile".equals(effectiveContentSource),
                        xhtml, metadata, context);
            }

            xhtml.endDocument();
        } finally {
            if (outFile != null) {
                Files.delete(outFile);
            }
        }
    }

    private void handleStream(String content, Parser handler, boolean isContentSource,
                              XHTMLContentHandler xhtml, Metadata metadata,
                              ParseContext context)
            throws IOException, SAXException, TikaException {
        if (content == null || content.isEmpty()) {
            return;
        }
        if (handler != null) {
            ContentHandler target = isContentSource ?
                    new BodyContentHandler(xhtml) : DISCARD_HANDLER;
            try (TikaInputStream tis = TikaInputStream.get(
                    content.getBytes(StandardCharsets.UTF_8))) {
                handler.parse(tis, target, metadata, context);
            }
        } else if (isContentSource) {
            // No handler — write raw content as XHTML text
            String[] lines = content.split("\n", -1);
            for (int i = 0; i < lines.length; i++) {
                xhtml.characters(lines[i]);
                if (i < lines.length - 1) {
                    xhtml.newline();
                }
            }
        }
    }

    private void handleOutputFile(Path outFile, Parser handler,
                                  boolean isContentSource,
                                  XHTMLContentHandler xhtml, Metadata metadata,
                                  ParseContext context)
            throws IOException, SAXException, TikaException {
        if (handler != null) {
            ContentHandler target = isContentSource ?
                    new BodyContentHandler(xhtml) : DISCARD_HANDLER;
            try (TikaInputStream tis = TikaInputStream.get(outFile)) {
                handler.parse(tis, target, metadata, context);
            }
        } else if (isContentSource) {
            // No handler — write raw file content as XHTML text
            try (BufferedReader reader = Files.newBufferedReader(outFile)) {
                String line = reader.readLine();
                while (line != null) {
                    xhtml.characters(line);
                    xhtml.newline();
                    line = reader.readLine();
                }
            }
        }
    }

    /**
     * Returns the configuration for this parser.
     */
    public ExternalParserConfig getConfig() {
        return config;
    }
}
