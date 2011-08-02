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

import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.core.header.MediaTypes;
import com.sun.jersey.test.framework.JerseyTest;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class TikaResourceTest extends JerseyTest {
  private static final String TIKA_PATH = "tika";
  public static final String TEST_DOC = "test.doc";
  public static final String TEST_XLSX = "16637.xlsx";
  private static final int UNPROCESSEABLE = 422;

  public TikaResourceTest() throws Exception {
    super("org.apache.tika.server");
  }

  /**
   * Test to see that the message "Hello World" is sent in the response.
   */
  @Test
  public void testHelloWorld() {
    String responseMsg = resource().path(TIKA_PATH).get(String.class);
    assertEquals(TikaResource.GREETING, responseMsg);
  }

  @Test
  public void testSimpleWord() {
    String responseMsg =
            resource().path(TIKA_PATH)
            .type("application/msword")
                    .put(String.class, ClassLoader.getSystemResourceAsStream(TEST_DOC));

    assertTrue(responseMsg.contains("test"));
  }

  @Test
  public void testApplicationWadl() {
    String serviceWadl = resource().path("application.wadl").
            accept(MediaTypes.WADL).get(String.class);

    assertTrue(serviceWadl.length() > 0);
  }

  @Test
  public void testPasswordXLS() throws Exception {
    ClientResponse cr =
            resource()
                    .path(TIKA_PATH)
                    .type("application/vnd.ms-excel")                    
                    .put(ClientResponse.class, ClassLoader.getSystemResourceAsStream("password.xls"));

    assertEquals(UNPROCESSEABLE, cr.getStatus());
  }
}
