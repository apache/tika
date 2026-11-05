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
package org.apache.tika.ml.junkdetect;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

import org.junit.jupiter.api.Test;

/** The bundled model must ship with the provenance record its trainer wrote. */
public class ModelProvenanceTest {

    @Test
    void provenanceMatchesBundledModel() throws Exception {
        byte[] model;
        try (InputStream is = getClass().getClassLoader()
                .getResourceAsStream(JunkDetector.DEFAULT_MODEL_RESOURCE)) {
            assertNotNull(is, "bundled model missing");
            model = is.readAllBytes();
        }
        String provenance;
        try (InputStream is = getClass().getClassLoader()
                .getResourceAsStream(JunkDetector.DEFAULT_MODEL_RESOURCE + ".provenance")) {
            assertNotNull(is, "junkdetect.bin.provenance missing; train with train-junk-model.sh");
            provenance = new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
        String recorded = null;
        for (String line : provenance.split("\n")) {
            if (line.startsWith("model_sha256\t")) {
                recorded = line.substring("model_sha256\t".length()).trim();
            }
        }
        String actual = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(model));
        assertEquals(actual, recorded,
                "junkdetect.bin changed without its provenance; retrain with train-junk-model.sh");
    }
}
