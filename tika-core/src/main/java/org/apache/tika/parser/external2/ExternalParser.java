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
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import org.apache.tika.config.Field;
import org.apache.tika.config.Initializable;
import org.apache.tika.config.InitializableProblemHandler;
import org.apache.tika.config.Param;
import org.apache.tika.config.TikaTaskTimeout;
import org.apache.tika.exception.TikaConfigException;
import org.apache.tika.exception.TikaException;
import org.apache.tika.io.TemporaryResources;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.ExternalProcess;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.AbstractParser;
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
public class ExternalParser extends AbstractParser implements Initializable {

    public static final long DEFAULT_TIMEOUT_MS = 60000;

    public static final String INPUT_FILE_TOKEN = "${INPUT_FILE}";

    public static final String OUTPUT_FILE_TOKEN = "${OUTPUT_FILE}";

    private static Pattern INPUT_TOKEN_MATCHER = Pattern.compile("\\$\\{INPUT_FILE}");
    private static Pattern OUTPUT_TOKEN_MATCHER = Pattern.compile("\\$\\{OUTPUT_FILE}");

    private static final Logger LOG = LoggerFactory.getLogger(ExternalParser.class);

    private Set<MediaType> supportedTypes = new HashSet<>();

    private List<String> commandLine = new ArrayList<>();

    private Parser outputParser = EmptyParser.INSTANCE;

    private boolean returnStdout = false;

    private boolean returnStderr = true;

    private long timeoutMs = DEFAULT_TIMEOUT_MS;

    private int maxStdErr = 10000;

    private int maxStdOut = 10000;

    @Override
    public Set<MediaType> getSupportedTypes(ParseContext context) {
        return supportedTypes;
    }

    @Override
    public void parse(InputStream stream, ContentHandler handler, Metadata metadata,
                      ParseContext context) throws IOException, SAXException, TikaException {
        //this may remain null, depending on whether the external parser writes to a file
        Path outFile = null;
        try (TemporaryResources tmp = new TemporaryResources()) {
            TikaInputStream tis = TikaInputStream.get(stream, tmp, metadata);
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
            long localTimeoutMillis = TikaTaskTimeout.getTimeoutMillis(context, timeoutMs);
            if (outputFileInCommandline) {
                result = ProcessUtils.execute(new ProcessBuilder(thisCommandLine),
                        localTimeoutMillis, maxStdOut, maxStdErr);
            } else {
                outFile = Files.createTempFile("tika-external2-", "");
                result = ProcessUtils.execute(new ProcessBuilder(thisCommandLine),
                        localTimeoutMillis, outFile, maxStdErr);
            }
            metadata.set(ExternalProcess.IS_TIMEOUT, result.isTimeout());
            metadata.set(ExternalProcess.EXIT_VALUE, result.getExitValue());
            metadata.set(ExternalProcess.STD_OUT_LENGTH, result.getStdoutLength());
            metadata.set(ExternalProcess.STD_OUT_IS_TRUNCATED,
                    result.isStdoutTruncated());
            metadata.set(ExternalProcess.STD_ERR_LENGTH, result.getStderrLength());
            metadata.set(ExternalProcess.STD_ERR_IS_TRUNCATED,
                    result.isStderrTruncated());

            if (returnStdout) {
                metadata.set(ExternalProcess.STD_OUT, result.getStdout());
            }
            if (returnStderr) {
                metadata.set(ExternalProcess.STD_ERR, result.getStderr());
            }
            XHTMLContentHandler xhtml = new XHTMLContentHandler(handler, metadata);
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
                try (InputStream is = TikaInputStream.get(outFile)) {
                    outputParser.parse(is, new BodyContentHandler(xhtml), metadata, parseContext);
                }
            } else {
                try (InputStream is = TikaInputStream.get(
                        result.getStdout().getBytes(StandardCharsets.UTF_8))) {
                    outputParser.parse(is, new BodyContentHandler(xhtml), metadata, parseContext);
                }
            }
        }

    }

    /**
     * This is set during initialization from a tika-config.
     * Any calls after initialization will result in a {@link IllegalStateException}.
     *
     * @param supportedTypes
     */
    @Field
    public void setSupportedTypes(List<String> supportedTypes) {
        if (this.supportedTypes.size() > 0) {
            throw new IllegalStateException("can't set supportedTypes after initialization");
        }
        for (String s : supportedTypes) {
            this.supportedTypes.add(MediaType.parse(s));
        }
    }

    @Field
    public void setTimeoutMs(long timeoutMs) {
        this.timeoutMs = timeoutMs;
    }

    @Field
    public void setMaxStdErr(int maxStdErr) {
        this.maxStdErr = maxStdErr;
    }

    @Field
    public void setMaxStdOut(int maxStdOut) {
        this.maxStdOut = maxStdOut;
    }

    /**
     * Use this to specify the full commandLine.  The commandline must
     * include at least {@link ExternalParser#INPUT_FILE_TOKEN}.
     * If the external process writes to an output file, specify
     * {@link ExternalParser#OUTPUT_FILE_TOKEN}.
     *
     * @param commandLine
     */
    @Field
    public void setCommandLine(List<String> commandLine) {
        this.commandLine = commandLine;
    }


    /**
     * If set to true, this will return the stdout in the metadata
     * via {@link org.apache.tika.metadata.ExternalProcess#STD_OUT}.
     * Default is <code>false</code> because this should normally
     * be handled by the outputParser
     *
     * @param returnStdout
     */
    @Field
    public void setReturnStdout(boolean returnStdout) {
        this.returnStdout = returnStdout;
    }

    /**
     * If set to true, this will return the stderr in the metadata
     * via {@link org.apache.tika.metadata.ExternalProcess#STD_ERR}.
     * Default is <code>true</code>
     * @param returnStderr
     */
    @Field
    public void setReturnStderr(boolean returnStderr) {
        this.returnStderr = returnStderr;
    }

    /**
     * This parser is called on the output of the process.
     * If the process writes to an output file, specified by
     * {@link ExternalParser#OUTPUT_FILE_TOKEN}, this parser will parse that file,
     * otherwise it will parse the UTF-8 encoded bytes from the process' STD_OUT.
     * @param parser
     */
    @Field
    public void setOutputParser(Parser parser) {
        this.outputParser = parser;
    }

    public Parser getOutputParser() {
        return outputParser;
    }

    @Override
    public void initialize(Map<String, Param> params) throws TikaConfigException {
        //no-op
    }

    @Override
    public void checkInitialization(InitializableProblemHandler problemHandler)
            throws TikaConfigException {
        if (supportedTypes.size() == 0) {
            throw new TikaConfigException("supportedTypes size must be > 0");
        }
        if (commandLine.isEmpty()) {
            throw new TikaConfigException("commandLine is empty?!");
        }

        if (outputParser == EmptyParser.INSTANCE) {
            LOG.debug("no parser selected for the output; contents will be " +
                    "written to the content handler");
        }
    }

}
