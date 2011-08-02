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

package org.apache.tika.server;

import au.com.bytecode.opencsv.CSVReader;
import com.sun.jersey.test.framework.JerseyTest;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import org.junit.Test;

import java.io.Reader;
import java.util.HashMap;
import java.util.Map;

public class MetadataResourceTest extends JerseyTest {
  private static final String META_PATH = "/meta";

  public MetadataResourceTest() throws Exception {
    super("org.apache.tika.server");
  }

  @Test
  public void testSimpleWord() throws Exception {
    Reader reader =
            resource().path(META_PATH)
            .type("application/msword")
                    .put(Reader.class, ClassLoader.getSystemResourceAsStream(TikaResourceTest.TEST_DOC));

    CSVReader csvReader = new CSVReader(reader);

    Map<String,String> metadata = new HashMap<String, String>();

    String[] nextLine;
    while ((nextLine = csvReader.readNext()) != null) {
      metadata.put(nextLine[0], nextLine[1]);
    }

    assertNotNull(metadata.get("Author"));
    assertEquals("Maxim Valyanskiy", metadata.get("Author"));
  }
/*
  @Test
  public void testXLSX() throws Exception {
    Reader reader =
            webResource.path(META_PATH)
            .type("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
                    .header("File-Name", TikaResourceTest.TEST_XLSX)
                    .put(Reader.class, ClassLoader.getSystemResourceAsStream(TikaResourceTest.TEST_XLSX));

    CSVReader csvReader = new CSVReader(reader);

    final Map < String, String > metadataActual = new HashMap < String, String > (),
            metadataExpected = new HashMap < String, String > ();

    String[] nextLine;
    while ((nextLine = csvReader.readNext()) != null) {
      metadataActual.put(nextLine[0], nextLine[1]);
    }
    metadataExpected.put("Author", "jet");
    metadataExpected.put("Application-Name", "Microsoft Excel");
    metadataExpected.put("description", "Тестовый комментарий");
    metadataExpected.put("resourceName", TikaResourceTest.TEST_XLSX);
    metadataExpected.put("protected", "false");
    metadataExpected.put("Creation-Date", "2010-05-11T12:37:42Z");
    metadataExpected.put("Last-Modified", "2010-05-11T14:46:20Z");
    assertEquals( true, metadataActual.size() >= metadataExpected.size() );
    for ( final Map.Entry < String, String > field : metadataExpected.entrySet() ) {
      final String key = field.getKey(), valueActual = metadataActual.get(key), valueExpected = field.getValue();
      assertNotNull( valueActual );
      assertEquals( valueExpected, valueActual );
    }
  }
*/
}
