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
package org.apache.tika.extractor;

import org.apache.tika.metadata.Metadata;

/**
 * Interface for different document selection strategies for purposes like
 * embedded document extraction by a {@link ContainerExtractor} instance.
 * An implementation of this interface defines some specific selection
 * criteria to be applied against the document metadata passed to the
 * {@link #select(Metadata)} method.
 *
 * @since Apache Tika 0.8
 */
public interface DocumentSelector {

    /**
     * Checks if a document with the given metadata matches the specified
     * selection criteria.
     *
     * @param metadata document metadata
     * @return <code>true</code> if the document matches the selection criteria,
     *         <code>false</code> otherwise
     */
    boolean select(Metadata metadata);

}
