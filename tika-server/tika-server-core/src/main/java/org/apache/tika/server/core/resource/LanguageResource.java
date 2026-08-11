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
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.Locale;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.tika.language.detect.LanguageDetector;
import org.apache.tika.language.detect.LanguageResult;

@Path("/language")
public class LanguageResource {
    private static final Logger LOG = LoggerFactory.getLogger(LanguageResource.class);

    // TIKA-4510: handle @PUT and @POST separately to avoid nondeterministic failures
    @PUT
    @Consumes("*/*")
    @Produces("text/plain")
    public String detectPut(final InputStream is) throws IOException {
        return detectStream(is);
    }

    @POST
    @Consumes("*/*")
    @Produces("text/plain")
    public String detectPost(final InputStream is) throws IOException {
        return detectStream(is);
    }

    /**
     * Detection accuracy saturates within the first few thousand characters, so anything
     * past this only buys work whose size the caller chooses. Input beyond it is ignored
     * rather than rejected: the answer is the same either way, and rejecting would break
     * callers who legitimately post whole documents.
     * <p>
     * This bounds the detection, not the request. This endpoint holds the text in the
     * server's own heap instead of a pipes child, so a large enough body still costs memory
     * before this class sees it; bounding the body itself is maxRequestSizeBytes' job.
     */
    public static final int MAX_DETECT_CHARS = 100_000;

    private String detectStream(InputStream is) throws IOException {
        return detectString(readAtMost(is, MAX_DETECT_CHARS));
    }

    /** Reads up to maxChars without materializing the rest of the stream. */
    private static String readAtMost(InputStream is, int maxChars) throws IOException {
        Reader reader = new InputStreamReader(is, UTF_8);
        char[] buffer = new char[Math.min(maxChars, 8192)];
        StringBuilder sb = new StringBuilder();
        int read;
        while (sb.length() < maxChars
                && (read = reader.read(buffer, 0, Math.min(buffer.length, maxChars - sb.length()))) != -1) {
            sb.append(buffer, 0, read);
        }
        return sb.toString();
    }

    private String detectString(String string) throws IOException {
        String text = string;
        if (text != null && text.length() > MAX_DETECT_CHARS) {
            LOG.debug("truncating {} chars to {} for language detection", text.length(), MAX_DETECT_CHARS);
            text = text.substring(0, MAX_DETECT_CHARS);
        }
        LanguageResult language = LanguageDetector.getDefaultLanguageDetector()
                .loadModels()
                .detect(text);
        String detectedLang = toIso1(language.getLanguage());
        LOG.debug("Detecting language for incoming resource: [{}]", detectedLang);
        return detectedLang;
    }

    /**
     * Normalize a detected language code to ISO 639-1 for backward compatibility
     * with existing API consumers. Falls back to the original code (which may be
     * ISO 639-3) for languages with no ISO 639-1 equivalent.
     */
    static String toIso1(String lang) {
        if (lang == null || lang.length() != 3) {
            return lang;
        }
        for (String code : Locale.getISOLanguages()) {
            if (new Locale(code).getISO3Language().equals(lang)) {
                return code;
            }
        }
        return lang;
    }
}
