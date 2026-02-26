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
package org.apache.tika.parser.external2;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
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
import org.apache.tika.parser.EmptyParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.sax.BodyContentHandler;
import org.apache.tika.sax.XHTMLContentHandler;
import org.apache.tika.utils.FileProcessResult;
import org.apache.tika.utils.ProcessUtils;

/**
 * This is a next generation external parser that uses some of the more
 * recent additions to Tika. This is an experimental alternative to the
 * {@link org.apache.tika.parser.external.ExternalParser}.
 * Specifically, it relies more on configuration than the SPI model.
 * Further, users can specify a parser to handle the output
 * of the external process.
 */
@TikaComponent
public class ExternalParser implements Parser {

    public static final long DEFAULT_TIMEOUT_MS = 60000;

    public static final String INPUT_FILE_TOKEN = "${INPUT_FILE}";

    public static final String OUTPUT_FILE_TOKEN = "${OUTPUT_FILE}";

    private static Pattern INPUT_TOKEN_MATCHER = Pattern.compile("\\$\\{INPUT_FILE}");
    private static Pattern OUTPUT_TOKEN_MATCHER = Pattern.compile("\\$\\{OUTPUT_FILE}");

    private static final Logger LOG = LoggerFactory.getLogger(ExternalParser.class);

    private final ExternalParserConfig config;

    // Cached values derived from config
    private final Set<MediaType> supportedTypes;
    private final List<String> commandLine;
    private final Parser outputParser;

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
        this.outputParser = config.getOutputParser() != null ?
                config.getOutputParser() : EmptyParser.INSTANCE;
    }

    /**
     * JSON config constructor - used for deserialization.
     */
    public ExternalParser(JsonConfig jsonConfig) {
        this(ConfigDeserializer.buildConfig(jsonConfig, ExternalParserConfig.class));
    }

    @Override
    public Set<MediaType> getSupportedTypes(ParseContext context) {
        return supportedTypes;
    }

    @Override
    public void parse(TikaInputStream tis, ContentHandler handler, Metadata metadata,
                      ParseContext context) throws IOException, SAXException, TikaException {
        //this may remain null, depending on whether the external parser writes to a file
        Path outFile = null;
        try (TemporaryResources tmp = new TemporaryResources()) {
            Path p = tis.getPath();
            List<String> thisCommandLine = new ArrayList<>();
            Matcher inputMatcher = INPUT_TOKEN_MATCHER.matcher("");
            Matcher outputMatcher = OUTPUT_TOKEN_MATCHER.matcher("");
            boolean outputFileInCommandline = false;
            for (String c : commandLine) {
                if (inputMatcher.reset(c).find()) {
                    String updated = c.replace(INPUT_FILE_TOKEN,
                            ProcessUtils.escapeCommandLine(p.toAbsolutePath().toString()));
                    thisCommandLine.add(updated);
                } else if (outputMatcher.reset(c).find()) {
                    outFile = Files.createTempFile("tika-external2-", "");
                    String updated = c.replace(OUTPUT_FILE_TOKEN,
                            ProcessUtils.escapeCommandLine(outFile.toAbsolutePath().toString()));
                    thisCommandLine.add(updated);
                    outputFileInCommandline = true;
                } else {
                    thisCommandLine.add(c);
                }
            }
            FileProcessResult result = null;
            long localTimeoutMillis = TimeoutLimits.getProcessTimeoutMillis(context, config.getTimeoutMs());
            if (outputFileInCommandline) {
                result = ProcessUtils.execute(new ProcessBuilder(thisCommandLine),
                        localTimeoutMillis, config.getMaxStdOut(), config.getMaxStdErr());
            } else {
                outFile = Files.createTempFile("tika-external2-", "");
                result = ProcessUtils.execute(new ProcessBuilder(thisCommandLine),
                        localTimeoutMillis, outFile, config.getMaxStdErr());
            }
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
            XHTMLContentHandler xhtml = new XHTMLContentHandler(handler, metadata, context);
            xhtml.startDocument();
            handleOutput(result, outFile, xhtml, metadata, context);
            xhtml.endDocument();
        } finally {
            if (outFile != null) {
                Files.delete(outFile);
            }
        }
    }

    private void handleOutput(FileProcessResult result, Path outFile,
                              XHTMLContentHandler xhtml, Metadata metadata,
                              ParseContext parseContext) throws SAXException, TikaException,
            IOException {
        if (outputParser == EmptyParser.INSTANCE) {
            if (outFile != null) {
                try (BufferedReader reader = Files.newBufferedReader(outFile)) {
                    String line = reader.readLine();
                    while (line != null) {
                        //do we want to wrap this in <p></p> elements?
                        xhtml.characters(line);
                        xhtml.newline();
                        line = reader.readLine();
                    }
                }
            } else {
                //read this in line by line and wrap <p></p> elements?
                xhtml.characters(result.getStdout());
            }
        } else {
            if (outFile != null) {
                try (TikaInputStream tis = TikaInputStream.get(outFile)) {
                    outputParser.parse(tis, new BodyContentHandler(xhtml), metadata, parseContext);
                }
            } else {
                try (TikaInputStream tis = TikaInputStream.get(
                        result.getStdout().getBytes(StandardCharsets.UTF_8))) {
                    outputParser.parse(tis, new BodyContentHandler(xhtml), metadata, parseContext);
                }
            }
        }

    }

    /**
     * Returns the output parser used to parse the external process output.
     */
    public Parser getOutputParser() {
        return outputParser;
    }

    /**
     * Returns the configuration for this parser.
     */
    public ExternalParserConfig getConfig() {
        return config;
    }
}
