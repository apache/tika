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
package org.apache.tika.detect.magika;

import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
import org.apache.tika.utils.FileProcessResult;
import org.apache.tika.utils.ProcessUtils;
import org.apache.tika.utils.StringUtils;

/**
 * Simple wrapper around Google's magika: https://github.com/google/magika
 * The tool must be installed on the host where Tika is running.
 * The default behavior is to run detection, report the results in the
 * metadata and then return null so that other detectors will be used.
 */
public class MagikaDetector implements Detector {

    enum STATUS {
        SUCCESS, TIMEOUT, CRASH, JSON_PARSE_EXCEPTION
    }

    public static final String MAGIKA_PREFIX = "magika:";

    public static Property MAGIKA_STATUS = Property.externalText(MAGIKA_PREFIX + "status");
    public static Property MAGIKA_DESCRIPTION =
            Property.externalText(MAGIKA_PREFIX + "description");
    public static Property MAGIKA_SCORE =
            Property.externalReal(MAGIKA_PREFIX + "score");
    public static Property MAGIKA_GROUP =
            Property.externalText(MAGIKA_PREFIX + "group");
    public static Property MAGIKA_LABEL =
            Property.externalText(MAGIKA_PREFIX + "label");
    public static Property MAGIKA_MIME =
            Property.externalText(MAGIKA_PREFIX + "mime_type");
    public static Property MAGIKA_IS_TEXT =
            Property.externalBoolean(MAGIKA_PREFIX + "is_text");

    public static Property MAGIKA_ERRORS =
            Property.externalTextBag(MAGIKA_PREFIX + "errors");

    public static Property MAGIKA_VERSION = Property.externalText(MAGIKA_PREFIX + "version");

    //TODO -- grab errors and warnings

    private static final Logger LOGGER = LoggerFactory.getLogger(MagikaDetector.class);
    private static final long DEFAULT_TIMEOUT_MS = 60000;
    private static final String DEFAULT_MAGIKA_PATH = "magika";

    //we set this during the initial check.
    //we assume that a new version is not installed during the lifecycle of the MagikaDetector
    private static String MAGIKA_VERSION_STRING = "";

    private static ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static boolean HAS_WARNED = false;
    private Boolean hasMagika = null;
    private String magikaPath = DEFAULT_MAGIKA_PATH;
    private int maxBytes = 1_000_000;
    private long timeoutMs = DEFAULT_TIMEOUT_MS;

    private boolean useMime = false;

    public static boolean checkHasMagika(String magikaCommandPath) {
        String[] commandline = new String[]{magikaCommandPath, "--version"};
        FileProcessResult result = null;
        try {
            result = ProcessUtils.execute(new ProcessBuilder(commandline),
                    1000, 1000, 1000);
        } catch (IOException e) {
            LOGGER.debug("problem with magika");
            return false;
        }

        if (result.getExitValue() != 0) {
            return false;
        }
        /* python
        Matcher m = Pattern
                .compile("Magika version:\\s+(.{4,50})").matcher("");

        */
        //rust
        Matcher m = Pattern
                .compile("magika ([^\\s]{4,50})").matcher("");
        for (String line : result.getStdout().split("[\r\n]+")) {
            if (m.reset(line).find()) {
                MAGIKA_VERSION_STRING = m.group(1);
                break;
            }
        }
        return true;
    }

    /**
     * @param input    document input stream, or <code>null</code>
     * @param metadata input metadata for the document
     * @return mime as identified by the file command or application/octet-stream otherwise
     * @throws IOException
     */
    @Override
    public MediaType detect(InputStream input, Metadata metadata) throws IOException {
        if (hasMagika == null) {
            hasMagika = checkHasMagika(this.magikaPath);
        }
        if (!hasMagika) {
            if (!HAS_WARNED) {
                LOGGER.warn("'magika' command isn't working: '" + magikaPath + "'");
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
     * As default behavior, Tika runs magika to add its detection
     * to the metadata, but NOT to use detection in determining parsers
     * etc.  If this is set to <code>true</code>, this detector
     * will return the first mime detected by magika and that
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

        String[] args = new String[]{
                ProcessUtils.escapeCommandLine(magikaPath),
                ProcessUtils.escapeCommandLine(path.toAbsolutePath().toString()),
                "--json"
        };
        ProcessBuilder builder = new ProcessBuilder(args);
        FileProcessResult result = ProcessUtils.execute(builder, timeoutMs, 10000000, 1000);
        return processResult(result, metadata, useMime);
    }

    protected static MediaType processResult(FileProcessResult result, Metadata metadata,
                                             boolean returnMime) {
        metadata.set(ExternalProcess.EXIT_VALUE, result.getExitValue());
        metadata.set(ExternalProcess.IS_TIMEOUT, result.isTimeout());

        if (result.isTimeout()) {
            metadata.set(MAGIKA_STATUS, STATUS.TIMEOUT.name());
            return MediaType.OCTET_STREAM;
        }
        if (result.getExitValue() != 0) {
            metadata.set(MAGIKA_STATUS, STATUS.CRASH.name());
            return MediaType.OCTET_STREAM;
        }
        JsonNode rootArray;
        try {
            rootArray = OBJECT_MAPPER.readTree(result.getStdout());
        } catch (JsonProcessingException e) {
            metadata.set(MAGIKA_STATUS, STATUS.JSON_PARSE_EXCEPTION.name());
            return MediaType.OCTET_STREAM;
        }
        if (! rootArray.isArray() || rootArray.isEmpty()) {
            //something went wrong
            return MediaType.OCTET_STREAM;
        }
        //for now just take the first value
        JsonNode root = rootArray.get(0);
        //this is the more modern version
        if (root.has("result")) {
            return processNewer(root.get("result"), metadata, returnMime);
        } else {
            return processOlder(root, metadata, returnMime);
        }
    }

    private static MediaType processOlder(JsonNode root, Metadata metadata, boolean returnMime) {
        metadata.set(MAGIKA_STATUS, "ok");
        //TODO -- should we get values in "dl" instead or in addition?
        if (! root.has("output")) {
            //do something else
            return MediaType.OCTET_STREAM;
        }
        JsonNode mOutput = root.get("output");
        if (mOutput.has("score")) {
            double score = mOutput.get("score").asDouble(-1.0);
            metadata.set(MAGIKA_SCORE, score);
        }
        addString(mOutput, "description", MAGIKA_DESCRIPTION, metadata);
        addString(mOutput, "group", MAGIKA_GROUP, metadata);
        addString(mOutput, "ct_label", MAGIKA_LABEL, metadata);
        addString(mOutput, "mime_type", MAGIKA_MIME, metadata);
        metadata.set(MAGIKA_VERSION, MAGIKA_VERSION_STRING);
        if (returnMime && ! StringUtils.isBlank(metadata.get(MAGIKA_MIME))) {
            return MediaType.parse(metadata.get(MAGIKA_MIME));
        }

        return MediaType.OCTET_STREAM;

    }

    private static MediaType processNewer(JsonNode result, Metadata metadata, boolean returnMime) {
        metadata.set(MAGIKA_STATUS, "ok");
        //TODO -- should we get values in "dl" instead or in addition?
        addString(result, "status", MAGIKA_STATUS, metadata);

        if (! result.has("value")) {
            return MediaType.OCTET_STREAM;
        }
        JsonNode mValue = result.get("value");

        if (! mValue.has("output")) {
            //do something else
            return MediaType.OCTET_STREAM;
        }

        if (mValue.has("score")) {
            double score = mValue.get("score").asDouble(-1.0);
            metadata.set(MAGIKA_SCORE, score);
        }

        JsonNode mOutput = mValue.get("output");
        if (mOutput.has("score")) {
            double score = mOutput.get("score").asDouble(-1.0);
            metadata.set(MAGIKA_SCORE, score);
        }
        addString(mOutput, "description", MAGIKA_DESCRIPTION, metadata);
        addString(mOutput, "group", MAGIKA_GROUP, metadata);
        addString(mOutput, "label", MAGIKA_LABEL, metadata);
        addString(mOutput, "mime_type", MAGIKA_MIME, metadata);
        setBoolean(mOutput, "is_text", MAGIKA_IS_TEXT, metadata);
        metadata.set(MAGIKA_VERSION, MAGIKA_VERSION_STRING);
        if (returnMime && ! StringUtils.isBlank(metadata.get(MAGIKA_MIME))) {
            return MediaType.parse(metadata.get(MAGIKA_MIME));
        }

        return MediaType.OCTET_STREAM;

    }

    private static void setBoolean(JsonNode node, String jsonKey, Property property,
                                   Metadata metadata) {
        if (! node.has(jsonKey)) {
            return;
        }
        if (! node.get(jsonKey).isBoolean()) {
            //log?
            return;
        }
        metadata.set(property, node.get(jsonKey).booleanValue());

    }

    private static void addString(JsonNode node, String jsonKey, Property property,
                                  Metadata metadata) {
        if (node.has(jsonKey)) {
            if (node.get(jsonKey).isArray()) {
                for (JsonNode child : node.get(jsonKey)) {
                    String val = child
                            .asText(StringUtils.EMPTY);
                    if (! StringUtils.isBlank(val)) {
                        metadata.add(property, val);
                    }
                }
            } else {
                String val = node
                        .get(jsonKey)
                        .asText(StringUtils.EMPTY);
                if (StringUtils.isBlank(val)) {
                    return;
                }
                metadata.set(property, val);
            }
        }
    }

    @Field
    public void setMagikaPath(String fileCommandPath) {
        //this opens up a potential command vulnerability.
        //Don't ever let an untrusted user set this.
        this.magikaPath = fileCommandPath;
        checkHasMagika(this.magikaPath);
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
