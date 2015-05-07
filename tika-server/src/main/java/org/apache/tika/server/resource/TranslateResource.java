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

package org.apache.tika.server.resource;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;

import org.apache.commons.io.IOUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.tika.config.LoadErrorHandler;
import org.apache.tika.config.ServiceLoader;
import org.apache.tika.config.TikaConfig;
import org.apache.tika.exception.TikaException;
import org.apache.tika.language.LanguageIdentifier;
import org.apache.tika.language.LanguageProfile;
import org.apache.tika.language.translate.Translator;

@Path("/translate")
public class TranslateResource {

	private Translator defaultTranslator;

	private ServiceLoader loader;

	private static final Log logger = LogFactory.getLog(TranslateResource.class
			.getName());

	private TikaConfig config;

	public TranslateResource(TikaConfig config) {
		this.config = config;
		this.loader = new ServiceLoader(ServiceLoader.class.getClassLoader(),
				LoadErrorHandler.WARN);
		this.defaultTranslator = this.config.getTranslator();
	}

	@PUT
	@POST
	@Path("/all/{translator}/{src}/{dest}")
	@Consumes("*/*")
	@Produces("text/plain")
	public String translate(final InputStream is,
			@PathParam("translator") String translator,
			@PathParam("src") String sLang, @PathParam("dest") String dLang)
			throws TikaException, IOException {
		return doTranslate(IOUtils.toString(is), translator, sLang, dLang);

	}

	@PUT
	@POST
	@Path("/all/{translator}/{dest}")
	@Consumes("*/*")
	@Produces("text/plain")
	public String autoTranslate(final InputStream is,
			@PathParam("translator") String translator,
			@PathParam("dest") String dLang) throws TikaException, IOException {
		final String content = IOUtils.toString(is);
		LanguageIdentifier language = new LanguageIdentifier(
				new LanguageProfile(content));
		String sLang = language.getLanguage();
		logger.info("LanguageIdentifier: detected source lang: [" + sLang + "]");
		return doTranslate(content, translator, sLang, dLang);
	}

	private String doTranslate(String content, String translator, String sLang,
			String dLang) throws TikaException, IOException {
		logger.info("Using translator: [" + translator + "]: src: [" + sLang
				+ "]: dest: [" + dLang + "]");
		Translator translate = byClassName(translator);
		if (translate == null) {
			translate = this.defaultTranslator;
			logger.info("Using default translator");
		}

		return translate.translate(content, sLang, dLang);
	}

	private Translator byClassName(String className) {
		List<Translator> translators = loader
				.loadStaticServiceProviders(Translator.class);
		for (Translator t : translators) {
			if (t.getClass().getName().equals(className)) {
				return t;
			}
		}
		return null;
	}

}
