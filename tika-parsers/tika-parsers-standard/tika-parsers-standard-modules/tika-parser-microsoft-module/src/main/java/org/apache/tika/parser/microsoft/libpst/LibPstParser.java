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
package org.apache.tika.parser.microsoft.libpst;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import org.apache.tika.config.Field;
import org.apache.tika.config.Initializable;
import org.apache.tika.config.InitializableProblemHandler;
import org.apache.tika.config.Param;
import org.apache.tika.exception.TikaConfigException;
import org.apache.tika.exception.TikaException;
import org.apache.tika.io.TemporaryResources;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.sax.XHTMLContentHandler;
import org.apache.tika.utils.FileProcessResult;
import org.apache.tika.utils.ProcessUtils;

/**
 * This is an optional PST parser that relies on the user installing
 * the GPL-3 libpst/readpst commandline tool and configuring
 * Tika to call this library via tika-config.xml
 */
public class LibPstParser implements Parser, Initializable {

    public static final MediaType MS_OUTLOOK_PST_MIMETYPE = MediaType.application("vnd.ms-outlook-pst");

    private static final Set<MediaType> SUPPORTED = Set.of(MS_OUTLOOK_PST_MIMETYPE);

    private static final Logger LOGGER = LoggerFactory.getLogger(LibPstParser.class);

    private static final int MAX_STDOUT = 100000;
    private static final int MAX_STDERR = 10000;
    private static final String READ_PST_COMMAND = "readpst";

    private LibPstParserConfig defaultConfig = new LibPstParserConfig();

    @Override
    public Set<MediaType> getSupportedTypes(ParseContext parseContext) {
        return SUPPORTED;
    }

    @Override
    public void parse(InputStream inputStream, ContentHandler contentHandler, Metadata metadata, ParseContext parseContext) throws IOException, SAXException, TikaException {
        TikaInputStream tis = TikaInputStream.cast(inputStream);
        TemporaryResources tmp = null;
        if (tis == null) {
            tmp = new TemporaryResources();
            tis = TikaInputStream.get(inputStream, tmp, metadata);
        }
        try {
            _parse(tis.getPath(), contentHandler, metadata, parseContext);
        } finally {
            IOUtils.closeQuietly(tmp);
        }
    }

    private void _parse(Path pst, ContentHandler contentHandler, Metadata metadata, ParseContext parseContext) throws TikaException, IOException, SAXException {
        LibPstParserConfig activeConfig = parseContext.get(LibPstParserConfig.class, defaultConfig);
        Path outDir = Files.createTempDirectory("libpst-");
        Path debugFile = activeConfig.isDebug() ? Files.createTempFile("tika-libpst-debug", ".txt") : null;
        try {
            ProcessBuilder pb = getProcessBuilder(pst, activeConfig, outDir, debugFile);
            XHTMLContentHandler xhtml = new XHTMLContentHandler(contentHandler, metadata);
            FileProcessResult fileProcessResult = ProcessUtils.execute(pb, activeConfig.getTimeoutSeconds() * 1000l, MAX_STDOUT, MAX_STDERR);
            xhtml.startDocument();
            processContents(outDir, activeConfig, xhtml, metadata, parseContext);
            if (fileProcessResult.isTimeout()) {
                throw new TikaException("Timeout exception: " + fileProcessResult.getProcessTimeMillis());
            }
            if (fileProcessResult.getExitValue() != 0) {
                LOGGER.warn("libpst bad exit value {}: {}", fileProcessResult.getExitValue(), fileProcessResult.getStderr());
                throw new TikaException("Bad exit value: " + fileProcessResult.getExitValue());
            }
            xhtml.endDocument();
        } finally {
            try {
                FileUtils.deleteDirectory(outDir.toFile());
            } catch (IOException e) {
                LOGGER.warn("Couldn't delete temporary directory: " + outDir.toAbsolutePath(), e);
            }
            try {
                if (debugFile != null) {
                    Files.delete(debugFile);
                }
            } catch (IOException e) {
                LOGGER.warn("Couldn't delete debug file?!", e);
            }
        }
    }

    private void processContents(Path outDir, LibPstParserConfig config, XHTMLContentHandler xhtml, Metadata metadata, ParseContext parseContext) throws IOException {
        Files.walkFileTree(outDir, new EmailVisitor(outDir, config.isProcessEmailAsMsg(), xhtml, metadata, parseContext));
    }

    private ProcessBuilder getProcessBuilder(Path pst, LibPstParserConfig config, Path outDir, Path debugFile) {
        List commands = new ArrayList<String>();
        commands.add(READ_PST_COMMAND);
        if (config.isDebug()) {
            commands.add("-d");
            commands.add(ProcessUtils.escapeCommandLine(debugFile
                    .toAbsolutePath()
                    .toString()));
        }
        if (config.isIncludeDeleted()) {
            commands.add("-D");
        }
        if (config.isProcessEmailAsMsg()) {
            commands.add("-m");
        } else {
            //include .eml and include extensions
            commands.add("-e");
        }
        commands.add("-o");
        commands.add(ProcessUtils.escapeCommandLine(outDir
                .toAbsolutePath()
                .toString()));

        commands.add(ProcessUtils.escapeCommandLine(pst
                .toAbsolutePath()
                .toString()));
        LOGGER.debug("command arguments: " + commands);
        return new ProcessBuilder(commands);
    }

    @Override
    public void initialize(Map<String, Param> map) throws TikaConfigException {
        try {
            check();
        } catch (IOException e) {
            LOGGER.error("Couldn't get version of libpst", e);
            throw new TikaConfigException("Unable to check version of readpst. Is it installed?!", e);
        }
    }

    @Override
    public void checkInitialization(InitializableProblemHandler initializableProblemHandler) throws TikaConfigException {

    }

    //throws exception if readpst is not available
    private static void check() throws TikaConfigException, IOException {
        ProcessBuilder pb = new ProcessBuilder(READ_PST_COMMAND, "-V");
        FileProcessResult result = ProcessUtils.execute(pb, 30000, 10000, 10000);
        if (result.getExitValue() != 0) {
            throw new TikaConfigException(
                    "bad exit value for LibPstParser. It must be installed and on the path" + " if this parser is configured. Exit value: " + result.getExitValue());
        }
        if (result.isTimeout()) {
            throw new TikaConfigException("timeout trying to get version from readpst?!");
        }
    }

    public static boolean checkQuietly() {
        try {
            check();
        } catch (TikaConfigException | IOException e) {
            return false;
        }
        return true;
    }

    @Field
    public void setTimeoutSeconds(long timeoutSeconds) {
        defaultConfig.setTimeoutSeconds(timeoutSeconds);
    }

    @Field
    public void setProcessEmailAsMsg(boolean processEmailAsMsg) {
        defaultConfig.setProcessEmailAsMsg(processEmailAsMsg);
    }

    @Field
    public void setIncludeDeleted(boolean includeDeleted) {
        defaultConfig.setIncludeDeleted(includeDeleted);
    }

    @Field
    public void setMaxEmails(int maxEmails) {
        defaultConfig.setMaxEmails(maxEmails);
    }


}
