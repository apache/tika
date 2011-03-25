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

import junit.framework.TestCase;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.BodyContentHandler;
import org.xml.sax.ContentHandler;

import java.io.InputStream;

public class WriteProtectedParserTest extends TestCase {
    public void testWriteProtected() throws Exception {
        InputStream input = ExcelParserTest.class.getResourceAsStream(
                "/test-documents/protect.xlsx");

        Metadata metadata = new Metadata();
        ContentHandler handler = new BodyContentHandler();
        new OfficeParser().parse(input, handler, metadata, new ParseContext());
        String content = handler.toString();
        assertTrue(content.contains("Office"));
    }
}
