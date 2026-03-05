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
package org.apache.tika.parser.strings;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.commons.io.IOUtils;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import org.apache.tika.config.ConfigDeserializer;
import org.apache.tika.config.Initializable;
import org.apache.tika.config.JsonConfig;
import org.apache.tika.config.TikaComponent;
import org.apache.tika.config.TikaProgressTracker;
import org.apache.tika.config.TimeoutLimits;
import org.apache.tika.detect.FileCommandDetector;
import org.apache.tika.exception.TikaConfigException;
import org.apache.tika.exception.TikaException;
import org.apache.tika.io.TemporaryResources;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.parser.external.ExternalParser;
import org.apache.tika.sax.XHTMLContentHandler;
import org.apache.tika.utils.SystemUtils;

/**
 * Parser that uses the "strings" (or strings-alternative) command to find the
 * printable strings in a object, or other binary, file
 * (application/octet-tis). Useful as "best-effort" parser for files detected
 * as application/octet-tis.
 *
 * @author gtotaro
 */
@TikaComponent(spi = false)
public class StringsParser implements Parser, Initializable {
    /**
     * Serial version UID
     */
    private static final long serialVersionUID = 802566634661575025L;

    private static final Set<MediaType> SUPPORTED_TYPES =
            Collections.singleton(MediaType.OCTET_STREAM);

    private StringsConfig defaultConfig = new StringsConfig();

    private FileCommandDetector fileCommandDetector;

    private boolean stringsPresent = false;
    private boolean hasEncodingOption = false;//whether or not the strings app allows -e

    public StringsParser() {
    }

    public StringsParser(StringsConfig config) {
        this.defaultConfig = config;
    }

    public StringsParser(JsonConfig jsonConfig) {
        defaultConfig = ConfigDeserializer.buildConfig(jsonConfig, StringsConfig.class);
    }

    public static String getStringsProg() {
        return SystemUtils.IS_OS_WINDOWS ? "strings.exe" : "strings";
    }

    @Override
    public Set<MediaType> getSupportedTypes(ParseContext context) {
        return SUPPORTED_TYPES;
    }

    @Override
    public void parse(TikaInputStream tis, ContentHandler handler, Metadata metadata,
                      ParseContext context) throws IOException, SAXException, TikaException {

        if (!stringsPresent) {
            return;
        }
        StringsConfig stringsConfig = context.get(StringsConfig.class, defaultConfig);

        try (TemporaryResources tmp = new TemporaryResources()) {
            File input = tis.getFile();

            // Metadata
            metadata.set("strings:min-len", "" + stringsConfig.getMinLength());
            metadata.set("strings:encoding", stringsConfig.toString());
            metadata.set("strings:file_output", doFile(tis));

            int totalBytes = 0;

            // Content
            XHTMLContentHandler xhtml = new XHTMLContentHandler(handler, metadata, context);

            xhtml.startDocument();

            totalBytes = doStrings(input, stringsConfig, xhtml, context);

            xhtml.endDocument();

            // Metadata
            metadata.set("strings:length", "" + totalBytes);
        }
    }

    private String doFile(TikaInputStream tis) throws IOException {
        Metadata tmpMetadata = new Metadata();
        fileCommandDetector.detect(tis, tmpMetadata, new ParseContext());
        return tmpMetadata.get(Metadata.CONTENT_TYPE);
    }

    /**
     * Checks if the "strings" command is supported.
     */
    private void checkForStrings() {
        String stringsProg = defaultConfig.getStringsPath() + getStringsProg();


        String[] checkCmd = {stringsProg, "--version"};
        try {
            stringsPresent = ExternalParser.check(checkCmd);
            if (!stringsPresent) {
                return;
            }
            // Check if the -e option (encoding) is supported
            if (!SystemUtils.IS_OS_WINDOWS) {
                String[] checkOpt =
                        {stringsProg, "-e", "" + defaultConfig.getEncoding().get(),
                                "/dev/null"};
                int[] errorValues =
                        {1, 2}; // Exit status code: 1 = general error; 2 = incorrect usage.
                hasEncodingOption = ExternalParser.check(checkOpt, errorValues);
            }
        } catch (NoClassDefFoundError ncdfe) {
            // This happens under OSGi + Fork Parser - see TIKA-1507
            // As a workaround for now, just say we can't use strings
            // TODO Resolve it so we don't need this try/catch block
        }
    }

    /**
     * Runs the "strings" command on the given file.
     *
     * @param input  {@see File} object that represents the file to parse.
     * @param config {@see StringsConfig} object including the strings
     *               configuration.
     * @param xhtml  {@see XHTMLContentHandler} object.
     * @return the total number of bytes read using the strings command.
     * @throws IOException   if any I/O error occurs.
     * @throws TikaException if the parsing process has been interrupted.
     * @throws SAXException
     */
    private int doStrings(File input, StringsConfig config, XHTMLContentHandler xhtml,
                          ParseContext context)
            throws IOException, TikaException, SAXException {

        String stringsProg = defaultConfig.getStringsPath() + getStringsProg();

        // Builds the command array
        ArrayList<String> cmdList = new ArrayList<>(4);
        cmdList.add(stringsProg);
        cmdList.add("-n");
        cmdList.add("" + config.getMinLength());
        // Currently, encoding option is not supported by Windows (and other) versions
        if (hasEncodingOption) {
            cmdList.add("-e");
            cmdList.add("" + config.getEncoding().get());
        }
        cmdList.add(input.getPath());

        String[] cmd = cmdList.toArray(new String[0]);

        ProcessBuilder pb = new ProcessBuilder(cmd);
        final Process process = pb.start();

        InputStream out = process.getInputStream();
        AtomicInteger totalBytes = new AtomicInteger();
        // Reads content printed out by "strings" command
        Thread gobbler = logStream(out, xhtml, totalBytes);
        gobbler.start();
        try {
            long timeoutMillis = TimeoutLimits.getProcessTimeoutMillis(context, config.getTimeoutSeconds() * 1000L);
            boolean completed = process.waitFor(timeoutMillis, TimeUnit.MILLISECONDS);
            if (!completed) {
                throw new TimeoutException("timed out");
            }
            gobbler.join(10000);
            TikaProgressTracker.update(context);
        } catch (InterruptedException | TimeoutException e) {
            throw new TikaException("strings process failed", e);
        } finally {
            process.destroyForcibly();
        }

        return totalBytes.get();
    }

    private Thread logStream(final InputStream tis, final ContentHandler handler,
                             final AtomicInteger totalBytes) {
        return new Thread(() -> {
            Reader reader = new InputStreamReader(tis, UTF_8);
            char[] buffer = new char[1024];
            try {
                for (int n = reader.read(buffer); n != -1; n = reader.read(buffer)) {
                    handler.characters(buffer, 0, n);
                    totalBytes.addAndGet(n);
                }
            } catch (SAXException | IOException e) {
                //swallow
            } finally {
                IOUtils.closeQuietly(tis);
            }
        });
    }

    public StringsConfig getDefaultConfig() {
        return defaultConfig;
    }

    @Override
    public void initialize() throws TikaConfigException {
        checkForStrings();
        fileCommandDetector = new FileCommandDetector();
        fileCommandDetector.setFilePath(defaultConfig.getFilePath());
        fileCommandDetector.setTimeoutMs(defaultConfig.getTimeoutSeconds() * 1000);
    }
}
