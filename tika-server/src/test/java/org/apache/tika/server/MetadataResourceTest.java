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

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.HashMap;
import java.util.Map;

import javax.ws.rs.core.Response;

import org.apache.cxf.binding.BindingFactoryManager;
import org.apache.cxf.endpoint.Server;
import org.apache.cxf.jaxrs.JAXRSBindingFactory;
import org.apache.cxf.jaxrs.JAXRSServerFactoryBean;
import org.apache.cxf.jaxrs.client.WebClient;
import org.apache.cxf.jaxrs.lifecycle.SingletonResourceProvider;
import org.junit.Test;

import au.com.bytecode.opencsv.CSVReader;

public class MetadataResourceTest extends CXFTestBase {
	private static final String META_PATH = "/meta";

	private static final String endPoint = "http://localhost:"
			+ TikaServerCli.DEFAULT_PORT;

	private Server server;

	/*
	 * (non-Javadoc)
	 * 
	 * @see junit.framework.TestCase#setUp()
	 */
	@Override
	protected void setUp() throws Exception {
		JAXRSServerFactoryBean sf = new JAXRSServerFactoryBean();
		sf.setResourceClasses(MetadataResource.class);
		sf.setResourceProvider(MetadataResource.class,
				new SingletonResourceProvider(new MetadataResource()));
		sf.setAddress(endPoint + "/");
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
	public void testSimpleWord() throws Exception {
		Response response = WebClient
				.create(endPoint + META_PATH)
				.type("application/msword")
				.accept("text/csv")
				.put(ClassLoader
						.getSystemResourceAsStream(TikaResourceTest.TEST_DOC));

		Reader reader = new InputStreamReader(
				(InputStream) response.getEntity());

		CSVReader csvReader = new CSVReader(reader);

		Map<String, String> metadata = new HashMap<String, String>();

		String[] nextLine;
		while ((nextLine = csvReader.readNext()) != null) {
			metadata.put(nextLine[0], nextLine[1]);
		}

		assertNotNull(metadata.get("Author"));
		assertEquals("Maxim Valyanskiy", metadata.get("Author"));
	}

}
