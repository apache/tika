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
package org.apache.tika.ml.chardetect;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;

import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.Test;

/**
 * Belt-and-suspenders check for a failure mode we've been burned by:
 * a test-tree copy of a model file shadowing the production copy and
 * quietly producing wrong eval numbers.  There must be exactly one copy
 * of the charset model on the classpath.
 */
public class ModelResourceUniquenessTest {

    private static final String MODEL_RESOURCE =
            "org/apache/tika/ml/chardetect/hashed-ngram.hng";

    private static List<URL> findAll(String resource) throws IOException {
        Enumeration<URL> urls =
                Thread.currentThread().getContextClassLoader().getResources(resource);
        return Collections.list(urls);
    }

    @Test
    public void modelResourceIsUnique() throws IOException {
        List<URL> urls = findAll(MODEL_RESOURCE);
        assertEquals(1, urls.size(),
                "Expected exactly one copy of " + MODEL_RESOURCE
                        + " on the classpath, found: " + urls);
    }

    @Test
    public void defaultResourceConstantPointsAtTheModel() throws IOException {
        byte[] viaClassLoader;
        try (InputStream is = Thread.currentThread().getContextClassLoader()
                .getResourceAsStream(MODEL_RESOURCE)) {
            assertNotNull(is, "classpath missing " + MODEL_RESOURCE);
            viaClassLoader = IOUtils.toByteArray(is);
        }
        byte[] viaConstant;
        try (InputStream is = HashedNgramEncodingDetector.class
                .getResourceAsStream(HashedNgramEncodingDetector.DEFAULT_MODEL_RESOURCE)) {
            assertNotNull(is, "constant resolves to null: "
                    + HashedNgramEncodingDetector.DEFAULT_MODEL_RESOURCE);
            viaConstant = IOUtils.toByteArray(is);
        }
        assertArrayEquals(viaClassLoader, viaConstant);
    }
}
