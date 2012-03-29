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

import org.apache.commons.compress.archivers.ArchiveInputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.archivers.zip.ZipArchiveInputStream;
import org.apache.cxf.binding.BindingFactoryManager;
import org.apache.cxf.endpoint.Server;
import org.apache.cxf.jaxrs.JAXRSBindingFactory;
import org.apache.cxf.jaxrs.JAXRSServerFactoryBean;
import org.apache.cxf.jaxrs.client.WebClient;
import org.apache.cxf.jaxrs.lifecycle.SingletonResourceProvider;
import org.junit.Test;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javax.ws.rs.core.Response;

public class UnpackerResourceTest extends CXFTestBase {
	private static final String UNPACKER_PATH = "/unpacker";
	private static final String ALL_PATH = "/all";

	private static final String endPoint = "http://localhost:"
			+ TikaServerCli.DEFAULT_PORT;

	private static final String TEST_DOC_WAV = "Doc1_ole.doc";
	private static final String WAV1_MD5 = "bdd0a78a54968e362445364f95d8dc96";
	private static final String WAV1_NAME = "_1310388059/MSj00974840000[1].wav";
	private static final String WAV2_MD5 = "3bbd42fb1ac0e46a95350285f16d9596";
	private static final String WAV2_NAME = "_1310388058/MSj00748450000[1].wav";
	private static final String JPG_NAME = "image1.jpg";
	private static final String XSL_IMAGE1_MD5 = "68ead8f4995a3555f48a2f738b2b0c3d";
	private static final String JPG_MD5 = XSL_IMAGE1_MD5;
	private static final String JPG2_NAME = "image2.jpg";
	private static final String JPG2_MD5 = "b27a41d12c646d7fc4f3826cf8183c68";
	private static final String TEST_DOCX_IMAGE = "2pic.docx";
	private static final String DOCX_IMAGE1_MD5 = "5516590467b069fa59397432677bad4d";
	private static final String DOCX_IMAGE2_MD5 = "a5dd81567427070ce0a2ff3e3ef13a4c";
	private static final String DOCX_IMAGE1_NAME = "image1.jpeg";
	private static final String DOCX_IMAGE2_NAME = "image2.jpeg";
	private static final String DOCX_EXE1_MD5 = "d71ffa0623014df725f8fd2710de4411";
	private static final String DOCX_EXE1_NAME = "GMapTool.exe";
	private static final String DOCX_EXE2_MD5 = "2485435c7c22d35f2de9b4c98c0c2e1a";
	private static final String DOCX_EXE2_NAME = "Setup.exe";
	private static final String XSL_IMAGE2_MD5 = "8969288f4245120e7c3870287cce0ff3";
	private static final String APPLICATION_MSWORD = "application/msword";
	private static final String APPLICATION_XML = "application/xml";
	private static final String CONTENT_TYPE = "Content-type";

	private Server server;

	/*
	 * (non-Javadoc)
	 * 
	 * @see junit.framework.TestCase#setUp()
	 */
	@Override
	protected void setUp() throws Exception {
		JAXRSServerFactoryBean sf = new JAXRSServerFactoryBean();
		List providers = new ArrayList();
		providers.add(new TarWriter());
		providers.add(new ZipWriter());
                providers.add(new TikaExceptionMapper());
    		sf.setProviders(providers);
		sf.setResourceClasses(UnpackerResource.class);
		sf.setResourceProvider(UnpackerResource.class,
				new SingletonResourceProvider(new UnpackerResource()));
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
	public void testDocWAV() throws Exception {
		Response response = WebClient.create(endPoint + UNPACKER_PATH)
				.type(APPLICATION_MSWORD).accept("application/zip")
				.put(ClassLoader.getSystemResourceAsStream(TEST_DOC_WAV));

		ArchiveInputStream zip = new ZipArchiveInputStream(
				(InputStream) response.getEntity());

		Map<String, String> data = readArchive(zip);
		assertEquals(WAV1_MD5, data.get(WAV1_NAME));
		assertEquals(WAV2_MD5, data.get(WAV2_NAME));
		assertFalse(data.containsKey(UnpackerResource.TEXT_FILENAME));
	}

	@Test
	public void testDocWAVText() throws Exception {
		Response response = WebClient.create(endPoint + ALL_PATH)
				.type(APPLICATION_MSWORD).accept("application/zip")
				.put(ClassLoader.getSystemResourceAsStream(TEST_DOC_WAV));

		ArchiveInputStream zip = new ZipArchiveInputStream(
				(InputStream) response.getEntity());

		Map<String, String> data = readArchive(zip);
		assertEquals(WAV1_MD5, data.get(WAV1_NAME));
		assertEquals(WAV2_MD5, data.get(WAV2_NAME));
		assertTrue(data.containsKey(UnpackerResource.TEXT_FILENAME));
	}

	@Test
	public void testDocPicture() throws Exception {
		Response response = WebClient.create(endPoint + UNPACKER_PATH)
				.type(APPLICATION_MSWORD).accept("application/zip")
				.put(ClassLoader.getSystemResourceAsStream(TEST_DOC_WAV));

		ZipArchiveInputStream zip = new ZipArchiveInputStream(
				(InputStream) response.getEntity());
		Map<String, String> data = readArchive(zip);

		assertEquals(JPG_MD5, data.get(JPG_NAME));
	}

	@Test
	public void testDocPictureNoOle() throws Exception {
		Response response = WebClient.create(endPoint + UNPACKER_PATH)
				.type(APPLICATION_MSWORD).accept("application/zip")
				.put(ClassLoader.getSystemResourceAsStream("2pic.doc"));

		ZipArchiveInputStream zip = new ZipArchiveInputStream(
				(InputStream) response.getEntity());

		Map<String, String> data = readArchive(zip);
		assertEquals(JPG2_MD5, data.get(JPG2_NAME));
	}

	@Test
	public void testImageDOCX() throws Exception {
		Response response = WebClient.create(endPoint + UNPACKER_PATH)
		.accept("application/zip").put(
				ClassLoader.getSystemResourceAsStream(TEST_DOCX_IMAGE));

		ZipArchiveInputStream zip = new ZipArchiveInputStream(
				(InputStream) response.getEntity());

		Map<String, String> data = readArchive(zip);
		assertEquals(DOCX_IMAGE1_MD5, data.get(DOCX_IMAGE1_NAME));
		assertEquals(DOCX_IMAGE2_MD5, data.get(DOCX_IMAGE2_NAME));
	}

	@Test
	public void test415() throws Exception {
		Response response = WebClient.create(endPoint + UNPACKER_PATH)
				.type("xxx/xxx")
				.accept("*/*")
				.put(ClassLoader.getSystemResourceAsStream(TEST_DOC_WAV));

		assertEquals(415, response.getStatus());
	}

	@Test
	public void testExeDOCX() throws Exception {
		String TEST_DOCX_EXE = "2exe.docx";
		Response response = WebClient.create(endPoint + UNPACKER_PATH)
				.accept("application/zip")
				.put(ClassLoader.getSystemResourceAsStream(TEST_DOCX_EXE));

		ZipArchiveInputStream zip = new ZipArchiveInputStream(
				(InputStream) response.getEntity());

		Map<String, String> data = readArchive(zip);

		assertEquals(DOCX_EXE1_MD5, data.get(DOCX_EXE1_NAME));
		assertEquals(DOCX_EXE2_MD5, data.get(DOCX_EXE2_NAME));
	}

	@Test
	public void testImageXSL() throws Exception {
		Response response = WebClient.create(endPoint + UNPACKER_PATH)
				.accept("application/zip")
				.put(ClassLoader.getSystemResourceAsStream("pic.xls"));

		ZipArchiveInputStream zip = new ZipArchiveInputStream(
				(InputStream) response.getEntity());

		Map<String, String> data = readArchive(zip);
		assertEquals(XSL_IMAGE1_MD5, data.get("0.jpg"));
		assertEquals(XSL_IMAGE2_MD5, data.get("1.jpg"));
	}

	@Test
	public void testTarDocPicture() throws Exception {
		Response response = WebClient.create(endPoint + UNPACKER_PATH)
				.type(APPLICATION_MSWORD).accept("application/x-tar")
				.put(ClassLoader.getSystemResourceAsStream(TEST_DOC_WAV));

		Map<String, String> data = readArchive(new TarArchiveInputStream(
				(InputStream) response.getEntity()));

		assertEquals(JPG_MD5, data.get(JPG_NAME));
	}

	@Test
	public void testText() throws Exception {
		Response response = WebClient.create(endPoint + ALL_PATH)
				.header(CONTENT_TYPE, APPLICATION_XML)
				.accept("application/zip")
				.put(ClassLoader.getSystemResourceAsStream("test.doc"));

		String responseMsg = readArchiveText(new ZipArchiveInputStream(
				(InputStream) response.getEntity()));
		assertNotNull(responseMsg);
		assertTrue(responseMsg.contains("test"));
	}

}
