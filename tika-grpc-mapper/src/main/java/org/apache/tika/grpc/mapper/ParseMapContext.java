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

import java.util.List;

import org.apache.tika.metadata.Metadata;

/**
 * Inputs available while building or decorating a {@link org.apache.tika.grpc.v1.ParseResponse}.
 * <p>
 * Decorators (for example PDF/HTML outline enrichment) receive this context so they can use
 * Tika metadata, extracted text, and optionally the original document bytes when available.
 */
public final class ParseMapContext {

    private final Metadata primary;
    private final List<Metadata> allMetadata;
    private final String extractedText;
    private final String docId;
    private final byte[] sourceBytes;

    private ParseMapContext(Metadata primary, List<Metadata> allMetadata, String extractedText,
                            String docId, byte[] sourceBytes) {
        this.primary = primary;
        this.allMetadata = allMetadata == null ? List.of() : List.copyOf(allMetadata);
        this.extractedText = extractedText;
        this.docId = docId;
        this.sourceBytes = sourceBytes == null ? null : sourceBytes.clone();
    }

    /**
     * Creates a context without source bytes (typical gRPC pipes path where only metadata is returned).
     */
    public static ParseMapContext of(Metadata primary, List<Metadata> allMetadata,
                                     String extractedText, String docId) {
        return new ParseMapContext(primary, allMetadata, extractedText, docId, null);
    }

    /**
     * Creates a context including original document bytes for decorators that need raw content
     * (PDF bookmarks, HTML heading trees, Markdown headings, section offsets).
     */
    public static ParseMapContext withSourceBytes(Metadata primary, List<Metadata> allMetadata,
                                                  String extractedText, String docId,
                                                  byte[] sourceBytes) {
        return new ParseMapContext(primary, allMetadata, extractedText, docId, sourceBytes);
    }

    public Metadata getPrimary() {
        return primary;
    }

    public List<Metadata> getAllMetadata() {
        return allMetadata;
    }

    public String getExtractedText() {
        return extractedText;
    }

    public String getDocId() {
        return docId;
    }

    /**
     * Original document bytes when supplied; {@code null} if not available.
     */
    public byte[] getSourceBytes() {
        return sourceBytes == null ? null : sourceBytes.clone();
    }

    public boolean hasSourceBytes() {
        return sourceBytes != null && sourceBytes.length > 0;
    }

}
