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
package org.apache.tika.fork;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

import junit.framework.TestCase;

import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.BodyContentHandler;
import org.xml.sax.ContentHandler;

public class ForkParserTest extends TestCase {

    public void testHelloWorld() throws Exception {
        ForkParser parser = new ForkParser(
                ForkParserTest.class.getClassLoader(),
                new ForkTestParser());
        try {
            ContentHandler output = new BodyContentHandler();
            InputStream stream = new ByteArrayInputStream(new byte[0]);
            ParseContext context = new ParseContext();
            parser.parse(stream, output, new Metadata(), context);
            assertEquals("Hello, World!", output.toString().trim());
        } finally {
            parser.close();
        }
    }


}
