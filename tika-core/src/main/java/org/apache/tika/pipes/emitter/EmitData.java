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
package org.apache.tika.pipes.emitter;

import java.io.Serializable;
import java.util.List;

import org.apache.tika.metadata.Metadata;

public class EmitData implements Serializable {
    /**
     * Serial version UID
     */
    private static final long serialVersionUID = -3861669115439125268L;

    private final EmitKey emitKey;
    private final List<Metadata> metadataList;

    public EmitData(EmitKey emitKey, List<Metadata> metadataList) {
        this.emitKey = emitKey;
        this.metadataList = metadataList;
    }

    public EmitKey getEmitKey() {
        return emitKey;
    }

    public List<Metadata> getMetadataList() {
        return metadataList;
    }

    @Override
    public String toString() {
        return "EmitData{" + "emitKey=" + emitKey + ", metadataList=" + metadataList + '}';
    }
}
