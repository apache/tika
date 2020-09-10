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

package org.apache.tika.server.api.impl;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.IOException;
import java.io.InputStream;

import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;

import org.apache.commons.io.IOUtils;
//TODO: don't hardcode optimaize
import org.apache.tika.langdetect.optimaize.OptimaizeLangDetector;
import org.apache.tika.language.detect.LanguageResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.tika.server.api.LanguageResourceApi;

/**
 * Tika JAX-RS Server
 *
 * <p>The Tika server implements [JAX-RS](http://en.wikipedia.org/wiki/JAX-RS) (Java API for RESTful Web Services) to provide web services according to the Representational State Transfer (REST) architectural style. This facilitates a wide varity oif operations and flexibility with regards to both client and server implementations. The officially supported Tika server implementation is packaged using the OpenAPI [jaxrs-cxf generator](https://openapi-generator.tech/docs/generators/jaxrs-cxf]. This work was tracked through [TIKA-3082](https://issues.apache.org/jira/browse/TIKA-3082). <b>N.B.</b> the OpenAPI version always tracks the underlying Tika version to remove uncertainty about which version of Tika is running within the server.
 *
 */
public class LanguageResourceApiServiceImpl implements LanguageResourceApi {
	private static final Logger LOG = LoggerFactory.getLogger(LanguageResourceApi.class);

	@PUT
	@POST
	@Path("/stream")
	@Consumes("*/*")
	@Produces("text/plain")
	public String detect(final InputStream is) throws IOException {
		String fileTxt = IOUtils.toString(is, UTF_8);
		LanguageResult language = new OptimaizeLangDetector().loadModels().detect(fileTxt);
		String detectedLang = language.getLanguage();
		LOG.info("Detecting language for incoming resource: [{}]", detectedLang);
		return detectedLang;
	}

	@PUT
	@POST
	@Path("/string")
	@Consumes("*/*")
	@Produces("text/plain")
	public String detect(final String string) throws IOException {
		LanguageResult language = new OptimaizeLangDetector().loadModels().detect(string);
		String detectedLang = language.getLanguage();
		LOG.info("Detecting language for incoming resource: [{}]", detectedLang);
		return detectedLang;
	}

    /**
     * POST a UTF-8 text file to the LanguageIdentifier to identify its language.
     *
     * POST a UTF-8 text file to the LanguageIdentifier to identify its language. &lt;b&gt;NOTE&lt;/b&gt;: This endpoint does not parse files.  It runs detection on a UTF-8 string.
     *
     */
    @Override
    public String postLanguageStream() {
        // TODO: Implement...
        return null;
    }
    
    /**
     * POST a text string to the LanguageIdentifier to identify its language.
     *
     * POST a text string to the LanguageIdentifier to identify its language.
     *
     */
    @Override
    public String postLanguageString() {
        // TODO: Implement...
        return null;
    }
    
    /**
     * PUT a UTF-8 text file to the LanguageIdentifier to identify its language.
     *
     * POST a UTF-8 text file to the LanguageIdentifier to identify its language. &lt;b&gt;NOTE&lt;/b&gt;: This endpoint does not parse files.  It runs detection on a UTF-8 string.
     *
     */
    @Override
    public String putLanguageStream() {
        // TODO: Implement...
        
        return null;
    }
    
    /**
     * PUT a text string to the LanguageIdentifier to identify its language.
     *
     * PUT a text string to the LanguageIdentifier to identify its language.
     *
     */
    @Override
    public String putLanguageString() {
        // TODO: Implement...
        
        return null;
    }
    
}

