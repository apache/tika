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
package org.apache.tika.grpc.mapper;

import org.apache.tika.grpc.v1.ParseResponse;

/**
 * Optional post-processor that enriches a {@link ParseResponse} after core Tika metadata mapping.
 * <p>
 * Intended for extensions such as document outlines (PDF bookmarks, HTML heading hierarchy,
 * Markdown headings, section char offsets) that decorate the response without coupling the
 * core mapper to format-specific outline libraries.
 */
@FunctionalInterface
public interface ParseResponseDecorator {

    /**
     * Mutates {@code builder} in place using {@code context}. Implementations should no-op when
     * required inputs (for example {@link ParseMapContext#getSourceBytes()}) are unavailable.
     *
     * @param builder response under construction; already contains typed metadata from {@link ParseResponseMapper}
     * @param context parse inputs including metadata, text, and optional source bytes
     */
    void decorate(ParseResponse.Builder builder, ParseMapContext context);

}
