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

package org.apache.tika.extractor;

import org.apache.tika.batch.DigestingAutoDetectParserFactory;
import org.apache.tika.config.TikaConfig;
import org.apache.tika.extractor.EmbeddedDocumentUtil;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.parser.RecursiveParserWrapper;
import org.apache.tika.sax.BasicContentHandlerFactory;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class TestEmbeddedDocumentUtil {
    //TODO -- figure out how to mock this into tika-core

    @Test
    public void testSimple() {
        Parser p = new AutoDetectParser();
        ParseContext parseContext = new ParseContext();
        parseContext.set(Parser.class, p);
        Parser txtParser = EmbeddedDocumentUtil.tryToFindExistingLeafParser(org.apache.tika.parser.csv.TextAndCSVParser.class, parseContext);
        assertNotNull(txtParser);
        assertEquals(org.apache.tika.parser.csv.TextAndCSVParser.class, txtParser.getClass());

    }

    @Test
    public void testDoublyDecorated() {
        Parser d = new DigestingAutoDetectParserFactory().getParser(TikaConfig.getDefaultConfig());
        RecursiveParserWrapper wrapper = new RecursiveParserWrapper(d,
                new BasicContentHandlerFactory(BasicContentHandlerFactory.HANDLER_TYPE.TEXT, -1));
        ParseContext parseContext = new ParseContext();
        parseContext.set(Parser.class, wrapper);
        Parser txtParser = EmbeddedDocumentUtil.tryToFindExistingLeafParser(org.apache.tika.parser.csv.TextAndCSVParser.class, parseContext);
        assertNotNull(txtParser);
        assertEquals(org.apache.tika.parser.csv.TextAndCSVParser.class, txtParser.getClass());
    }
}
