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
package org.apache.tika.server.classic.config;

import org.apache.commons.lang3.StringUtils;
import org.apache.tika.exception.TikaException;
import org.apache.tika.extractor.DocumentSelector;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.ocr.TesseractOCRConfig;
import org.apache.tika.server.core.ParseContextConfig;

import javax.ws.rs.core.MultivaluedMap;

import static org.apache.tika.server.core.resource.TikaResource.processHeaderConfig;

public class TesseractServerConfig implements ParseContextConfig {

    public static final String X_TIKA_OCR_HEADER_PREFIX = "X-Tika-OCR";
    @Override
    public void configure(MultivaluedMap<String, String> httpHeaders,
                          Metadata metadata, ParseContext parseContext) {
        //lazily initialize configs
        //if a header is submitted, any params set in --tika-config tika-config.xml
        //upon server startup will be ignored.
        TesseractOCRConfig ocrConfig = null;
        DocumentSelector documentSelector = null;
        for (String key : httpHeaders.keySet()) {
            if (StringUtils.startsWithIgnoreCase(key, X_TIKA_OCR_HEADER_PREFIX)) {
                ocrConfig = (ocrConfig == null) ? new TesseractOCRConfig() : ocrConfig;
                processHeaderConfig(httpHeaders, ocrConfig, key, X_TIKA_OCR_HEADER_PREFIX);
            }
        }
        if (ocrConfig != null) {
            parseContext.set(TesseractOCRConfig.class, ocrConfig);
        }
    }
}
