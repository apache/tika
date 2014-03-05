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
package org.apache.tika.parser.mbox;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.apache.tika.TikaTest;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.Parser;
import org.junit.Test;

public class OutlookPSTParserTest extends TikaTest {

  private Parser parser = new OutlookPSTParser();

  @Test
  public void testAccept() throws Exception {
    assertTrue((parser.getSupportedTypes(null).contains(MediaType.application("vnd.ms-outlook-pst"))));
  }

  @Test
  public void testParse() throws Exception {
    XMLResult result = getXML(getResourceAsStream("/test-documents/testPST.pst"), new OutlookPSTParser(), new Metadata());
    String xml = result.xml;

    assertFalse(xml.isEmpty());
    assertTrue(xml.contains("<meta name=\"Content-Length\" content=\"271360\" />"));
    assertTrue(xml.contains("<meta name=\"Content-Type\" content=\"application/vnd.ms-outlook-pst\" />"));

    assertTrue(xml.contains("<body><div class=\"email-folder\"><h1>"));
    assertTrue(xml.contains("<div class=\"email-entry\"><h1>&lt;530D9CAC.5080901@gmail.com&gt;</h1>"));
    assertTrue(xml.contains("<div class=\"email-entry\"><h1>&lt;1393363252.28814.YahooMailNeo@web140906.mail.bf1.yahoo.com&gt;</h1>"));
    assertTrue(xml.contains("Gary Murphy commented on TIKA-1250:"));

    assertTrue(xml.contains("<div class=\"email-folder\"><h1>Racine (pour la recherche)</h1>"));
  }

}
