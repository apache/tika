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
package org.apache.tika.parser.microsoft.onenote;

import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

import org.apache.tika.exception.TikaException;

public class GUIDTest {

    @Test
    public void testRejectsMalformedCurlyBraceGuid() {
        byte[] malformed = "{638DE92F-A6D4-4BC1-9A36-4ĲFC2511A5B7}"
                .getBytes(StandardCharsets.UTF_16LE);

        assertThrows(TikaException.class, () -> GUID.fromCurlyBraceUTF16Bytes(malformed));
    }
}
