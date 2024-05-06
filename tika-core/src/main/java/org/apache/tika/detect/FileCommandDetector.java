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
package org.apache.tika.detect;

import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import org.apache.tika.config.Field;
import org.apache.tika.io.BoundedInputStream;
import org.apache.tika.io.TemporaryResources;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.ExternalProcess;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.Property;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.external.ExternalParser;
import org.apache.tika.utils.FileProcessResult;
import org.apache.tika.utils.ProcessUtils;
import org.apache.tika.utils.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This runs the linux 'file' command against a file. If this is called on a TikaInputStream, it
 * will use the underlying Path or spool the full file to disk and then run file against that.
 *
 * <p>If this is run against any other type of InputStream, it will spool up to {@link #maxBytes} to
 * disk and then run the detector.
 *
 * <p>As with all detectors, mark must be supported.
 *
 * <p>If you want to use file's mime type in the parse, e.g. to select the parser in
 * AutoDetectParser, set {@link FileCommandDetector#setUseMime(boolean)} to true. The default
 * behavior is to store the value as {@link FileCommandDetector#FILE_MIME} but rely on other
 * detectors for the "active" mime used by Tika.
 */
public class FileCommandDetector implements Detector {

    // TODO: file has some diff mimes names for some very common mimes
    // should we map file mimes to Tika mimes, e.g. text/xml -> application/xml??

    public static Property FILE_MIME = Property.externalText("file:mime");
    private static final Logger LOGGER = LoggerFactory.getLogger(FileCommandDetector.class);
    private static final long DEFAULT_TIMEOUT_MS = 6000;
    private static final String DEFAULT_FILE_COMMAND_PATH = "file";
    private static boolean HAS_WARNED = false;
    private Boolean hasFileCommand = null;
    private String fileCommandPath = DEFAULT_FILE_COMMAND_PATH;
    private int maxBytes = 1_000_000;
    private long timeoutMs = DEFAULT_TIMEOUT_MS;

    private boolean useMime = false;

    public static boolean checkHasFile() {
        return checkHasFile(DEFAULT_FILE_COMMAND_PATH);
    }

    public static boolean checkHasFile(String fileCommandPath) {
        String[] commandline = new String[] {fileCommandPath, "-v"};
        return ExternalParser.check(commandline);
    }

    /**
     * @param input document input stream, or <code>null</code>
     * @param metadata input metadata for the document
     * @return mime as identified by the file command or application/octet-stream otherwise
     * @throws IOException
     */
    @Override
    public MediaType detect(InputStream input, Metadata metadata) throws IOException {
        if (hasFileCommand == null) {
            hasFileCommand = checkHasFile(this.fileCommandPath);
        }
        if (!hasFileCommand) {
            if (!HAS_WARNED) {
                LOGGER.warn("'file' command isn't working: '" + fileCommandPath + "'");
                HAS_WARNED = true;
            }
            return MediaType.OCTET_STREAM;
        }
        TikaInputStream tis = TikaInputStream.cast(input);
        if (tis != null) {
            // spool the full file to disk, if called with a TikaInputStream
            // and there is no underlying file
            return detectOnPath(tis.getPath(), metadata);
        }

        input.mark(maxBytes);
        try (TemporaryResources tmp = new TemporaryResources()) {
            Path tmpFile = tmp.createTempFile(metadata);
            Files.copy(new BoundedInputStream(maxBytes, input), tmpFile, REPLACE_EXISTING);
            return detectOnPath(tmpFile, metadata);
        } finally {
            input.reset();
        }
    }

    private MediaType detectOnPath(Path path, Metadata metadata) throws IOException {

        String[] args =
                new String[] {
                    ProcessUtils.escapeCommandLine(fileCommandPath),
                    "-b",
                    "--mime-type",
                    ProcessUtils.escapeCommandLine(path.toAbsolutePath().toString())
                };
        ProcessBuilder builder = new ProcessBuilder(args);
        FileProcessResult result = ProcessUtils.execute(builder, timeoutMs, 10000, 10000);
        if (result.isTimeout()) {
            metadata.set(ExternalProcess.IS_TIMEOUT, true);
            return MediaType.OCTET_STREAM;
        }
        if (result.getExitValue() != 0) {
            metadata.set(ExternalProcess.EXIT_VALUE, result.getExitValue());
            return MediaType.OCTET_STREAM;
        }
        String mimeString = result.getStdout();
        if (StringUtils.isBlank(mimeString)) {
            return MediaType.OCTET_STREAM;
        }
        metadata.set(FILE_MIME, mimeString);
        if (useMime) {
            MediaType mt = MediaType.parse(mimeString);
            if (mt == null) {
                return MediaType.OCTET_STREAM;
            } else {
                return mt;
            }
        }
        return MediaType.OCTET_STREAM;
    }

    @Field
    public void setFilePath(String fileCommandPath) {
        // this opens up a potential command vulnerability.
        // Don't ever let an untrusted user set this.
        this.fileCommandPath = fileCommandPath;
        checkHasFile(this.fileCommandPath);
    }

    @Field
    public void setUseMime(boolean useMime) {
        this.useMime = useMime;
    }

    public boolean isUseMime() {
        return useMime;
    }

    /**
     * If this is not called on a TikaInputStream, this detector will spool up to this many bytes to
     * a file to be detected by the 'file' command.
     *
     * @param maxBytes
     */
    @Field
    public void setMaxBytes(int maxBytes) {
        this.maxBytes = maxBytes;
    }

    @Field
    public void setTimeoutMs(long timeoutMs) {
        this.timeoutMs = timeoutMs;
    }
}
