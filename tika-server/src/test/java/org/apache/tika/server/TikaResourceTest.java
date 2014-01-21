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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.InputStream;

import javax.ws.rs.core.Response;

import org.apache.cxf.binding.BindingFactoryManager;
import org.apache.cxf.endpoint.Server;
import org.apache.cxf.jaxrs.JAXRSBindingFactory;
import org.apache.cxf.jaxrs.JAXRSServerFactoryBean;
import org.apache.cxf.jaxrs.client.WebClient;
import org.apache.cxf.jaxrs.ext.multipart.Attachment;
import org.apache.cxf.jaxrs.lifecycle.SingletonResourceProvider;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class TikaResourceTest extends CXFTestBase {
	private static final String TIKA_PATH = "/tika";
	public static final String TEST_DOC = "test.doc";
	public static final String TEST_XLSX = "16637.xlsx";
	private static final int UNPROCESSEABLE = 422;
	private static final String endPoint = "http://localhost:"
			+ TikaServerCli.DEFAULT_PORT;
	
	private Server server;

	@Before
	public void setUp() {
		JAXRSServerFactoryBean sf = new JAXRSServerFactoryBean();
		sf.setResourceClasses(TikaResource.class);
		sf.setResourceProvider(TikaResource.class,
				new SingletonResourceProvider(new TikaResource()));
		sf.setAddress(endPoint + "/");
		BindingFactoryManager manager = sf.getBus().getExtension(
				BindingFactoryManager.class);
		JAXRSBindingFactory factory = new JAXRSBindingFactory();
		factory.setBus(sf.getBus());
		manager.registerBindingFactory(JAXRSBindingFactory.JAXRS_BINDING_ID,
				factory);
		server = sf.create();
	}

    @After
	public void tearDown() throws Exception {
		server.stop();
		server.destroy();
	}

	@Test
	public void testHelloWorld() throws Exception {
		Response response = WebClient.create(endPoint + TIKA_PATH)
				.type("text/plain").accept("text/plain").get();
		assertEquals(TikaResource.GREETING,
				getStringFromInputStream((InputStream) response.getEntity()));
	}

	@Test
	public void testSimpleWord() throws Exception {
		Response response = WebClient.create(endPoint + TIKA_PATH)
				.type("application/msword")
				.accept("text/plain")
				.put(ClassLoader.getSystemResourceAsStream(TEST_DOC));
		String responseMsg = getStringFromInputStream((InputStream) response
				.getEntity());
		assertTrue(responseMsg.contains("test"));
	}

	@Test
	public void testApplicationWadl() throws Exception {
		Response response = WebClient
				.create(endPoint + TIKA_PATH + "?_wadl")
				.accept("text/plain").get();
		String resp = getStringFromInputStream((InputStream) response
				.getEntity());
		assertTrue(resp.startsWith("<application"));
	}

	@Test
	public void testPasswordXLS() throws Exception {
		Response response = WebClient.create(endPoint + TIKA_PATH)
				.type("application/vnd.ms-excel")
				.accept("text/plain")
				.put(ClassLoader.getSystemResourceAsStream("password.xls"));

		assertEquals(UNPROCESSEABLE, response.getStatus());
	}

  @Test
  public void testSimpleWordHTML() throws Exception {
      Response response = WebClient.create(endPoint + TIKA_PATH)
              .type("application/msword")
              .accept("text/html")
              .put(ClassLoader.getSystemResourceAsStream(TEST_DOC));
      String responseMsg = getStringFromInputStream((InputStream) response
              .getEntity());
      assertTrue(responseMsg.contains("test"));
  }

  @Test
  public void testPasswordXLSHTML() throws Exception {
      Response response = WebClient.create(endPoint + TIKA_PATH)
              .type("application/vnd.ms-excel")
              .accept("text/html")
              .put(ClassLoader.getSystemResourceAsStream("password.xls"));

      assertEquals(UNPROCESSEABLE, response.getStatus());
  }

  @Test
  public void testSimpleWordXML() throws Exception {
    Response response = WebClient.create(endPoint + TIKA_PATH)
      .type("application/msword")
      .accept("text/xml")
      .put(ClassLoader.getSystemResourceAsStream(TEST_DOC));
    String responseMsg = getStringFromInputStream((InputStream) response
      .getEntity());
    assertTrue(responseMsg.contains("test"));
  }

  @Test
  public void testPasswordXLSXML() throws Exception {
    Response response = WebClient.create(endPoint + TIKA_PATH)
      .type("application/vnd.ms-excel")
      .accept("text/xml")
      .put(ClassLoader.getSystemResourceAsStream("password.xls"));

    assertEquals(UNPROCESSEABLE, response.getStatus());
  }
  
  @Test
  public void testSimpleWordMultipartXML() throws Exception {
    ClassLoader.getSystemResourceAsStream(TEST_DOC);  
    Attachment attachmentPart = 
        new Attachment("myworddoc", "application/msword", ClassLoader.getSystemResourceAsStream(TEST_DOC));
    WebClient webClient = WebClient.create(endPoint + TIKA_PATH + "/form");
    Response response = webClient.type("multipart/form-data")
      .accept("text/xml")
      .put(attachmentPart);
    String responseMsg = getStringFromInputStream((InputStream) response
      .getEntity());
    assertTrue(responseMsg.contains("test"));
  }
  
}
