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
package org.apache.tika.parser.microsoft;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import org.apache.tika.TikaTest;
import org.apache.tika.exception.EncryptedDocumentException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;

public class OLE2CasingTest extends TikaTest {

    final static Set<String> IGNORE_FIELDS = new HashSet<>();

    static {
        IGNORE_FIELDS.add(TikaCoreProperties.PARSE_TIME_MILLIS.getName());
    }

    @Test
    public void testEncrypted() throws Exception {
        Assertions.assertThrows(EncryptedDocumentException.class, () -> {
            getXML("casing/protected_normal_case.docx");
        });
        Assertions.assertThrows(EncryptedDocumentException.class, () -> {
            getXML("casing/protected_upper_case.docx");
        });
    }

    @Test
    @Disabled("until POI can handle case insensitive entry lookups")
    public void testBasic() throws Exception {
        List<Metadata> metadataList = getRecursiveMetadata("casing/simple_normal_case.doc");
        assertCloseEnough(metadataList, getRecursiveMetadata("casing/simple_lower_case.doc"));
        assertCloseEnough(metadataList, getRecursiveMetadata("casing/simple_upper_case.doc"));
    }

    private void assertCloseEnough(List<Metadata> expected, List<Metadata> test) {
        for (int i = 0; i < expected.size(); i++) {
            assertCloseEnough(expected.get(i), test.get(i));
        }
    }

    private void assertCloseEnough(Metadata expected, Metadata test) {
        for (String n : expected.names()) {
            if (! IGNORE_FIELDS.contains(n)) {
                assertArrayEquals(expected.getValues(n), test.getValues(n));
            }
        }
    }
}
