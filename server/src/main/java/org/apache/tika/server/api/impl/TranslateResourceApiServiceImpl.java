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
import java.util.List;

import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response;

import org.apache.commons.io.IOUtils;
import org.apache.tika.config.LoadErrorHandler;
import org.apache.tika.config.ServiceLoader;
import org.apache.tika.exception.TikaException;
import org.apache.tika.langdetect.optimaize.OptimaizeLangDetector;
import org.apache.tika.language.detect.LanguageResult;
import org.apache.tika.language.translate.Translator;
import org.apache.tika.server.ServerStatus;
import org.apache.tika.server.api.TranslateResourceApi;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * Tika JAX-RS Server
 *
 * <p>The Tika server implements [JAX-RS](http://en.wikipedia.org/wiki/JAX-RS) (Java API for RESTful Web Services) to provide web services according to the Representational State Transfer (REST) architectural style. This facilitates a wide varity oif operations and flexibility with regards to both client and server implementations. The officially supported Tika server implementation is packaged using the OpenAPI [jaxrs-cxf generator](https://openapi-generator.tech/docs/generators/jaxrs-cxf]. This work was tracked through [TIKA-3082](https://issues.apache.org/jira/browse/TIKA-3082). <b>N.B.</b> the OpenAPI version always tracks the underlying Tika version to remove uncertainty about which version of Tika is running within the server.
 *
 */
public class TranslateResourceApiServiceImpl implements TranslateResourceApi {
    private Translator defaultTranslator;

    private ServiceLoader loader;

    private static final Logger LOG = LoggerFactory.getLogger(TranslateResourceApi.class);

    private final ServerStatus serverStatus;
    public TranslateResourceApiServiceImpl(ServerStatus serverStatus) {
        this.loader = new ServiceLoader(ServiceLoader.class.getClassLoader(),
                LoadErrorHandler.WARN);
        this.defaultTranslator = TikaResourceApiServiceImpl.getConfig().getTranslator();
        this.serverStatus = serverStatus;
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
        return doTranslate(IOUtils.toString(is, UTF_8), translator, sLang, dLang);

    }

    @PUT
    @POST
    @Path("/all/{translator}/{dest}")
    @Consumes("*/*")
    @Produces("text/plain")
    public String autoTranslate(final InputStream is,
                                @PathParam("translator") String translator,
                                @PathParam("dest") String dLang) throws TikaException, IOException {
        final String content = IOUtils.toString(is, UTF_8);
        LanguageResult language = new OptimaizeLangDetector().loadModels().detect(content);
        if (language.isUnknown()) {
            throw new TikaException("Unable to detect language to use for translation of text");
        }

        String sLang = language.getLanguage();
        LOG.info("LanguageIdentifier: detected source lang: [{}]", sLang);
        return doTranslate(content, translator, sLang, dLang);
    }

    private String doTranslate(String content, String translator, String sLang,
                               String dLang) throws TikaException, IOException {
        LOG.info("Using translator: [{}]: src: [{}]: dest: [{}]", translator, sLang, dLang);
        Translator translate = byClassName(translator);
        if (translate == null) {
            translate = this.defaultTranslator;
            LOG.info("Using default translator");
        }
        TikaResourceApiServiceImpl.checkIsOperating();
        long taskId = serverStatus.start(ServerStatus.TASK.TRANSLATE, null);
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
        List<Translator> translators = loader
                .loadStaticServiceProviders(Translator.class);
        for (Translator t : translators) {
            if (t.getClass().getName().equals(className)) {
                return t;
            }
        }
        return null;
    }

    /**
     * POST a document and auto-detects the *src* language and translates to *dest*
     *
     * POST a document and translates from the *src* language to the *dest*. &lt;b&gt;NOTE&lt;/b&gt;:  *translator* should be a fully qualified Tika class name (with package) and *dest* should be the 2 character short code for the source language.
     *
     */
    public String postTranslateAllSrcDest() {
        // TODO: Implement...
        
        return null;
    }
    
    /**
     * POST a document and translates from the *src* language to the *dest*
     *
     * POST a document and translates from the *src* language to the *dest*. &lt;b&gt;NOTE&lt;/b&gt;:  *translator* should be a fully qualified Tika class name (with package), *src* and *dest* should be the 2 character short code for the source language and dest language respectively.
     *
     */
    public String postTranslateAllTranslatorSrcDest() {
        // TODO: Implement...
        
        return null;
    }
    
    /**
     * PUT a document and auto-detects the *src* language and translates to *dest*
     *
     * PUT a document and translates from the *src* language to the *dest*. &lt;b&gt;NOTE&lt;/b&gt;:  *translator* should be a fully qualified Tika class name (with package) and *dest* should be the 2 character short code for the source language.
     *
     */
    public String putTranslateAllSrcDest() {
        // TODO: Implement...
        
        return null;
    }
    
    /**
     * PUT a document and translates from the *src* language to the *dest*
     *
     * PUT a document and translates from the *src* language to the *dest*. &lt;b&gt;NOTE&lt;/b&gt;:  *translator* should be a fully qualified Tika class name (with package), *src* and *dest* should be the 2 character short code for the source language and dest language respectively.
     *
     */
    public String putTranslateAllTranslatorSrcDest() {
        // TODO: Implement...
        
        return null;
    }
    
}

