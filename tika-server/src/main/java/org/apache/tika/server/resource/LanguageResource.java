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

import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;

import org.apache.commons.io.IOUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.tika.config.TikaConfig;
import org.apache.tika.language.LanguageIdentifier;
import org.apache.tika.language.LanguageProfile;

import com.google.common.base.Charsets;

@Path("/language")
public class LanguageResource {

	private static final Log logger = LogFactory.getLog(LanguageResource.class
			.getName());

	private TikaConfig config;

	public LanguageResource(TikaConfig config) {
		this.config = config;
	}

	@PUT
	@POST
	@Path("/stream")
	@Consumes("*/*")
	@Produces("text/plain")
	public String detect(final InputStream is) throws IOException {
		// comme çi comme ça
		// this is English!
		String fileTxt = IOUtils.toString(is, Charsets.UTF_8);
		logger.debug("File: " + fileTxt);
		LanguageIdentifier lang = new LanguageIdentifier(new LanguageProfile(
				fileTxt));
		String detectedLang = lang.getLanguage();
		logger.info("Detecting language for incoming resource: ["
				+ detectedLang + "]");
		return detectedLang;
	}

	@PUT
	@POST
	@Path("/string")
	@Consumes("*/*")
	@Produces("text/plain")
	public String detect(final String string) throws IOException {
		logger.debug("String: " + string);
		LanguageIdentifier lang = new LanguageIdentifier(new LanguageProfile(
				string));
		String detectedLang = lang.getLanguage();
		logger.info("Detecting language for incoming resource: ["
				+ detectedLang + "]");
		return detectedLang;
	}

}
