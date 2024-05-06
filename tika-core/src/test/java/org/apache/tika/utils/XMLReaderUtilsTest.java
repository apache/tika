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
package org.apache.tika.utils;

import static org.junit.jupiter.api.Assertions.fail;

import java.io.ByteArrayInputStream;
import java.net.ConnectException;
import java.nio.charset.StandardCharsets;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.ToTextContentHandler;
import org.junit.jupiter.api.Test;

public class XMLReaderUtilsTest {
    // make sure that parseSAX actually defends against external entities
    @Test
    public void testExternalDTD() throws Exception {
        String xml = "<!DOCTYPE foo SYSTEM \"http://127.234.172.38:7845/bar\"><foo/>";
        try {
            XMLReaderUtils.parseSAX(
                    new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)),
                    new ToTextContentHandler(),
                    new ParseContext());
        } catch (ConnectException e) {
            fail("Parser tried to access the external DTD:" + e);
        }
    }

    @Test
    public void testExternalEntity() throws Exception {
        String xml =
                "<!DOCTYPE foo ["
                        + " <!ENTITY bar SYSTEM \"http://127.234.172.38:7845/bar\">"
                        + " ]><foo>&bar;</foo>";
        try {
            XMLReaderUtils.parseSAX(
                    new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)),
                    new ToTextContentHandler(),
                    new ParseContext());
        } catch (ConnectException e) {
            fail("Parser tried to access the external DTD:" + e);
        }
    }
}
