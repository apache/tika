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

package org.apache.tika.server.core.resource;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.IOException;
import java.io.InputStream;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.tika.langdetect.optimaize.OptimaizeLangDetector;
import org.apache.tika.language.detect.LanguageResult;

@Path("/language")
public class LanguageResource {
    private static final Logger LOG = LoggerFactory.getLogger(LanguageResource.class);

    @PUT
    @POST
    @Path("/stream")
    @Consumes("*/*")
    @Produces("text/plain")
    public String detect(final InputStream is) throws IOException {
        String fileTxt = IOUtils.toString(is, UTF_8);
        LanguageResult language = new OptimaizeLangDetector()
                .loadModels()
                .detect(fileTxt);
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
        LanguageResult language = new OptimaizeLangDetector()
                .loadModels()
                .detect(string);
        String detectedLang = language.getLanguage();
        LOG.info("Detecting language for incoming resource: [{}]", detectedLang);
        return detectedLang;
    }

}
