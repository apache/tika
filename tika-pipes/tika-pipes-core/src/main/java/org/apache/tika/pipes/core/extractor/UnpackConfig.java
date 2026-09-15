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
package org.apache.tika.pipes.core.extractor;

import java.io.Serializable;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.tika.annotation.TikaComponent;

@TikaComponent(name = "unpack-config")
public class UnpackConfig implements Serializable {

    /**
     * Serial version UID
     */
    private static final long serialVersionUID = -3861669115439125268L;

    private static final Logger LOG = LoggerFactory.getLogger(UnpackConfig.class);

    /**
     * Default maximum bytes to unpack per file: 10 GB.
     * Use -1 to disable the limit (not recommended).
     */
    public static final long DEFAULT_MAX_UNPACK_BYTES = 10L * 1024L * 1024L * 1024L;

    public enum SUFFIX_STRATEGY {
            NONE, EXISTING, DETECTED;

        public static SUFFIX_STRATEGY parse(String s) {
            if (s.equalsIgnoreCase("none")) {
                return NONE;
            } else if (s.equalsIgnoreCase("existing")) {
                return EXISTING;
            } else if (s.equalsIgnoreCase("detected")) {
                return DETECTED;
            }
            throw new IllegalArgumentException("can't parse " + s);
        }
    }

    public enum KEY_BASE_STRATEGY {
        /**
         * Default pattern: {containerKey}-embed/{id}{suffix}
         */
        DEFAULT,
        /**
         * Custom pattern using emitKeyBase
         */
        CUSTOM;

        public static KEY_BASE_STRATEGY parse(String s) {
            if (s.equalsIgnoreCase(DEFAULT.name())) {
                return DEFAULT;
            } else if (s.equalsIgnoreCase(CUSTOM.name())) {
                return CUSTOM;
            }
            throw new IllegalArgumentException("can't parse " + s);
        }
    }

    /**
     * Output format for UNPACK mode.
     */
    public enum OUTPUT_FORMAT {
        /**
         * Regular output - embedded files emitted individually or as simple zip
         */
        REGULAR,
        /**
         * Frictionless Data Package format with datapackage.json manifest,
         * SHA256 hashes, mimetypes, and files in unpacked/ subdirectory
         */
        FRICTIONLESS;

        public static OUTPUT_FORMAT parse(String s) {
            if (s.equalsIgnoreCase(REGULAR.name())) {
                return REGULAR;
            } else if (s.equalsIgnoreCase(FRICTIONLESS.name())) {
                return FRICTIONLESS;
            }
            throw new IllegalArgumentException("can't parse OUTPUT_FORMAT: " + s);
        }
    }

    /**
     * Output mode for how embedded files are delivered.
     */
    public enum OUTPUT_MODE {
        /**
         * Package all files into a single zip archive
         */
        ZIPPED,
        /**
         * Emit files directly to the configured emitter as separate items
         */
        DIRECTORY;

        public static OUTPUT_MODE parse(String s) {
            if (s.equalsIgnoreCase(ZIPPED.name())) {
                return ZIPPED;
            } else if (s.equalsIgnoreCase(DIRECTORY.name())) {
                return DIRECTORY;
            }
            throw new IllegalArgumentException("can't parse OUTPUT_MODE: " + s);
        }
    }
    private int zeroPadName = 0;

    private SUFFIX_STRATEGY suffixStrategy = SUFFIX_STRATEGY.NONE;

    private String embeddedIdPrefix = "-";

    private String emitter;

    private boolean includeOriginal = false;

    private KEY_BASE_STRATEGY keyBaseStrategy = KEY_BASE_STRATEGY.DEFAULT;
    //This should be set per file. This allows a custom
    //emit key base that bypasses the algorithmic generation of the emitKey
    //from the primary json emitKey when keyBase Strategy is CUSTOM
    private String emitKeyBase = "";

    // Zipping options
    private boolean zipEmbeddedFiles = false;

    private Boolean includeMetadata = null;  // null: the format decides, see writesMetadata()

    // Maximum bytes to unpack per file (default 10GB, -1 to disable limit)
    private long maxUnpackBytes = DEFAULT_MAX_UNPACK_BYTES;

    // Frictionless Data Package options
    private OUTPUT_FORMAT outputFormat = OUTPUT_FORMAT.REGULAR;
    private OUTPUT_MODE outputMode = OUTPUT_MODE.ZIPPED;

    /**
     * Create an UnpackConfig with default settings.
     */
    public UnpackConfig() {
    }

    /** The worker mutates its instance; hand out copies. */
    public UnpackConfig copy() {
        UnpackConfig c = new UnpackConfig();
        c.zeroPadName = zeroPadName;
        c.suffixStrategy = suffixStrategy;
        c.embeddedIdPrefix = embeddedIdPrefix;
        c.emitter = emitter;
        c.includeOriginal = includeOriginal;
        c.keyBaseStrategy = keyBaseStrategy;
        c.emitKeyBase = emitKeyBase;
        c.zipEmbeddedFiles = zipEmbeddedFiles;
        c.includeMetadata = includeMetadata;
        c.maxUnpackBytes = maxUnpackBytes;
        c.outputFormat = outputFormat;
        c.outputMode = outputMode;
        return c;
    }

    public int getZeroPadName() {
        return zeroPadName;
    }

    public SUFFIX_STRATEGY getSuffixStrategy() {
        return suffixStrategy;
    }

    public KEY_BASE_STRATEGY getKeyBaseStrategy() {
        return keyBaseStrategy;
    }

    public String getEmbeddedIdPrefix() {
        return embeddedIdPrefix;
    }

    public String getEmitter() {
        return emitter;
    }

    public boolean isIncludeOriginal() {
        return includeOriginal;
    }

    public void setZeroPadName(int zeroPadName) {
        this.zeroPadName = zeroPadName;
    }

    public void setSuffixStrategy(SUFFIX_STRATEGY suffixStrategy) {
        this.suffixStrategy = suffixStrategy;
    }

    public void setSuffixStrategy(String suffixStrategy) {
        setSuffixStrategy(SUFFIX_STRATEGY.valueOf(suffixStrategy));
    }

    public void setKeyBaseStrategy(KEY_BASE_STRATEGY keyBaseStrategy) {
        this.keyBaseStrategy = keyBaseStrategy;
    }

    public void setKeyBaseStrategy(String keyBaseStrategy) {
        setKeyBaseStrategy(KEY_BASE_STRATEGY.valueOf(keyBaseStrategy));
    }

    public void setEmbeddedIdPrefix(String embeddedIdPrefix) {
        this.embeddedIdPrefix = embeddedIdPrefix;
    }

    public void setEmitter(String emitter) {
        this.emitter = emitter;
    }

    public void setIncludeOriginal(boolean includeOriginal) {
        this.includeOriginal = includeOriginal;
    }

    public void setEmitKeyBase(String emitKeyBase) {
        this.emitKeyBase = emitKeyBase;
    }

    public String getEmitKeyBase() {
        return emitKeyBase;
    }

    /**
     * Whether to zip all embedded files into a single archive before emitting.
     * When true, embedded files are collected during parsing and then zipped
     * and emitted as a single archive after parsing completes.
     */
    public boolean isZipEmbeddedFiles() {
        return zipEmbeddedFiles;
    }

    public void setZipEmbeddedFiles(boolean zipEmbeddedFiles) {
        this.zipEmbeddedFiles = zipEmbeddedFiles;
    }

    /** Null when the format decides; see {@link #writesMetadata()}. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public Boolean getIncludeMetadata() {
        return includeMetadata;
    }

    public void setIncludeMetadata(Boolean includeMetadata) {
        this.includeMetadata = includeMetadata;
    }

    /**
     * Metadata for every extracted file: {@code metadata.json} under FRICTIONLESS, a sidecar
     * per file under REGULAR zip output, nothing under REGULAR loose output. Unset means on
     * for FRICTIONLESS, off for REGULAR. Not a bean getter: the class is introspected.
     */
    public boolean writesMetadata() {
        if (includeMetadata != null) {
            return includeMetadata;
        }
        return outputFormat == OUTPUT_FORMAT.FRICTIONLESS;
    }

    /** @deprecated since 4.1, removal planned for 5.0; see {@link #writesMetadata()} */
    @Deprecated
    @JsonIgnore
    public boolean isIncludeMetadataInZip() {
        return writesMetadata();
    }

    /** @deprecated since 4.1, removal planned for 5.0; use {@link #setIncludeMetadata(Boolean)} */
    @Deprecated
    @JsonProperty("includeMetadataInZip")
    public void setIncludeMetadataInZip(boolean includeMetadataInZip) {
        LOG.warn("unpack-config.includeMetadataInZip is deprecated (removal in 5.0); use includeMetadata");
        setIncludeMetadata(includeMetadataInZip);
    }

    /**
     * Maximum total bytes to unpack per file. Default is 10GB.
     * Set to -1 to disable the limit (not recommended). 0 is not "unlimited" -- it means
     * zero bytes, so the very first embedded file's extraction is immediately capped.
     *
     * @return max bytes to unpack, or -1 if no limit
     */
    public long getMaxUnpackBytes() {
        return maxUnpackBytes;
    }

    public void setMaxUnpackBytes(long maxUnpackBytes) {
        this.maxUnpackBytes = maxUnpackBytes;
    }

    /**
     * Maximum total bytes to unpack per file, with -1 (no limit) normalized to
     * {@link Long#MAX_VALUE} so callers can bound reads without a special case.
     * <p>
     * Deliberately not named {@code getMaxUnpackBytesOrUnlimited} -- this class is JSON
     * (de)serialized via bean introspection, and a "get"-prefixed method here would be
     * picked up as a phantom property with no matching setter.
     *
     * @return max bytes to unpack, or {@link Long#MAX_VALUE} if unlimited
     */
    public long maxUnpackBytesOrUnlimited() {
        return maxUnpackBytes >= 0 ? maxUnpackBytes : Long.MAX_VALUE;
    }

    /**
     * Get the output format for UNPACK mode.
     * REGULAR is the default (existing behavior).
     * FRICTIONLESS creates a Frictionless Data Package with datapackage.json manifest.
     */
    public OUTPUT_FORMAT getOutputFormat() {
        return outputFormat;
    }

    public void setOutputFormat(OUTPUT_FORMAT outputFormat) {
        this.outputFormat = outputFormat;
    }

    public void setOutputFormat(String outputFormat) {
        setOutputFormat(OUTPUT_FORMAT.valueOf(outputFormat));
    }

    /**
     * Get the output mode for how embedded files are delivered.
     * ZIPPED packages all files into a single zip archive.
     * DIRECTORY emits files directly to the configured emitter.
     */
    public OUTPUT_MODE getOutputMode() {
        return outputMode;
    }

    public void setOutputMode(OUTPUT_MODE outputMode) {
        this.outputMode = outputMode;
    }

    public void setOutputMode(String outputMode) {
        setOutputMode(OUTPUT_MODE.valueOf(outputMode));
    }

    /** @deprecated since 4.1, removal planned for 5.0; see {@link #writesMetadata()} */
    @Deprecated
    @JsonIgnore
    public boolean isIncludeFullMetadata() {
        return writesMetadata();
    }

    /** @deprecated since 4.1, removal planned for 5.0; use {@link #setIncludeMetadata(Boolean)} */
    @Deprecated
    @JsonProperty("includeFullMetadata")
    public void setIncludeFullMetadata(boolean includeFullMetadata) {
        LOG.warn("unpack-config.includeFullMetadata is deprecated (removal in 5.0); use includeMetadata");
        setIncludeMetadata(includeFullMetadata);
    }

    @Override
    public String toString() {
        return "UnpackConfig{" + "zeroPadName=" + zeroPadName + ", suffixStrategy=" +
                suffixStrategy + ", embeddedIdPrefix='" + embeddedIdPrefix + '\'' +
                ", emitter='" + emitter + '\'' + ", includeOriginal=" + includeOriginal +
                ", keyBaseStrategy=" + keyBaseStrategy + ", emitKeyBase='" + emitKeyBase + '\'' +
                ", zipEmbeddedFiles=" + zipEmbeddedFiles + ", includeMetadata=" + includeMetadata +
                ", maxUnpackBytes=" + maxUnpackBytes + ", outputFormat=" + outputFormat +
                ", outputMode=" + outputMode + '}';
    }

    @Override
    public final boolean equals(Object o) {
        if (!(o instanceof UnpackConfig config)) {
            return false;
        }

        return zeroPadName == config.zeroPadName && includeOriginal == config.includeOriginal &&
                suffixStrategy == config.suffixStrategy &&
                Objects.equals(embeddedIdPrefix, config.embeddedIdPrefix) &&
                Objects.equals(emitter, config.emitter) &&
                keyBaseStrategy == config.keyBaseStrategy &&
                Objects.equals(emitKeyBase, config.emitKeyBase) &&
                zipEmbeddedFiles == config.zipEmbeddedFiles &&
                Objects.equals(includeMetadata, config.includeMetadata) &&
                maxUnpackBytes == config.maxUnpackBytes &&
                outputFormat == config.outputFormat &&
                outputMode == config.outputMode;
    }

    @Override
    public int hashCode() {
        int result = zeroPadName;
        result = 31 * result + Objects.hashCode(suffixStrategy);
        result = 31 * result + Objects.hashCode(embeddedIdPrefix);
        result = 31 * result + Objects.hashCode(emitter);
        result = 31 * result + Boolean.hashCode(includeOriginal);
        result = 31 * result + Objects.hashCode(keyBaseStrategy);
        result = 31 * result + Objects.hashCode(emitKeyBase);
        result = 31 * result + Boolean.hashCode(zipEmbeddedFiles);
        result = 31 * result + Objects.hashCode(includeMetadata);
        result = 31 * result + Long.hashCode(maxUnpackBytes);
        result = 31 * result + Objects.hashCode(outputFormat);
        result = 31 * result + Objects.hashCode(outputMode);
        return result;
    }
}
