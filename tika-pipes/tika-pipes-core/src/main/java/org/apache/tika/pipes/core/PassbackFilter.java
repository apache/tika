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
package org.apache.tika.pipes.core;

import java.io.Serializable;
import java.util.List;

import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;

/**
 * Filter/Select some of the emitted output and pass it back to the client parser.
 * <p>
 * This is intentionally NOT a MetadataFilter. MetadataFilter is applied before emission
 * to transform metadata (e.g., remove fields, compute digests). PassbackFilter is applied
 * after emission to select metadata to pass back from the forked PipesServer to the parent.
 * They share a method signature but serve entirely different purposes.
 */
public abstract class PassbackFilter implements Serializable {

    /**
     * Filters the metadata list in place, selecting which data to pass back to the client.
     *
     * @param metadataList the list to filter (must be mutable)
     * @throws TikaException if filtering fails
     */
    public abstract void filter(List<Metadata> metadataList) throws TikaException;
}
