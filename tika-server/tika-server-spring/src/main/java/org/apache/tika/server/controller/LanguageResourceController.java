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
package org.apache.tika.server.controller;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.IOException;
import java.io.InputStream;

import org.apache.commons.io.IOUtils;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import org.apache.tika.langdetect.optimaize.OptimaizeLangDetector;
import org.apache.tika.language.detect.LanguageResult;
import org.apache.tika.server.api.LanguageResourceApi;
import org.apache.tika.server.exception.TikaServerRuntimeException;

/**
 * Controller for language identification services.
 * Handles the /language endpoints for the Language Resource tag.
 */
@RestController
public class LanguageResourceController implements LanguageResourceApi {
    @Override
    public ResponseEntity<String> postLanguageStream(Resource body) {
        try {
            InputStream is = body.getInputStream();
            String fileTxt = IOUtils.toString(is, UTF_8);
            LanguageResult language = new OptimaizeLangDetector()
                    .loadModels()
                    .detect(fileTxt);
            String detectedLang = language.getLanguage();
            return ResponseEntity.ok(detectedLang);
        } catch (IOException e) {
            throw new TikaServerRuntimeException(e);
        }
    }

    @Override
    public ResponseEntity<String> postLanguageString(String body) {
        LanguageResult language = new OptimaizeLangDetector()
                .loadModels()
                .detect(body);
        String detectedLang = language.getLanguage();
        return ResponseEntity.ok(detectedLang);
    }

    @Override
    public ResponseEntity<String> putLanguageStream(Resource body) {
        return postLanguageStream(body);
    }

    @Override
    public ResponseEntity<String> putLanguageString(String body) {
        return postLanguageString(body);
    }
}
