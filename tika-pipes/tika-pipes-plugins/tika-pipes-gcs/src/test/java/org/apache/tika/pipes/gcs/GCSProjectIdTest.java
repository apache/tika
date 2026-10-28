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
package org.apache.tika.pipes.gcs;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import org.apache.tika.exception.TikaConfigException;
import org.apache.tika.pipes.emitter.gcs.GCSEmitter;
import org.apache.tika.pipes.fetcher.gcs.GCSFetcher;
import org.apache.tika.pipes.iterator.gcs.GCSPipesIterator;
import org.apache.tika.plugins.ExtensionConfig;

/**
 * projectId is optional: reading, writing and listing objects need no project, so each
 * component builds without one. Building makes no request, so these need no credentials.
 */
public class GCSProjectIdTest {

    private static final String BUCKET_ONLY = "{\"bucket\": \"bucket\"}";
    private static final String WITH_PROJECT = "{\"bucket\": \"bucket\", \"projectId\": \"project\"}";

    @Test
    public void testFetcherWithoutProjectId() throws Exception {
        assertNotNull(GCSFetcher.build(new ExtensionConfig("gcs", "gcs-fetcher", BUCKET_ONLY)));
    }

    @Test
    public void testFetcherWithProjectId() throws Exception {
        assertNotNull(GCSFetcher.build(new ExtensionConfig("gcs", "gcs-fetcher", WITH_PROJECT)));
    }

    @Test
    public void testFetcherRequiresBucket() {
        assertThrows(TikaConfigException.class,
                () -> GCSFetcher.build(new ExtensionConfig("gcs", "gcs-fetcher", "{}")));
    }

    @Test
    public void testEmitterWithoutProjectId() throws Exception {
        assertNotNull(GCSEmitter.build(new ExtensionConfig("gcs", "gcs-emitter", BUCKET_ONLY)));
    }

    @Test
    public void testEmitterWithProjectId() throws Exception {
        assertNotNull(GCSEmitter.build(new ExtensionConfig("gcs", "gcs-emitter", WITH_PROJECT)));
    }

    @Test
    public void testEmitterRequiresBucket() {
        assertThrows(TikaConfigException.class,
                () -> GCSEmitter.build(new ExtensionConfig("gcs", "gcs-emitter", "{}")));
    }

    @Test
    public void testIteratorWithoutProjectId() throws Exception {
        assertNotNull(GCSPipesIterator.build(new ExtensionConfig("gcs", "gcs-pipes-iterator", BUCKET_ONLY)));
    }

    @Test
    public void testIteratorWithProjectId() throws Exception {
        assertNotNull(GCSPipesIterator.build(new ExtensionConfig("gcs", "gcs-pipes-iterator", WITH_PROJECT)));
    }

    @Test
    public void testIteratorRequiresBucket() {
        assertThrows(TikaConfigException.class,
                () -> GCSPipesIterator.build(new ExtensionConfig("gcs", "gcs-pipes-iterator", "{}")));
    }
}
