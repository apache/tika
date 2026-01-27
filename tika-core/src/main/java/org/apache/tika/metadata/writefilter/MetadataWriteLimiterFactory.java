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
package org.apache.tika.metadata.writefilter;

/**
 * Factory interface for creating {@link MetadataWriteLimiter} instances.
 * <p>
 * Implementations of this interface are placed in ParseContext and used
 * by {@code ParseContext.newMetadata()} to create Metadata objects with
 * limits applied at creation time.
 *
 * @since Apache Tika 4.0
 */
public interface MetadataWriteLimiterFactory {
    /**
     * Creates a new limiter instance.
     * Each call should return a fresh instance since limiters are stateful.
     *
     * @return a new MetadataWriteLimiter instance
     */
    MetadataWriteLimiter newInstance();
}
