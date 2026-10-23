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
package org.apache.tika.parser;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;

public class ParseRecordExternalReferenceTest {

    @Test
    public void testCapAndCount() {
        ParseRecord record = ParseRecord.newInstance(new ParseContext());
        Metadata m = new Metadata();
        record.beforeParse(m);
        for (int i = 0; i < ParseRecord.MAX_EXTERNAL_REFERENCES + 5; i++) {
            record.addExternalReference("http://127.0.0.1:9/" + i);
        }
        record.afterParse();
        assertEquals(ParseRecord.MAX_EXTERNAL_REFERENCES,
                m.getValues(TikaCoreProperties.XML_EXTERNAL_REFERENCE).length);
        assertEquals(ParseRecord.MAX_EXTERNAL_REFERENCES + 5,
                m.getInt(TikaCoreProperties.XML_EXTERNAL_REFERENCE_COUNT));
        assertFalse(record.isExternalReferenceInEmbedded());
    }

    @Test
    public void testLongReferenceTruncated() {
        ParseRecord record = ParseRecord.newInstance(new ParseContext());
        Metadata m = new Metadata();
        record.beforeParse(m);
        record.addExternalReference("http://127.0.0.1:9/" + "x".repeat(5000));
        assertEquals(1000, m.get(TikaCoreProperties.XML_EXTERNAL_REFERENCE).length());
    }

    @Test
    public void testOutsideAParseIsANoOp() {
        ParseRecord record = ParseRecord.newInstance(new ParseContext());
        record.addExternalReference("http://127.0.0.1:9/x");
        assertFalse(record.isExternalReferenceInEmbedded());
    }

    @Test
    public void testNestedGoesToInnerAndFlagsEmbedded() {
        ParseRecord record = ParseRecord.newInstance(new ParseContext());
        Metadata outer = new Metadata();
        Metadata inner = new Metadata();
        record.beforeParse(outer);
        record.beforeParse(inner);
        record.addExternalReference("http://127.0.0.1:9/x");
        record.afterParse();
        record.afterParse();
        assertNull(outer.get(TikaCoreProperties.XML_EXTERNAL_REFERENCE));
        assertEquals("http://127.0.0.1:9/x", inner.get(TikaCoreProperties.XML_EXTERNAL_REFERENCE));
        assertTrue(record.isExternalReferenceInEmbedded());
    }
}
