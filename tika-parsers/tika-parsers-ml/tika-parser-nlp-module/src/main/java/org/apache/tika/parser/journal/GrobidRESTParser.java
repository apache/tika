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
package org.apache.tika.parser.journal;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.time.Duration;
import java.util.Properties;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.ContentHandler;

import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;

public class GrobidRESTParser {

    private static final Logger LOG = LoggerFactory.getLogger(GrobidRESTParser.class);

    private static final String GROBID_REST_HOST = "http://localhost:8070";
    private static final String GROBID_ISALIVE_PATH = "/api/isalive";
    private static final String GROBID_PROCESSHEADER_PATH = "/api/processHeaderDocument";
    private static final String GROBID_LEGACY_ISALIVE_PATH = "/grobid";
    private static final String GROBID_LEGACY_PROCESSHEADER_PATH = "/processHeaderDocument";
    private Boolean legacyMode = null;
    private String restHostUrlStr;

    public GrobidRESTParser() {
        String restHostUrlStr = null;
        try {
            restHostUrlStr = readRestUrl();
        } catch (IOException e) {
            LOG.warn("can't read rest url", e);
        }

        if (restHostUrlStr == null || restHostUrlStr.equals("")) {
            this.restHostUrlStr = GROBID_REST_HOST;
        } else {
            this.restHostUrlStr = restHostUrlStr;
        }
    }

    private static String readRestUrl() throws IOException {
        Properties grobidProperties = new Properties();
        grobidProperties
                .load(GrobidRESTParser.class.getResourceAsStream("GrobidExtractor.properties"));

        return grobidProperties.getProperty("grobid.server.url");
    }

    protected static boolean canRun() {
        try {
            String resp = send(request(readRestUrl() + GROBID_ISALIVE_PATH).GET()).body();
            return resp != null && !resp.equals("") && resp.startsWith("true");
        } catch (Exception e) {
            //swallow...can't run
            return false;
        }
    }

    public void parse(String filePath, ContentHandler handler, Metadata metadata,
                      ParseContext context) throws FileNotFoundException {

        File pdfFile = new File(filePath);
        if (!pdfFile.isFile()) {
            throw new FileNotFoundException(filePath);
        }

        try {
            checkMode();
            String boundary = UUID.randomUUID().toString();
            ByteArrayOutputStream body = new ByteArrayOutputStream();
            body.write(("--" + boundary + "\r\n" +
                    "Content-Disposition: form-data; name=\"input\"; filename=\"" +
                    pdfFile.getName() + "\"\r\n" +
                    "Content-Type: application/octet-stream\r\n\r\n").getBytes(UTF_8));
            Files.copy(pdfFile.toPath(), body);
            body.write(("\r\n--" + boundary + "--\r\n").getBytes(UTF_8));
            HttpRequest.Builder request = request(restHostUrlStr +
                    (legacyMode ? GROBID_LEGACY_PROCESSHEADER_PATH : GROBID_PROCESSHEADER_PATH))
                    .header("Accept", "application/xml")
                    .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                    .POST(HttpRequest.BodyPublishers.ofByteArray(body.toByteArray()));

            String resp = send(request).body();
            Metadata teiMet = new TEIDOMParser().parse(resp, context);
            for (String key : teiMet.names()) {
                metadata.add("grobid:header_" + key, teiMet.get(key));
            }
        } catch (Exception e) {
            LOG.warn("Couldn't read response", e);
        }
    }

    private void checkMode() throws TikaException {
        if (legacyMode != null) {
            return;
        }
        if (head(restHostUrlStr + GROBID_ISALIVE_PATH) == 200) {
            legacyMode = false;
            return;
        }
        if (head(restHostUrlStr + GROBID_LEGACY_ISALIVE_PATH) == 200) {
            legacyMode = true;
            return;
        }
        throw new TikaException("Cannot connect to Grobid Service");
    }

    private static int head(String url) throws TikaException {
        try {
            return send(request(url).method("HEAD", HttpRequest.BodyPublishers.noBody()))
                    .statusCode();
        } catch (IOException e) {
            throw new TikaException("Cannot connect to Grobid Service", e);
        }
    }

    private static HttpRequest.Builder request(String url) {
        return HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(60));
    }

    private static HttpResponse<String> send(HttpRequest.Builder request) throws IOException {
        try {
            return ClientHolder.CLIENT.send(request.build(),
                    HttpResponse.BodyHandlers.ofString(UTF_8));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException(e);
        }
    }

    private static class ClientHolder {
        private static final HttpClient CLIENT =
                HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1)
                        .connectTimeout(Duration.ofSeconds(30)).build();
    }

}
