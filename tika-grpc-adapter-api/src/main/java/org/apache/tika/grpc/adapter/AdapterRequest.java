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
package org.apache.tika.grpc.adapter;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.protobuf.ByteString;

import org.apache.tika.metadata.Metadata;

/**
 * One document an {@link ExternalParserAdapter} is called for.
 *
 * @param content      the bytes Tika parsed
 * @param tikaMetadata a copy of what Tika's parse recorded for them; empty when it recorded
 *                     nothing
 * @param resourceName the resource name the caller sent, or empty
 * @param contentType  the content type Tika detected, which routed the document here
 * @param options      the {@code options} object of the adapter's config entry, as
 *                     {@link ExternalParserAdapter#validateOptions} accepted it at startup; an
 *                     empty object when not set
 * @param limits       what the contribution may hold
 */
public record AdapterRequest(ByteString content, Metadata tikaMetadata, String resourceName,
                             String contentType, JsonNode options, ContributionLimits limits) {

    /** A builder for this request's contribution, bounded by {@link #limits()}. */
    public AdapterContribution.Builder contribution() {
        return AdapterContribution.builder(limits);
    }
}
