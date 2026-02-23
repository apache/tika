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
package org.apache.tika.parser.jina;

import java.io.Serializable;

import org.apache.tika.exception.TikaConfigException;

/**
 * Configuration for {@link JinaReaderParser}.
 * <p>
 * Sends PDF (base64-encoded) or HTML (raw string) content to the
 * <a href="https://jina.ai/reader/">Jina Reader API</a> and receives
 * back clean markdown, which is then converted to XHTML.
 */
public class JinaReaderConfig implements Serializable {

    private static final long serialVersionUID = 1L;

    /** Jina Reader API endpoint. */
    private String baseUrl = "https://r.jina.ai/";

    /** Bearer token for the Jina Reader API. */
    private String apiKey = "";

    /** HTTP timeout in seconds. Jina Reader is a remote service; default is generous. */
    private int timeoutSeconds = 120;

    /**
     * Response format requested from Jina Reader.
     * Valid values: {@code markdown}, {@code html}, {@code text}, {@code screenshot}.
     * Default is {@code markdown} since we convert it to XHTML.
     */
    private String returnFormat = "markdown";

    // ---- getters / setters ------------------------------------------------

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) throws TikaConfigException {
        this.baseUrl = baseUrl;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) throws TikaConfigException {
        this.apiKey = apiKey;
    }

    public int getTimeoutSeconds() {
        return timeoutSeconds;
    }

    public void setTimeoutSeconds(int timeoutSeconds) {
        this.timeoutSeconds = timeoutSeconds;
    }

    public String getReturnFormat() {
        return returnFormat;
    }

    public void setReturnFormat(String returnFormat) {
        this.returnFormat = returnFormat;
    }
}
