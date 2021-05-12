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

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Properties;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.apache.cxf.jaxrs.client.WebClient;
import org.apache.cxf.jaxrs.ext.multipart.Attachment;
import org.apache.cxf.jaxrs.ext.multipart.ContentDisposition;
import org.apache.cxf.jaxrs.ext.multipart.MultipartBody;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.ContentHandler;

import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;

public class GrobidRESTParser {

    private static final Logger LOG = LoggerFactory.getLogger(GrobidRESTParser.class);

    private static final String GROBID_REST_HOST = "http://localhost:8080";
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
        Response response = null;
        try {
            response = WebClient.create(readRestUrl() + GROBID_ISALIVE_PATH).get();
            String resp = response.readEntity(String.class);
            return resp != null && !resp.equals("") && resp.startsWith("true");
        } catch (Exception e) {
            //swallow...can't run
            return false;
        }
    }

    public void parse(String filePath, ContentHandler handler, Metadata metadata,
                      ParseContext context) throws FileNotFoundException {

        File pdfFile = new File(filePath);
        ContentDisposition cd = new ContentDisposition(
                "form-data; name=\"input\"; filename=\"" + pdfFile.getName() + "\"");
        Attachment att = new Attachment("input", new FileInputStream(pdfFile), cd);
        MultipartBody body = new MultipartBody(att);

        try {
            checkMode();
            Response response = WebClient.create(restHostUrlStr +
                    (legacyMode ? GROBID_LEGACY_PROCESSHEADER_PATH : GROBID_PROCESSHEADER_PATH))
                    .accept(MediaType.APPLICATION_XML).type(MediaType.MULTIPART_FORM_DATA)
                    .post(body);


            String resp = response.readEntity(String.class);
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
        Response response = WebClient.create(restHostUrlStr + GROBID_ISALIVE_PATH).head();
        if (response.getStatus() == 200) {
            legacyMode = false;
            return;
        }
        response = WebClient.create(restHostUrlStr + GROBID_LEGACY_ISALIVE_PATH).head();
        if (response.getStatus() == 200) {
            legacyMode = true;
            return;
        }
        throw new TikaException("Cannot connect to Grobid Service");
    }

}
