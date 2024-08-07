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
package org.apache.tika.pipes.extractor;

import java.io.Serializable;
import java.util.Objects;

public class EmbeddedDocumentBytesConfig implements Serializable {

    /**
     * Serial version UID
     */
    private static final long serialVersionUID = -3861669115439125268L;


    public static EmbeddedDocumentBytesConfig SKIP = new EmbeddedDocumentBytesConfig(false);

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
    //for our current custom serialization, this can't be final. :(
    private boolean extractEmbeddedDocumentBytes;

    private int zeroPadName = 0;

    private SUFFIX_STRATEGY suffixStrategy = SUFFIX_STRATEGY.NONE;

    private String embeddedIdPrefix = "-";

    private String emitter;

    private boolean includeOriginal = false;

    //This should be set per file. This allows a custom
    //emit key base that bypasses the algorithmic generation of the emitKey
    //from the primary json emitKey
    private String emitKeyBase = "";

    /**
     * Create an EmbeddedDocumentBytesConfig with
     * {@link EmbeddedDocumentBytesConfig#extractEmbeddedDocumentBytes}
     * set to <code>true</code>
     */
    public EmbeddedDocumentBytesConfig() {
        this.extractEmbeddedDocumentBytes = true;
    }

    public EmbeddedDocumentBytesConfig(boolean extractEmbeddedDocumentBytes) {
        this.extractEmbeddedDocumentBytes = extractEmbeddedDocumentBytes;
    }

    public static EmbeddedDocumentBytesConfig getSKIP() {
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

    @Override
    public String toString() {
        return "EmbeddedDocumentBytesConfig{" + "extractEmbeddedDocumentBytes=" + extractEmbeddedDocumentBytes + ", zeroPadName=" +
                zeroPadName + ", suffixStrategy=" +
                suffixStrategy + ", embeddedIdPrefix='" + embeddedIdPrefix + '\'' + ", emitter='" + emitter + '\'' +
                ", includeOriginal=" + includeOriginal + ", emitKeyBase='" +
                emitKeyBase + '\'' + '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        EmbeddedDocumentBytesConfig that = (EmbeddedDocumentBytesConfig) o;
        return extractEmbeddedDocumentBytes == that.extractEmbeddedDocumentBytes && zeroPadName == that.zeroPadName
                && includeOriginal == that.includeOriginal &&
                suffixStrategy == that.suffixStrategy && Objects.equals(embeddedIdPrefix, that.embeddedIdPrefix)
                && Objects.equals(emitter, that.emitter) &&
                Objects.equals(emitKeyBase, that.emitKeyBase);
    }

    @Override
    public int hashCode() {
        int result = Boolean.hashCode(extractEmbeddedDocumentBytes);
        result = 31 * result + zeroPadName;
        result = 31 * result + Objects.hashCode(suffixStrategy);
        result = 31 * result + Objects.hashCode(embeddedIdPrefix);
        result = 31 * result + Objects.hashCode(emitter);
        result = 31 * result + Boolean.hashCode(includeOriginal);
        result = 31 * result + Objects.hashCode(emitKeyBase);
        return result;
    }
}
