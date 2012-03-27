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

import java.io.IOException;
import org.apache.commons.httpclient.HttpException;
import org.apache.cxf.binding.BindingFactoryManager;
import org.apache.cxf.endpoint.Server;
import org.apache.cxf.jaxrs.JAXRSBindingFactory;
import org.apache.cxf.jaxrs.JAXRSServerFactoryBean;
import org.apache.cxf.jaxrs.lifecycle.SingletonResourceProvider;
import org.junit.Test;

public class TikaResourceTest extends CXFTestBase {
	private static final String TIKA_PATH = "tika";
	public static final String TEST_DOC = "test.doc";
	public static final String TEST_XLSX = "16637.xlsx";
	private static final int UNPROCESSEABLE = 422;
	private static final String service = "http://localhost:"
			+ TikaServerCli.DEFAULT_PORT + "/";
	private static final String endPoint = "http://localhost:"
			+ TikaServerCli.DEFAULT_PORT + "/" + TIKA_PATH;

	private Server server;

	/*
	 * (non-Javadoc)
	 * 
	 * @see junit.framework.TestCase#setUp()
	 */
	@Override
	protected void setUp() throws Exception {
		JAXRSServerFactoryBean sf = new JAXRSServerFactoryBean();
		sf.setResourceClasses(TikaResource.class);
		sf.setResourceProvider(TikaResource.class,
				new SingletonResourceProvider(new TikaResource()));
		sf.setAddress(service);
		BindingFactoryManager manager = sf.getBus().getExtension(
				BindingFactoryManager.class);
		JAXRSBindingFactory factory = new JAXRSBindingFactory();
		factory.setBus(sf.getBus());
		manager.registerBindingFactory(JAXRSBindingFactory.JAXRS_BINDING_ID,
				factory);
		server = sf.create();
	}
	

	/*
	 * (non-Javadoc)
	 * 
	 * @see junit.framework.TestCase#tearDown()
	 */
	@Override
	protected void tearDown() throws Exception {
		server.stop();
		server.destroy();
	}
	

	@Test
	public void testHelloWorld() throws Exception {
		getAndCompare(endPoint, TikaResource.GREETING, "text/plain",
				"text/plain", 200);
	}

	@Test
	public void testSimpleWord() throws Exception {
		String responseMsg = putAndGetString(endPoint,
				ClassLoader
						.getSystemResourceAsStream(TikaResourceTest.TEST_DOC));

		assertTrue(responseMsg.contains("test"));
	}

	@Test
	public void testApplicationWadl() throws HttpException, IOException {
		String serviceWadl = endPoint + "/application.wadl";
		String resp = getAndReturnResp(serviceWadl);
		assertTrue(resp.length() > 0);
	}

	@Test
	public void testPasswordXLS() throws Exception {
		putAndCheckStatus(endPoint,
				ClassLoader.getSystemResourceAsStream("password.xls"),
				UNPROCESSEABLE);
	}
}
