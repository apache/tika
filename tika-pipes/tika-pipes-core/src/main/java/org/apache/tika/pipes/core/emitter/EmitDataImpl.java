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
package org.apache.tika.pipes.core.emitter;

import java.nio.charset.StandardCharsets;
import java.util.List;

import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.pipes.api.emitter.EmitData;
import org.apache.tika.utils.StringUtils;

public class EmitDataImpl implements EmitData {

    private final String emitKey;
    private final List<Metadata> metadataList;
    private final String containerStackTrace;
    // ParseContext is not serialized - it's set by PipesClient after deserialization
    private ParseContext parseContext;

    // Raw UTF-8 content under content-bytes-config; rides the IPC as binary
    private byte[] contentBytes;

    public EmitDataImpl(String emitKey, List<Metadata> metadataList) {
        this(emitKey, metadataList, StringUtils.EMPTY);
    }

    public EmitDataImpl(String emitKey, List<Metadata> metadataList, String containerStackTrace) {
        this.emitKey = emitKey;
        this.metadataList = metadataList;
        this.containerStackTrace = (containerStackTrace == null) ? StringUtils.EMPTY :
                containerStackTrace;
    }

    public String getEmitKey() {
        return emitKey;
    }

    public List<Metadata> getMetadataList() {
        return metadataList;
    }

    public String getContainerStackTrace() {
        return containerStackTrace;
    }

    @Override
    public byte[] getContentBytes() {
        return contentBytes;
    }

    public void setContentBytes(byte[] contentBytes) {
        this.contentBytes = contentBytes;
    }

    /**
     * Inverse of the content-bytes move: puts the content back in
     * {@code TIKA_CONTENT} for consumers that only read the metadata list
     * (e.g. a regular Emitter). No-op when there are no content bytes or
     * the metadata already carries content.
     */
    public void restoreContentFromBytes() {
        if (contentBytes == null || metadataList == null || metadataList.isEmpty()) {
            return;
        }
        Metadata m = metadataList.get(0);
        if (m.get(TikaCoreProperties.TIKA_CONTENT) == null) {
            m.set(TikaCoreProperties.TIKA_CONTENT,
                    new String(contentBytes, StandardCharsets.UTF_8));
        }
        contentBytes = null;
    }

    public long getEstimatedSizeBytes() {
        long sz = estimateSizeInBytes(getEmitKey(), getMetadataList(), containerStackTrace);
        if (contentBytes != null) {
            sz += 36 + contentBytes.length;
        }
        return sz;
    }

    /**
     * Gets the ParseContext. This is not serialized - it's set by PipesClient
     * after deserialization from the original FetchEmitTuple.
     */
    @Override
    public ParseContext getParseContext() {
        return parseContext;
    }

    /**
     * Sets the ParseContext. Called by PipesClient after deserialization
     * to restore the ParseContext from the original FetchEmitTuple.
     */
    public void setParseContext(ParseContext parseContext) {
        this.parseContext = parseContext;
    }

    private static long estimateSizeInBytes(String id, List<Metadata> metadataList,
                                            String containerStackTrace) {
        // Estimates Java heap cost (UTF-16: 2 bytes/char + object overhead).
        // Used by the DYNAMIC emit strategy to decide passback vs. direct-emit; it is not
        // used to enforce the IPC payload limit (that is handled by BoundedOutputStream in
        // ServerProtocolIO, which measures actual wire bytes during serialization).
        long sz = 36 + id.length() * 2L;
        sz += 36 + containerStackTrace.length() * 2L;
        for (Metadata m : metadataList) {
            for (String n : m.names()) {
                sz += 36 + n.length() * 2L;
                for (String v : m.getValues(n)) {
                    sz += 36 + v.length() * 2L;
                }
            }
        }
        return sz;
    }

    @Override
    public String toString() {
        return "EmitData{" + "emitKey=" + emitKey + ", metadataList=" + metadataList +
                ", containerStackTrace='" + containerStackTrace + '\'' +
                ", contentBytes.length=" + (contentBytes == null ? "null" : contentBytes.length) +
                '}';
    }
}
