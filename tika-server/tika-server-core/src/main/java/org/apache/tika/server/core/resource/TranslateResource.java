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
import java.util.List;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.tika.config.LoadErrorHandler;
import org.apache.tika.config.ServiceLoader;
import org.apache.tika.exception.TikaException;
import org.apache.tika.langdetect.optimaize.OptimaizeLangDetector;
import org.apache.tika.language.detect.LanguageResult;
import org.apache.tika.language.translate.Translator;
import org.apache.tika.server.core.ServerStatus;

@Path("/translate")
public class TranslateResource {
    private static final Logger LOG = LoggerFactory.getLogger(TranslateResource.class);
    private final ServerStatus serverStatus;
    private final long timeoutMillis;
    private Translator defaultTranslator;
    private ServiceLoader loader;

    public TranslateResource(ServerStatus serverStatus, long timeoutMillis) {
        this.loader = new ServiceLoader(ServiceLoader.class.getClassLoader(), LoadErrorHandler.WARN);
        this.defaultTranslator = TikaResource
                .getConfig()
                .getTranslator();
        this.serverStatus = serverStatus;
        this.timeoutMillis = timeoutMillis;
    }

    @PUT
    @POST
    @Path("/all/{translator}/{src}/{dest}")
    @Consumes("*/*")
    @Produces("text/plain")
    public String translate(final InputStream is, @PathParam("translator") String translator, @PathParam("src") String sLang, @PathParam("dest") String dLang)
            throws TikaException, IOException {
        return doTranslate(IOUtils.toString(is, UTF_8), translator, sLang, dLang);

    }

    @PUT
    @POST
    @Path("/all/{translator}/{dest}")
    @Consumes("*/*")
    @Produces("text/plain")
    public String autoTranslate(final InputStream is, @PathParam("translator") String translator, @PathParam("dest") String dLang) throws TikaException, IOException {
        final String content = IOUtils.toString(is, UTF_8);
        LanguageResult language = new OptimaizeLangDetector()
                .loadModels()
                .detect(content);
        if (language.isUnknown()) {
            throw new TikaException("Unable to detect language to use for translation of text");
        }

        String sLang = language.getLanguage();
        LOG.info("LanguageIdentifier: detected source lang: [{}]", sLang);

        return doTranslate(content, translator, sLang, dLang);
    }

    private String doTranslate(String content, String translator, String sLang, String dLang) throws TikaException, IOException {
        LOG.info("Using translator: [{}]: src: [{}]: dest: [{}]", translator, sLang, dLang);
        Translator translate = byClassName(translator);
        if (translate == null) {
            translate = this.defaultTranslator;
            LOG.info("Using default translator");
        }
        TikaResource.checkIsOperating();
        long taskId = serverStatus.start(ServerStatus.TASK.TRANSLATE, null, timeoutMillis);
        try {
            return translate.translate(content, sLang, dLang);
        } catch (OutOfMemoryError e) {
            serverStatus.setStatus(ServerStatus.STATUS.ERROR);
            throw e;
        } finally {
            serverStatus.complete(taskId);
        }
    }

    private Translator byClassName(String className) {
        List<Translator> translators = loader.loadStaticServiceProviders(Translator.class);
        for (Translator t : translators) {
            if (t
                    .getClass()
                    .getName()
                    .equals(className)) {
                return t;
            }
        }
        return null;
    }
}
