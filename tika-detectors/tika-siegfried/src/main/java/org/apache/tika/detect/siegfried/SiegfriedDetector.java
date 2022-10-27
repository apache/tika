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
package org.apache.tika.detect.siegfried;

import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.tika.config.Field;
import org.apache.tika.detect.Detector;
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

/**
 * Simple wrapper around Siegfried https://github.com/richardlehane/siegfried
 * The default behavior is to run detection, report the results in the
 * metadata and then return null so that other detectors will be used.
 */
public class SiegfriedDetector implements Detector {

    enum STATUS {
        SUCCESS, TIMEOUT, CRASH, JSON_PARSE_EXCEPTION
    }

    public static final String SIEGFRIED_PREFIX = "sf:";
    public static Property SIEGFRIED_STATUS = Property.externalText(SIEGFRIED_PREFIX + "status");

    public static Property SIEGFRIED_VERSION =
            Property.externalText(SIEGFRIED_PREFIX + "sf_version");

    public static Property SIEGFRIED_SIGNATURE =
            Property.externalText(SIEGFRIED_PREFIX + "signature");

    public static Property SIEGFRIED_IDENTIFIERS_NAME =
            Property.externalTextBag(SIEGFRIED_PREFIX + "identifiers_name");

    public static Property SIEGFRIED_IDENTIFIERS_DETAILS =
            Property.externalTextBag(SIEGFRIED_PREFIX + "identifiers_details");

    //TODO -- grab errors and warnings

    public static String ID = "id";
    public static String FORMAT = "format";
    public static String VERSION = "version";
    public static String MIME = "mime";
    public static String WARNING = "warning";
    public static String BASIS = "basis";

    private static final Logger LOGGER = LoggerFactory.getLogger(SiegfriedDetector.class);
    private static final long DEFAULT_TIMEOUT_MS = 6000;
    private static final String DEFAULT_SIEGFRIED_PATH = "sf";

    private static ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static boolean HAS_WARNED = false;
    private Boolean hasSiegfriedCommand = null;
    private String siegfriedPath = DEFAULT_SIEGFRIED_PATH;
    private int maxBytes = 1_000_000;
    private long timeoutMs = DEFAULT_TIMEOUT_MS;

    private boolean useMime = false;

    public static boolean checkHasSiegfried(String siegfriedCommandPath) {
        String[] commandline = new String[]{siegfriedCommandPath, "-version"};
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
        if (hasSiegfriedCommand == null) {
            hasSiegfriedCommand = checkHasSiegfried(this.siegfriedPath);
        }
        if (!hasSiegfriedCommand) {
            if (!HAS_WARNED) {
                LOGGER.warn("'siegfried' command isn't working: '" + siegfriedPath + "'");
                HAS_WARNED = true;
            }
            return MediaType.OCTET_STREAM;
        }
        TikaInputStream tis = TikaInputStream.cast(input);
        if (tis != null) {
            //spool the full file to disk, if called with a TikaInputStream
            //and there is no underlying file
            return detectOnPath(tis.getPath(), metadata);
        }

        input.mark(maxBytes);
        try (TemporaryResources tmp = new TemporaryResources()) {
            Path tmpFile = tmp.createTempFile();
            Files.copy(new BoundedInputStream(maxBytes, input), tmpFile, REPLACE_EXISTING);
            return detectOnPath(tmpFile, metadata);
        } finally {
            input.reset();
        }
    }

    /**
     * As default behavior, Tika runs Siegfried to add its detection
     * to the metadata, but NOT to use detection in determining parsers
     * etc.  If this is set to <code>true</code>, this detector
     * will return the first mime detected by Siegfried and that
     * mime will be used by the AutoDetectParser to select the appropriate
     * parser.
     *
     * @param useMime
     */
    @Field
    public void setUseMime(boolean useMime) {
        this.useMime = useMime;
    }

    public boolean isUseMime() {
        return useMime;
    }

    private MediaType detectOnPath(Path path, Metadata metadata) throws IOException {

        String[] args = new String[]{ProcessUtils.escapeCommandLine(siegfriedPath), "-json",
                ProcessUtils.escapeCommandLine(path.toAbsolutePath().toString())};
        ProcessBuilder builder = new ProcessBuilder(args);
        FileProcessResult result = ProcessUtils.execute(builder, timeoutMs, 1000000, 1000);
        return processResult(result, metadata, useMime);
    }

    protected static MediaType processResult(FileProcessResult result, Metadata metadata,
                                             boolean returnMime) {
        metadata.set(ExternalProcess.EXIT_VALUE, result.getExitValue());
        metadata.set(ExternalProcess.IS_TIMEOUT, result.isTimeout());

        if (result.isTimeout()) {
            metadata.set(SIEGFRIED_STATUS, STATUS.TIMEOUT.name());
            return MediaType.OCTET_STREAM;
        }
        if (result.getExitValue() != 0) {
            metadata.set(SIEGFRIED_STATUS, STATUS.CRASH.name());
            return MediaType.OCTET_STREAM;
        }
        JsonNode root;
        try {
            root = OBJECT_MAPPER.readTree(result.getStdout());
        } catch (JsonProcessingException e) {
            metadata.set(SIEGFRIED_STATUS, STATUS.JSON_PARSE_EXCEPTION.name());
            return MediaType.OCTET_STREAM;
        }

        if (root.has("siegfried")) {
            String siegfriedVersion = root.get("siegfried").asText(StringUtils.EMPTY);
            metadata.set(SIEGFRIED_VERSION, siegfriedVersion);
        }

        if (root.has("signature")) {
            String sig = root.get("signature").asText(StringUtils.EMPTY);
            metadata.set(SIEGFRIED_SIGNATURE, sig);
        }

        if (root.has("identifiers")) {
            for (JsonNode n : root.get("identifiers")) {
                if (n.has("name") && n.has("details")) {
                    String name = n.get("name").asText(StringUtils.EMPTY);
                    String details = n.get("details").asText(StringUtils.EMPTY);
                    metadata.add(SIEGFRIED_IDENTIFIERS_NAME, name);
                    metadata.add(SIEGFRIED_IDENTIFIERS_DETAILS, details);
                }
            }
        }
        MediaType mt = MediaType.OCTET_STREAM;
        if (root.has("files")) {
            for (JsonNode file : root.get("files")) {
                //TODO
///                String errors = file.get("errors").asText("");
                for (JsonNode match : file.get("matches")) {
                    String ns = match.has("ns") ? match.get("ns").asText(StringUtils.EMPTY) :
                            StringUtils.EMPTY;
                    addNotBlank(match, "basis", metadata, SIEGFRIED_PREFIX + ns + ":" + BASIS);
                    addNotBlank(match, "format", metadata, SIEGFRIED_PREFIX + ns + ":" + FORMAT);
                    addNotBlank(match, "id", metadata, SIEGFRIED_PREFIX + ns + ":" + ID);
                    addNotBlank(match, "mime", metadata, SIEGFRIED_PREFIX + ns + ":" + MIME);
                    addNotBlank(match, "version", metadata, SIEGFRIED_PREFIX + ns + ":" + VERSION);
                    addNotBlank(match, "warning", metadata, SIEGFRIED_PREFIX + ns + ":" + WARNING);

                    //take the first non-octet-stream
                    if (returnMime && mt.equals(MediaType.OCTET_STREAM)) {
                        if (match.has("mime")) {
                            String mimeString = match.get("mime").asText(StringUtils.EMPTY);
                            mt = MediaType.parse(mimeString);
                            if (mt == null) {
                                mt = MediaType.OCTET_STREAM;
                            }
                        }
                    }
                }
            }
        }
        return mt;
    }

    private static void addNotBlank(JsonNode node, String jsonKey, Metadata metadata,
                                    String metadataKey) {
        if (node.has(jsonKey)) {
            String val = node.get(jsonKey).asText(StringUtils.EMPTY);
            if (StringUtils.isBlank(val)) {
                return;
            }
            metadata.set(metadataKey, val);
        }
    }

    @Field
    public void setSiegfriedPath(String fileCommandPath) {
        //this opens up a potential command vulnerability.
        //Don't ever let an untrusted user set this.
        this.siegfriedPath = fileCommandPath;
        checkHasSiegfried(this.siegfriedPath);
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
}
