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
package org.apache.tika.parser.inference;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.mime.MediaType;

/**
 * One thing offered to the dispatcher: the bytes of an image, a page render, a media clip,
 * with the document they came from and, when known, its parent. Captured at the offer so
 * a task can run after the document has closed; the bytes live in a file the dispatcher
 * owns until the flush, and the id paths let the flush find the metadata the recursive
 * wrapper kept once the parser's own objects are no longer read.
 */
public final class InferenceUnit {

    private final InputKind kind;
    private final MediaType type;
    private final Metadata target;
    private final Metadata parent;
    private final String targetIdPath;
    private final String parentIdPath;
    private final Path path;
    private final String text;
    private final long size;
    private final int page;

    public InferenceUnit(InputKind kind, MediaType type, Metadata target, Metadata parent,
                         Path path) throws IOException {
        this(kind, type, target, parent, path, -1);
    }

    /** A unit that is one page of its target: {@code page} is 1-based. */
    public InferenceUnit(InputKind kind, MediaType type, Metadata target, Metadata parent,
                         Path path, int page) throws IOException {
        this(kind, type, target, parent, target.get(TikaCoreProperties.EMBEDDED_ID_PATH),
                parent == null ? null : parent.get(TikaCoreProperties.EMBEDDED_ID_PATH), path,
                null, Files.size(path), page);
    }

    /** A {@link InputKind#TEXT} unit: the target's extracted text, held in memory. */
    public InferenceUnit(MediaType type, Metadata target, Metadata parent, String text) {
        this(InputKind.TEXT, type, target, parent,
                target.get(TikaCoreProperties.EMBEDDED_ID_PATH),
                parent == null ? null : parent.get(TikaCoreProperties.EMBEDDED_ID_PATH), null,
                text, text.getBytes(StandardCharsets.UTF_8).length, -1);
    }

    private InferenceUnit(InputKind kind, MediaType type, Metadata target, Metadata parent,
                          String targetIdPath, String parentIdPath, Path path, String text,
                          long size, int page) {
        this.kind = kind;
        this.type = type;
        this.target = target;
        this.parent = parent;
        this.targetIdPath = targetIdPath;
        this.parentIdPath = parentIdPath;
        this.path = path;
        this.text = text;
        this.size = size;
        this.page = page;
    }

    /** The same unit aimed at the metadata objects that are still read. */
    InferenceUnit retargeted(Metadata target, Metadata parent) {
        return new InferenceUnit(kind, type, target, parent, targetIdPath, parentIdPath, path,
                text, size, page);
    }

    /** The text of a {@link InputKind#TEXT} unit; null for the rest. */
    public String getText() {
        return text;
    }

    /** The 1-based page this unit renders, for {@link InputKind#PAGES}; -1 otherwise. */
    public int getPage() {
        return page;
    }

    public InputKind getKind() {
        return kind;
    }

    public MediaType getType() {
        return type;
    }

    /** The document the bytes belong to. */
    public Metadata getTarget() {
        return target;
    }

    /** The document the target is embedded in; null at top level. */
    public Metadata getParent() {
        return parent;
    }

    public String getTargetIdPath() {
        return targetIdPath;
    }

    public String getParentIdPath() {
        return parentIdPath;
    }

    public Path getPath() {
        return path;
    }

    public long getSize() {
        return size;
    }

    /** Readable during a task run only; the file is deleted when the flush ends. */
    public byte[] getBytes() throws IOException {
        return text != null ? text.getBytes(StandardCharsets.UTF_8) : Files.readAllBytes(path);
    }
}
