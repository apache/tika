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
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

import org.apache.tika.config.TikaComponent;
import org.apache.tika.extractor.BasicEmbeddedBytesSelector;
import org.apache.tika.extractor.EmbeddedBytesSelector;

@TikaComponent(name = "unpack-config")
public class UnpackConfig implements Serializable {

    /**
     * Serial version UID
     */
    private static final long serialVersionUID = -3861669115439125268L;


    public static UnpackConfig SKIP = new UnpackConfig(false);

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
    //for our current custom serialization, this can't be final. :(
    private boolean extractEmbeddedDocumentBytes;

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

    // Filter parameters for embedded bytes selection
    private Set<String> includeMimeTypes = new HashSet<>();
    private Set<String> excludeMimeTypes = new HashSet<>();
    private Set<String> includeEmbeddedResourceTypes = new HashSet<>();
    private Set<String> excludeEmbeddedResourceTypes = new HashSet<>();

    /**
     * Create an UnpackConfig with
     * {@link UnpackConfig#extractEmbeddedDocumentBytes}
     * set to <code>true</code>
     */
    public UnpackConfig() {
        this.extractEmbeddedDocumentBytes = true;
    }

    public UnpackConfig(boolean extractEmbeddedDocumentBytes) {
        this.extractEmbeddedDocumentBytes = extractEmbeddedDocumentBytes;
    }

    public static UnpackConfig getSKIP() {
        return SKIP;
    }

    public boolean isExtractEmbeddedDocumentBytes() {
        return extractEmbeddedDocumentBytes;
    }

    public void setExtractEmbeddedDocumentBytes(boolean extractEmbeddedDocumentBytes) {
        this.extractEmbeddedDocumentBytes = extractEmbeddedDocumentBytes;
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

    public Set<String> getIncludeMimeTypes() {
        return includeMimeTypes;
    }

    public void setIncludeMimeTypes(Set<String> includeMimeTypes) {
        this.includeMimeTypes = new HashSet<>(includeMimeTypes);
    }

    public Set<String> getExcludeMimeTypes() {
        return excludeMimeTypes;
    }

    public void setExcludeMimeTypes(Set<String> excludeMimeTypes) {
        this.excludeMimeTypes = new HashSet<>(excludeMimeTypes);
    }

    public Set<String> getIncludeEmbeddedResourceTypes() {
        return includeEmbeddedResourceTypes;
    }

    public void setIncludeEmbeddedResourceTypes(Set<String> includeEmbeddedResourceTypes) {
        this.includeEmbeddedResourceTypes = new HashSet<>(includeEmbeddedResourceTypes);
    }

    public Set<String> getExcludeEmbeddedResourceTypes() {
        return excludeEmbeddedResourceTypes;
    }

    public void setExcludeEmbeddedResourceTypes(Set<String> excludeEmbeddedResourceTypes) {
        this.excludeEmbeddedResourceTypes = new HashSet<>(excludeEmbeddedResourceTypes);
    }

    /**
     * Creates an EmbeddedBytesSelector based on the configured filter parameters.
     *
     * @return an EmbeddedBytesSelector that will filter embedded documents based on
     *         configured mime types and resource types
     */
    public EmbeddedBytesSelector createEmbeddedBytesSelector() {
        if (includeMimeTypes.isEmpty() && excludeMimeTypes.isEmpty()
                && includeEmbeddedResourceTypes.isEmpty() && excludeEmbeddedResourceTypes.isEmpty()) {
            return EmbeddedBytesSelector.ACCEPT_ALL;
        }
        return new BasicEmbeddedBytesSelector(includeMimeTypes, excludeMimeTypes,
                includeEmbeddedResourceTypes, excludeEmbeddedResourceTypes);
    }

    @Override
    public String toString() {
        return "UnpackConfig{" + "extractEmbeddedDocumentBytes=" + extractEmbeddedDocumentBytes + ", zeroPadName=" + zeroPadName + ", suffixStrategy=" +
                suffixStrategy + ", embeddedIdPrefix='" + embeddedIdPrefix + '\'' + ", emitter='" + emitter + '\'' + ", includeOriginal=" + includeOriginal + ", keyBaseStrategy=" +
                keyBaseStrategy + ", emitKeyBase='" + emitKeyBase + '\'' +
                ", includeMimeTypes=" + includeMimeTypes + ", excludeMimeTypes=" + excludeMimeTypes +
                ", includeEmbeddedResourceTypes=" + includeEmbeddedResourceTypes + ", excludeEmbeddedResourceTypes=" + excludeEmbeddedResourceTypes + '}';
    }

    @Override
    public final boolean equals(Object o) {
        if (!(o instanceof UnpackConfig config)) {
            return false;
        }

        return extractEmbeddedDocumentBytes == config.extractEmbeddedDocumentBytes && zeroPadName == config.zeroPadName && includeOriginal == config.includeOriginal &&
                suffixStrategy == config.suffixStrategy && Objects.equals(embeddedIdPrefix, config.embeddedIdPrefix) && Objects.equals(emitter, config.emitter) &&
                keyBaseStrategy == config.keyBaseStrategy && Objects.equals(emitKeyBase, config.emitKeyBase) &&
                Objects.equals(includeMimeTypes, config.includeMimeTypes) &&
                Objects.equals(excludeMimeTypes, config.excludeMimeTypes) &&
                Objects.equals(includeEmbeddedResourceTypes, config.includeEmbeddedResourceTypes) &&
                Objects.equals(excludeEmbeddedResourceTypes, config.excludeEmbeddedResourceTypes);
    }

    @Override
    public int hashCode() {
        int result = Boolean.hashCode(extractEmbeddedDocumentBytes);
        result = 31 * result + zeroPadName;
        result = 31 * result + Objects.hashCode(suffixStrategy);
        result = 31 * result + Objects.hashCode(embeddedIdPrefix);
        result = 31 * result + Objects.hashCode(emitter);
        result = 31 * result + Boolean.hashCode(includeOriginal);
        result = 31 * result + Objects.hashCode(keyBaseStrategy);
        result = 31 * result + Objects.hashCode(emitKeyBase);
        result = 31 * result + Objects.hashCode(includeMimeTypes);
        result = 31 * result + Objects.hashCode(excludeMimeTypes);
        result = 31 * result + Objects.hashCode(includeEmbeddedResourceTypes);
        result = 31 * result + Objects.hashCode(excludeEmbeddedResourceTypes);
        return result;
    }
}
