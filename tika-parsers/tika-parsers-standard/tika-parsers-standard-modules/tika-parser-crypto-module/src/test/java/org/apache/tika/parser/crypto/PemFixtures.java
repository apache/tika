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
package org.apache.tika.parser.crypto;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/** Builds PEM-armored test inputs from the DER fixtures. */
public final class PemFixtures {

    private PemFixtures() {
    }

    public static byte[] resource(String name) throws IOException {
        try (InputStream is = PemFixtures.class.getResourceAsStream("/test-documents/" + name)) {
            return is.readAllBytes();
        }
    }

    /** {@code der} armored as {@code -----BEGIN <label>-----} with 64-column lines ending in {@code eol}. */
    public static byte[] pem(byte[] der, String label, String eol) {
        String body = Base64.getMimeEncoder(64, eol.getBytes(StandardCharsets.US_ASCII))
                .encodeToString(der);
        return ("-----BEGIN " + label + "-----" + eol + body + eol + "-----END " + label + "-----" + eol)
                .getBytes(StandardCharsets.US_ASCII);
    }
}
