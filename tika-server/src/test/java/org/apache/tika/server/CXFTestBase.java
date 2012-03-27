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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;

import javax.ws.rs.core.HttpHeaders;

import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.compress.archivers.ArchiveEntry;
import org.apache.commons.compress.archivers.ArchiveInputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.archivers.zip.ZipArchiveInputStream;
import org.apache.commons.compress.utils.IOUtils;
import org.apache.commons.httpclient.Header;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpException;
import org.apache.commons.httpclient.methods.GetMethod;
import org.apache.commons.httpclient.methods.PutMethod;
import org.apache.cxf.io.CachedOutputStream;
import org.apache.tika.io.CloseShieldInputStream;

import au.com.bytecode.opencsv.CSVReader;
import junit.framework.TestCase;

public class CXFTestBase extends TestCase {

	protected Map<String, String> putAndGetMet(String address,
			InputStream content) throws Exception {
		Map<String, String> met = new HashMap<String, String>();
		PutMethod put = new PutMethod(address);
		put.setRequestBody(content);
		HttpClient httpClient = new HttpClient();
		InputStreamReader reader = null;

		try {
			httpClient.executeMethod(put);
			CSVReader csvReader = new CSVReader(new InputStreamReader(
					put.getResponseBodyAsStream()));

			String[] nextLine;
			while ((nextLine = csvReader.readNext()) != null) {
				met.put(nextLine[0], nextLine[1]);
			}

		} finally {
			put.releaseConnection();
		}

		return met;
	}

	protected String getAndReturnResp(String address) throws HttpException,
			IOException {
		String resp = null;
		GetMethod get = new GetMethod(address);
		HttpClient httpClient = new HttpClient();
		InputStreamReader reader = null;

		try {
			httpClient.executeMethod(get);
			resp = get.getResponseBodyAsString();
		} finally {
			get.releaseConnection();
		}

		return resp;
	}

	protected void putAndCheckStatus(String address, InputStream content,
			int expectedStatus) throws Exception {
		putAndCheckStatus(address, null, content, expectedStatus);
	}
	
	protected void putAndCheckStatus(String address, String contentType, InputStream content,
			int expectedStatus) throws Exception {
		PutMethod put = new PutMethod(address);
		put.setRequestBody(content);
		if(contentType != null) 
			put.setRequestHeader(HttpHeaders.CONTENT_TYPE, contentType);
		HttpClient httpClient = new HttpClient();
		try {
			int result = httpClient.executeMethod(put);
			assertEquals(expectedStatus, result);
		} finally {
			put.releaseConnection();
		}
	}

	protected String putAndGetString(String address, InputStream content)
			throws Exception {
		String resp = null;
		PutMethod put = new PutMethod(address);
		put.setRequestBody(content);
		HttpClient httpClient = new HttpClient();
		InputStreamReader reader = null;

		try {
			httpClient.executeMethod(put);
			resp = put.getResponseBodyAsString();
		} finally {
			put.releaseConnection();
		}

		return resp;
	}

	protected Map<String,String> putAndGetMapData(String address,
			InputStream content, boolean zip) throws Exception {
		PutMethod put = new PutMethod(address);
		put.setRequestBody(content);
		HttpClient httpClient = new HttpClient();
        Map<String,String> data = new HashMap<String, String>();
		
		try {
			httpClient.executeMethod(put);
			data = readArchive(zip ? 
					new ZipArchiveInputStream(put.getResponseBodyAsStream()):
						new TarArchiveInputStream(put.getResponseBodyAsStream()));
		} finally {
			put.releaseConnection();
		}

		return data;
	}
	
	protected String putAndGetArchiveText(String address,
			InputStream content, boolean zip) throws Exception {
		PutMethod put = new PutMethod(address);
		put.setRequestBody(content);
		HttpClient httpClient = new HttpClient();
        String archiveText = null;
		
		try {
			httpClient.executeMethod(put);
			archiveText = readArchiveText(zip ? 
					new ZipArchiveInputStream(put.getResponseBodyAsStream()):
						new TarArchiveInputStream(put.getResponseBodyAsStream()));
		} finally {
			//put.releaseConnection();
		}

		return archiveText;
	}	

	protected void getAndCompare(String address, String expectedValue,
			String acceptType, String expectedContentType, int expectedStatus)
			throws Exception {
		GetMethod get = new GetMethod(address);
		get.setRequestHeader("Accept", acceptType);
		get.setRequestHeader("Accept-Language", "da;q=0.8,en");
		HttpClient httpClient = new HttpClient();
		try {
			int result = httpClient.executeMethod(get);
			assertEquals(expectedStatus, result);
			String content = getStringFromInputStream(get
					.getResponseBodyAsStream());
			assertEquals("Expected value is wrong", expectedValue, content);
			if (expectedContentType != null) {
				Header ct = get.getResponseHeader("Content-Type");
				assertEquals("Wrong type of response", expectedContentType,
						ct.getValue());
			}
		} finally {
			get.releaseConnection();
		}
	}

	protected String getStringFromInputStream(InputStream in) throws Exception {
		CachedOutputStream bos = new CachedOutputStream();
		IOUtils.copy(in, bos);
		in.close();
		bos.close();
		return bos.getOut().toString();
	}

	protected InputStream cloneInputStream(InputStream is) throws IOException {
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		// Fake code simulating the copy
		// You can generally do better with nio if you need...
		// And please, unlike me, do something about the Exceptions :D
		byte[] buffer = new byte[1024];
		int len;
		while ((len = is.read(buffer)) > -1) {
			baos.write(buffer, 0, len);
		}
		baos.flush();
		return new ByteArrayInputStream(baos.toByteArray());

	}
	

	protected Map<String, String> readArchive(ArchiveInputStream zip)
			throws IOException {
		Map<String, String> data = new HashMap<String, String>();

		while (true) {
			ArchiveEntry entry = zip.getNextEntry();

			if (entry == null) {
				break;
			}

			ByteArrayOutputStream bos = new ByteArrayOutputStream();

			IOUtils.copy(zip, bos);

			data.put(entry.getName(), DigestUtils.md5Hex(bos.toByteArray()));
		}

		return data;
	}

	protected String readArchiveText(ArchiveInputStream zip)
			throws IOException {
		while (true) {
			ArchiveEntry entry = zip.getNextEntry();

			if (entry == null) {
				break;
			}

			if (!entry.getName().equals(UnpackerResource.TEXT_FILENAME)) {
				continue;
			}

			ByteArrayOutputStream bos = new ByteArrayOutputStream();

			IOUtils.copy(zip, bos);

			return bos.toString("UTF-8");
		}

		return null;
	}	

}
