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

import org.apache.tika.config.TikaComponent;

@TikaComponent
public class UnpackConfig implements Serializable {

    /**
     * Serial version UID
     */
    private static final long serialVersionUID = -3861669115439125268L;

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
    private boolean includeMetadataInZip = false;

    /**
     * Create an UnpackConfig with default settings.
     */
    public UnpackConfig() {
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

    /**
     * Whether to include the metadata JSON for each embedded document in the zip file.
     * Only applicable when {@link #isZipEmbeddedFiles()} is true.
     */
    public boolean isIncludeMetadataInZip() {
        return includeMetadataInZip;
    }

    public void setIncludeMetadataInZip(boolean includeMetadataInZip) {
        this.includeMetadataInZip = includeMetadataInZip;
    }

    @Override
    public String toString() {
        return "UnpackConfig{" + "zeroPadName=" + zeroPadName + ", suffixStrategy=" +
                suffixStrategy + ", embeddedIdPrefix='" + embeddedIdPrefix + '\'' +
                ", emitter='" + emitter + '\'' + ", includeOriginal=" + includeOriginal +
                ", keyBaseStrategy=" + keyBaseStrategy + ", emitKeyBase='" + emitKeyBase + '\'' +
                ", zipEmbeddedFiles=" + zipEmbeddedFiles + ", includeMetadataInZip=" + includeMetadataInZip + '}';
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
                includeMetadataInZip == config.includeMetadataInZip;
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
        result = 31 * result + Boolean.hashCode(includeMetadataInZip);
        return result;
    }
}
