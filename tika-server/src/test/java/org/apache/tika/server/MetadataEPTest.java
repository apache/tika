package org.apache.tika.server;

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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import org.apache.cxf.binding.BindingFactoryManager;
import org.apache.cxf.endpoint.Server;
import org.apache.cxf.jaxrs.JAXRSBindingFactory;
import org.apache.cxf.jaxrs.JAXRSServerFactoryBean;
import org.apache.cxf.jaxrs.client.WebClient;
import org.apache.tika.io.IOUtils;
import org.eclipse.jetty.util.ajax.JSON;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import au.com.bytecode.opencsv.CSVReader;

public class MetadataEPTest extends CXFTestBase {
  private static final String META_PATH = "/metadata";

  private static final String endPoint = "http://localhost:" + TikaServerCli.DEFAULT_PORT;

  private Server server;

  private static InputStream copy(InputStream in, int remaining) throws IOException {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    while (remaining > 0) {
      byte[] bytes = new byte[remaining];
      int n = in.read(bytes);
      if (n <= 0) {
        break;
      }
      out.write(bytes, 0, n);
      remaining -= n;
    }
    return new ByteArrayInputStream(out.toByteArray());
  }

  @Before
  public void setUp() {
    JAXRSServerFactoryBean sf = new JAXRSServerFactoryBean();
    sf.setResourceClasses(MetadataEP.class);
    List<Object> providers = new ArrayList<Object>();
    providers.add(new CSVMessageBodyWriter());
    providers.add(new JSONMessageBodyWriter());
    sf.setProviders(providers);
    sf.setAddress(endPoint + "/");
    BindingFactoryManager manager = sf.getBus().getExtension(BindingFactoryManager.class);
    JAXRSBindingFactory factory = new JAXRSBindingFactory();
    factory.setBus(sf.getBus());
    manager.registerBindingFactory(JAXRSBindingFactory.JAXRS_BINDING_ID, factory);
    server = sf.create();
  }

  @After
  public void tearDown()  {
    server.stop();
    server.destroy();
  }

  @Test
  public void testSimpleWord_CSV() throws Exception {
    Response response = WebClient.create(endPoint + META_PATH).type("application/msword").accept("text/csv")
        .post(ClassLoader.getSystemResourceAsStream(TikaResourceTest.TEST_DOC));
    Assert.assertEquals(Status.OK.getStatusCode(), response.getStatus());

    Reader reader = new InputStreamReader((InputStream) response.getEntity());

    @SuppressWarnings("resource")
    CSVReader csvReader = new CSVReader(reader);

    Map<String, String> metadata = new HashMap<String, String>();

    String[] nextLine;
    while ((nextLine = csvReader.readNext()) != null) {
      metadata.put(nextLine[0], nextLine[1]);
    }

    assertNotNull(metadata.get("Author"));
    assertEquals("Maxim Valyanskiy", metadata.get("Author"));
  }

  @Test
  public void testSimpleWord_JSON() throws Exception {
    Response response = WebClient.create(endPoint + META_PATH).type("application/msword")
        .accept(MediaType.APPLICATION_JSON).post(ClassLoader.getSystemResourceAsStream(TikaResourceTest.TEST_DOC));

    Assert.assertEquals(Status.OK.getStatusCode(), response.getStatus());

    Reader reader = new InputStreamReader((InputStream) response.getEntity());
    Map<?, ?> metadata = (Map<?, ?>) JSON.parse(reader);

    assertNotNull(metadata.get("Author"));
    assertEquals("Maxim Valyanskiy", metadata.get("Author"));
  }

  @Test
  public void testGetField_Author_TEXT() throws Exception {
    Response response = WebClient.create(endPoint + META_PATH + "/Author").type("application/msword")
        .accept(MediaType.TEXT_PLAIN).post(ClassLoader.getSystemResourceAsStream(TikaResourceTest.TEST_DOC));
    Assert.assertEquals(Status.OK.getStatusCode(), response.getStatus());

    StringWriter w = new StringWriter();
    IOUtils.copy((InputStream) response.getEntity(), w);
    assertEquals("Maxim Valyanskiy", w.toString());
  }

  @Test
  public void testGetField_Author_JSON() throws Exception {
    Response response = WebClient.create(endPoint + META_PATH + "/Author").type("application/msword")
        .accept(MediaType.APPLICATION_JSON).post(ClassLoader.getSystemResourceAsStream(TikaResourceTest.TEST_DOC));
    Assert.assertEquals(Status.OK.getStatusCode(), response.getStatus());

    Reader reader = new InputStreamReader((InputStream) response.getEntity());
    Map<?, ?> metadata = (Map<?, ?>) JSON.parse(reader);

    assertNotNull(metadata.get("Author"));
    assertEquals("Maxim Valyanskiy", metadata.get("Author"));
  }

  @Test
  public void testGetField_XXX_NotFound() throws Exception {
    Response response = WebClient.create(endPoint + META_PATH + "/xxx").type("application/msword")
        .accept(MediaType.APPLICATION_JSON).post(ClassLoader.getSystemResourceAsStream(TikaResourceTest.TEST_DOC));
    Assert.assertEquals(Status.NOT_FOUND.getStatusCode(), response.getStatus());
  }

  @Test
  public void testGetField_Author_TEXT_Partial_BAD_REQUEST() throws Exception {

    InputStream stream = ClassLoader.getSystemResourceAsStream(TikaResourceTest.TEST_DOC);

    Response response = WebClient.create(endPoint + META_PATH + "/Author").type("application/msword")
        .accept(MediaType.TEXT_PLAIN).post(copy(stream, 8000));
    Assert.assertEquals(Status.BAD_REQUEST.getStatusCode(), response.getStatus());
  }

  @Test
  public void testGetField_Author_TEXT_Partial_Found() throws Exception {

    InputStream stream = ClassLoader.getSystemResourceAsStream(TikaResourceTest.TEST_DOC);

    Response response = WebClient.create(endPoint + META_PATH + "/Author").type("application/msword")
        .accept(MediaType.TEXT_PLAIN).post(copy(stream, 12000));
    Assert.assertEquals(Status.OK.getStatusCode(), response.getStatus());

    StringWriter w = new StringWriter();
    IOUtils.copy((InputStream) response.getEntity(), w);
    assertEquals("Maxim Valyanskiy", w.toString());
  }

}
