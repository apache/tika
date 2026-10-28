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

import java.util.List;

import org.apache.tika.metadata.Metadata;

/**
 * One child document an adapter made, as the contribution builder kept it.
 *
 * @param path     where it sits in the parent, in the form of Tika's
 *                 {@code X-TIKA:embedded_resource_path}: starts with {@code /}, unique within
 *                 the contribution
 * @param metadata its metadata; a copy, since {@link Metadata} can change
 * @param errors   what went wrong reading it, if anything; a child with errors is
 *                 {@code STATUS_PARTIAL} in the Document
 */
public record ChildDraft(String path, Metadata metadata, List<String> errors) {

    public ChildDraft {
        Metadata copy = new Metadata();
        copy.putAll(metadata);
        metadata = copy;
        errors = List.copyOf(errors);
    }

    /** A copy of the metadata. */
    @Override
    public Metadata metadata() {
        Metadata copy = new Metadata();
        copy.putAll(metadata);
        return copy;
    }
}
