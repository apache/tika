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
package org.apache.tika.fuzzing;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import com.code_intelligence.jazzer.junit.FuzzTest;
import org.xml.sax.SAXException;

import org.apache.tika.exception.TikaException;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.parser.RecursiveParserWrapper;
import org.apache.tika.sax.BasicContentHandlerFactory;
import org.apache.tika.sax.RecursiveParserWrapperHandler;

/**
 * AutoDetectParser over the three ways a caller hands Tika bytes. Findings are Errors
 * (OOM, stack overflow), hangs, and anything not a TikaException/IOException/SAXException.
 * With {@code -Dtika.fuzz.strict=true}, a parser RuntimeException that CompositeParser
 * wrapped in a TikaException is a finding too.
 */
class AutoDetectParserFuzzTest {

    private static final Parser PARSER = new RecursiveParserWrapper(new AutoDetectParser());
    private static final boolean STRICT = Boolean.getBoolean("tika.fuzz.strict");

    @FuzzTest
    void bytes(byte[] data) throws Exception {
        try (TikaInputStream tis = TikaInputStream.get(data)) {
            parse(tis);
        }
    }

    @FuzzTest
    void stream(byte[] data) throws Exception {
        try (TikaInputStream tis = TikaInputStream.get(new ByteArrayInputStream(data))) {
            parse(tis);
        }
    }

    @FuzzTest
    void path(byte[] data) throws Exception {
        Path tmp = Files.createTempFile("tika-fuzz-", ".bin");
        try {
            Files.write(tmp, data);
            try (TikaInputStream tis = TikaInputStream.get(tmp)) {
                parse(tis);
            }
        } finally {
            Files.deleteIfExists(tmp);
        }
    }

    static void parse(TikaInputStream tis) throws Exception {
        RecursiveParserWrapperHandler handler = new RecursiveParserWrapperHandler(
                new BasicContentHandlerFactory(BasicContentHandlerFactory.HANDLER_TYPE.TEXT, -1));
        try {
            PARSER.parse(tis, handler, new Metadata(), new ParseContext());
        } catch (TikaException e) {
            if (STRICT && e.getCause() instanceof RuntimeException) {
                throw e;
            }
        } catch (IOException | SAXException e) {
            // expected on malformed input
        }
    }
}
