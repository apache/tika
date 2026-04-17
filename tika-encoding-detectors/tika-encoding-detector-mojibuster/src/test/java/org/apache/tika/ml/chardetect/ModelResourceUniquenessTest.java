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
 * quietly producing wrong eval numbers.  These tests assert there is
 * exactly one copy of each specialist's model on the classpath, so
 * accidentally planting a second (test or stale) copy fails the build
 * immediately instead of at eval time.
 */
public class ModelResourceUniquenessTest {

    private static final String UTF16_RESOURCE =
            "org/apache/tika/ml/chardetect/utf16-specialist.bin";

    private static List<URL> findAll(String resource) throws IOException {
        Enumeration<URL> urls =
                Thread.currentThread().getContextClassLoader().getResources(resource);
        return Collections.list(urls);
    }

    @Test
    public void utf16ModelResourceIsUnique() throws IOException {
        List<URL> urls = findAll(UTF16_RESOURCE);
        assertEquals(1, urls.size(),
                "Expected exactly one copy of " + UTF16_RESOURCE
                        + " on the classpath, found: " + urls);
    }

    @Test
    public void specialistConstructorLoadsSameBytesAsClasspathResource()
            throws IOException {
        // The specialist classes load via their own DEFAULT_MODEL_RESOURCE
        // constants.  If those constants ever drift from the production
        // resource path, both the md5 match and the load would succeed but
        // point at different files.  Assert bytes-equal.
        byte[] utf16ResourceBytes;
        try (InputStream is = Thread.currentThread().getContextClassLoader()
                .getResourceAsStream(UTF16_RESOURCE)) {
            assertNotNull(is, "classpath missing " + UTF16_RESOURCE);
            utf16ResourceBytes = IOUtils.toByteArray(is);
        }
        byte[] utf16ViaConstant;
        try (InputStream is = Utf16SpecialistEncodingDetector.class
                .getResourceAsStream(
                        Utf16SpecialistEncodingDetector.DEFAULT_MODEL_RESOURCE)) {
            assertNotNull(is, "constant resolves to null: "
                    + Utf16SpecialistEncodingDetector.DEFAULT_MODEL_RESOURCE);
            utf16ViaConstant = IOUtils.toByteArray(is);
        }
        assertArraysEqual(utf16ResourceBytes, utf16ViaConstant,
                "UTF-16 model loaded via DEFAULT_MODEL_RESOURCE differs from "
                        + "classpath " + UTF16_RESOURCE);
    }

    private static void assertArraysEqual(byte[] a, byte[] b, String message) {
        if (!java.util.Arrays.equals(a, b)) {
            throw new AssertionError(message
                    + " (len " + a.length + " vs " + b.length + ")");
        }
    }
}
