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

import java.util.List;

import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.pipes.api.emitter.EmitData;
import org.apache.tika.utils.StringUtils;

public class EmitDataImpl implements EmitData {

    private final String emitKey;
    private final List<Metadata> metadataList;
    private final String containerStackTrace;
    // ParseContext is not serialized - it's set by PipesClient after deserialization
    private ParseContext parseContext;

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

    public long getEstimatedSizeBytes() {
        return estimateSizeInBytes(getEmitKey(), getMetadataList(), containerStackTrace);
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
        // Estimate Smile wire bytes: 1 byte per char (accurate for ASCII metadata keys/values
        // such as file paths, checksums, MIME types; up to 3x underestimate for CJK content).
        // Using the Java-heap cost (length*2 for UTF-16) overstates the wire size by 2x for
        // ASCII-heavy content (e.g. compressed-archive metadata) and biases the DYNAMIC emit
        // strategy toward unnecessary direct-emit.  The server-side writeFinished() post-check
        // catches the CJK case where actual Smile bytes exceed the configured limit.
        long sz = 16 + id.length();
        sz += 16 + containerStackTrace.length();
        for (Metadata m : metadataList) {
            for (String n : m.names()) {
                sz += 16 + n.length();
                for (String v : m.getValues(n)) {
                    sz += 16 + v.length();
                }
            }
        }
        return sz;
    }

    @Override
    public String toString() {
        return "EmitData{" + "emitKey=" + emitKey + ", metadataList=" + metadataList +
                ", containerStackTrace='" + containerStackTrace + '\'' + '}';
    }
}
