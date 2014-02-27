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

import static junit.framework.Assert.assertTrue;

import java.io.InputStream;

import org.apache.tika.TikaTest;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.Parser;
import org.apache.tika.parser.microsoft.ooxml.OOXMLParserTest;
import org.junit.Test;



public class OfficeParserTest extends TikaTest {

  @Test
  public void parseOfficeWord() throws Exception {
    Metadata metadata = new Metadata();
    Parser parser = new OfficeParser();

    String xml = getXML(getTestDocument("test.doc"), parser, metadata).xml;

    assertTrue(xml.contains("test"));
  }

  private InputStream getTestDocument(String name) {
    return TikaInputStream.get(OOXMLParserTest.class.getResourceAsStream("/test-documents/" + name));
}
}
