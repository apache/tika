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
package org.apache.tika.parser.pkg;

import static org.junit.jupiter.api.Assertions.fail;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.InputStream;

import org.junit.jupiter.api.Test;
import org.xml.sax.ContentHandler;

import org.apache.tika.exception.EncryptedDocumentException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.Parser;
import org.apache.tika.parser.external.ExternalParser;
import org.apache.tika.sax.BodyContentHandler;

/**
 * Test case for parsing unrar files.
 */
public class UnrarParserTest extends AbstractPkgTest {

    /**
     * Note - we don't currently support Encrypted RAR files,
     * so all we can do is throw a helpful exception
     */
    @Test
    public void testEncryptedRar() throws Exception {
        assumeTrue(ExternalParser.check("unrar"));
        Parser parser = new UnrarParser();

        try (InputStream input = getResourceAsStream("/test-documents/test-documents-enc.rar")) {
            Metadata metadata = new Metadata();
            ContentHandler handler = new BodyContentHandler();

            // Note - we don't currently support encrypted RAR
            // files so we can't check the contents
            parser.parse(input, handler, metadata, trackingContext);
            fail("No support yet for Encrypted RAR files");
        } catch (EncryptedDocumentException e) {
            // Good, as expected right now
        }
    }
}
