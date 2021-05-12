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
package org.apache.tika.server.standard.config;

import static org.apache.tika.server.core.resource.TikaResource.processHeaderConfig;

import java.util.List;
import java.util.Map;
import javax.ws.rs.core.MultivaluedMap;

import org.apache.commons.lang3.StringUtils;

import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.pdf.PDFParserConfig;
import org.apache.tika.server.core.ParseContextConfig;

/**
 * PDF parser configuration, for the request
 */
public class PDFServerConfig implements ParseContextConfig {

    /**
     * The HTTP header prefix required (case-insensitive) by this config.
     */
    public static final String X_TIKA_PDF_HEADER_PREFIX = "X-Tika-PDF";

    /**
     * Configures the parseContext with present headers.
     * Note: only first value of header is considered.
     *
     * @param httpHeaders  the headers.
     * @param metadata     the metadata.
     * @param parseContext the parse context to configure.
     */
    @Override
    public void configure(MultivaluedMap<String, String> httpHeaders, Metadata metadata,
                          ParseContext parseContext) {
        //lazily initialize configs
        //if a header is submitted, any params set in --tika-config tika-config.xml
        //upon server startup will be ignored.
        PDFParserConfig pdfParserConfig = null;
        for (Map.Entry<String, List<String>> kvp : httpHeaders.entrySet()) {
            if (StringUtils.startsWithIgnoreCase(kvp.getKey(), X_TIKA_PDF_HEADER_PREFIX)) {
                pdfParserConfig =
                        (pdfParserConfig == null) ? new PDFParserConfig() : pdfParserConfig;
                processHeaderConfig(pdfParserConfig, kvp.getKey(), kvp.getValue().get(0).trim(),
                        X_TIKA_PDF_HEADER_PREFIX);
            }
        }
        if (pdfParserConfig != null) {
            parseContext.set(PDFParserConfig.class, pdfParserConfig);
        }
    }

}
