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

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.tika.config.Field;
import org.apache.tika.io.BoundedInputStream;
import org.apache.tika.io.TemporaryResources;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.external.ExternalParser;
import org.apache.tika.utils.ProcessUtils;

/**
 * This runs the linux 'file' command against a file.  If
 * this is called on a TikaInputStream, it will use the underlying Path
 * or spool the full file to disk and then run file against that.
 * <p>
 * If this is run against any other type of InputStream, it will spool
 * up to {@link #maxBytes} to disk and then run the detector.
 * <p>
 * As with all detectors, mark must be supported.
 */
public class FileCommandDetector implements Detector {

    //TODO: file has some diff mimes names for some very common mimes
    //should we map file mimes to Tika mimes, e.g. text/xml -> application/xml??

    private static final Logger LOGGER = LoggerFactory.getLogger(FileCommandDetector.class);
    private static final long DEFAULT_TIMEOUT_MS = 6000;
    private static final String DEFAULT_FILE_COMMAND_PATH = "file";
    private static boolean HAS_WARNED = false;
    private Boolean hasFileCommand = null;
    private String fileCommandPath = DEFAULT_FILE_COMMAND_PATH;
    private int maxBytes = 1_000_000;
    private long timeoutMs = DEFAULT_TIMEOUT_MS;

    public static boolean checkHasFile() {
        return checkHasFile(DEFAULT_FILE_COMMAND_PATH);
    }


    public static boolean checkHasFile(String fileCommandPath) {
        String[] commandline = new String[]{fileCommandPath, "-v"};
        return ExternalParser.check(commandline);
    }

    /**
     * @param input    document input stream, or <code>null</code>
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
            //spool the full file to disk, if called with a TikaInputStream
            //and there is no underlying file
            return detectOnPath(tis.getPath());
        }

        input.mark(maxBytes);
        TemporaryResources tmp = new TemporaryResources();
        try {
            Path tmpFile = tmp.createTempFile();
            Files.copy(new BoundedInputStream(maxBytes, input), tmpFile, REPLACE_EXISTING);
            return detectOnPath(tmpFile);
        } finally {
            tmp.close();
            input.reset();
        }
    }

    private MediaType detectOnPath(Path path) throws IOException {

        String[] args =
                new String[]{ProcessUtils.escapeCommandLine(fileCommandPath), "-b", "--mime-type",
                        ProcessUtils.escapeCommandLine(path.toAbsolutePath().toString())};
        ProcessBuilder builder = new ProcessBuilder(args);
        Process process = builder.start();
        StringStreamGobbler errorGobbler = new StringStreamGobbler(process.getErrorStream());
        StringStreamGobbler outGobbler = new StringStreamGobbler(process.getInputStream());
        Thread errorThread = new Thread(errorGobbler);
        Thread outThread = new Thread(outGobbler);
        errorThread.start();
        outThread.start();

        process.getErrorStream();
        process.getInputStream();

        boolean finished = false;
        try {
            finished = process.waitFor(timeoutMs, TimeUnit.MILLISECONDS);
            if (!finished) {
                process.destroyForcibly();
                throw new IOException(new TimeoutException("timed out"));
            }
            int exitValue = process.exitValue();
            if (exitValue != 0) {
                throw new IOException(new RuntimeException("bad exit value"));
            }
            errorThread.join();
            outThread.join();
        } catch (InterruptedException e) {
            //swallow
        }
        MediaType mt = MediaType.parse(outGobbler.toString().trim());
        if (mt == null) {
            return MediaType.OCTET_STREAM;
        } else {
            return mt;
        }
    }

    @Field
    public void setFilePath(String fileCommandPath) {
        //this opens up a potential command vulnerability.
        //Don't ever let an untrusted user set this.
        this.fileCommandPath = fileCommandPath;
        checkHasFile(this.fileCommandPath);
    }

    /**
     * If this is not called on a TikaInputStream, this detector
     * will spool up to this many bytes to a file to be detected
     * by the 'file' command.
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

    private static class StringStreamGobbler implements Runnable {

        //plagiarized from org.apache.oodt's StreamGobbler
        private final BufferedReader reader;
        private final StringBuilder sb = new StringBuilder();

        public StringStreamGobbler(InputStream is) {
            this.reader =
                    new BufferedReader(new InputStreamReader(new BufferedInputStream(is), UTF_8));
        }

        @Override
        public void run() {
            String line = null;
            try {
                while ((line = reader.readLine()) != null) {
                    sb.append(line);
                    sb.append("\n");
                }
            } catch (IOException e) {
                //swallow ioe
            }
        }

        public void stopGobblingAndDie() {
            IOUtils.closeQuietly(reader);
        }

        @Override
        public String toString() {
            return sb.toString();
        }

    }
}
