/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.tika.parser.strings;

import static org.apache.tika.parser.strings.StringsParser.getStringsProg;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.InputStream;
import java.util.Arrays;
import java.util.Collections;

import org.junit.jupiter.api.Test;
import org.xml.sax.ContentHandler;

import org.apache.tika.config.Initializable;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.parser.external.ExternalParser;
import org.apache.tika.sax.BodyContentHandler;

public class StringsParserTest {
    public static boolean canRun() {
        String[] checkCmd = {new StringsParser().getStringsPath() + getStringsProg(), "--version"};
        return ExternalParser.check(checkCmd);
    }

    @Test
    public void testParse() throws Exception {
        assumeTrue(canRun());

        String resource = "/test-documents/testOCTET_header.dbase3";

        String[] content = {"CLASSNO", "TITLE", "ITEMNO", "LISTNO", "LISTDATE"};

        String[] met_attributes = {"min-len", "encoding", "strings:file_output"};

        StringsConfig stringsConfig = new StringsConfig();

        Parser parser = new StringsParser();
        ((Initializable) parser).initialize(Collections.emptyMap());
        ContentHandler handler = new BodyContentHandler();
        Metadata metadata = new Metadata();

        ParseContext context = new ParseContext();
        context.set(StringsConfig.class, stringsConfig);

        try (InputStream stream = StringsParserTest.class.getResourceAsStream(resource)) {
            parser.parse(stream, handler, metadata, context);
        } catch (Exception e) {
            e.printStackTrace();
        }

        // Content
        for (String word : content) {
            assertTrue(handler.toString().contains(word), "can't find " + word);
        }

        // Metadata
        Arrays.equals(met_attributes, metadata.names());
    }
}
