/**
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

package org.apache.tika.language.translate;

import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Properties;
import java.util.logging.Logger;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.cxf.jaxrs.client.WebClient;
import org.apache.tika.exception.TikaException;
import org.apache.tika.io.IOUtils;
import org.apache.tika.language.LanguageIdentifier;
import org.apache.tika.language.LanguageProfile;

/**
 * An implementation of a REST client to the <a
 * href="https://www.googleapis.com/language/translate/v2">Google Translate v2
 * API</a>. Based on the <a
 * href="http://hayageek.com/google-translate-api-tutorial/">great tutorial</a>
 * from <a href="http://hayageek.com">hayageek.com</a>. Set your API key in
 * translator.google.properties.
 * 
 * 
 */
public class GoogleTranslator implements Translator {

	private static final String GOOGLE_TRANSLATE_URL_BASE = "https://www.googleapis.com/language/translate/v2";

	private static final String DEFAULT_KEY = "dummy-secret";

	private static final Logger LOG = Logger.getLogger(GoogleTranslator.class
			.getName());

	private WebClient client;

	private String apiKey;

	private boolean isAvailable;

	public GoogleTranslator() {
		this.client = WebClient.create(GOOGLE_TRANSLATE_URL_BASE);
		this.isAvailable = true;
		Properties config = new Properties();
		try {
			config.load(GoogleTranslator.class
					.getClassLoader()
					.getResourceAsStream(
							"org/apache/tika/language/translate/translator.google.properties"));
			this.apiKey = config.getProperty("translator.client-secret");
			if (this.apiKey.equals(DEFAULT_KEY))
				this.isAvailable = false;
		} catch (Exception e) {
			e.printStackTrace();
			isAvailable = false;
		}
	}

	@Override
	public String translate(String text, String sourceLanguage,
			String targetLanguage) throws TikaException, IOException {
		if (!this.isAvailable)
			return text;
		Response response = client.accept(MediaType.APPLICATION_JSON)
				.query("key", apiKey).query("source", sourceLanguage)
				.query("target", targetLanguage).query("q", text).get();
		BufferedReader reader = new BufferedReader(new InputStreamReader(
				(InputStream) response.getEntity(), IOUtils.UTF_8));
		String line = null;
		StringBuffer responseText = new StringBuffer();
		while ((line = reader.readLine()) != null) {
			responseText.append(line);
		}

		ObjectMapper mapper = new ObjectMapper();
		JsonNode jsonResp = mapper.readTree(responseText.toString());
		return jsonResp.findValuesAsText("translatedText").get(0);
	}

	@Override
	public String translate(String text, String targetLanguage)
			throws TikaException, IOException {
		if (!this.isAvailable)
			return text;
		LanguageIdentifier language = new LanguageIdentifier(
				new LanguageProfile(text));
		String sourceLanguage = language.getLanguage();
		return translate(text, sourceLanguage, targetLanguage);
	}

	@Override
	public boolean isAvailable() {
		return this.isAvailable;
	}

}
