/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.tika.parser.journal;

import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Properties;

import org.apache.cxf.jaxrs.client.WebClient;
import org.apache.cxf.jaxrs.ext.multipart.Attachment;
import org.apache.cxf.jaxrs.ext.multipart.ContentDisposition;
import org.apache.cxf.jaxrs.ext.multipart.MultipartBody;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.ContentHandler;

public class GrobidRESTParser {

    private static final Logger LOG = LoggerFactory.getLogger(GrobidRESTParser.class);


    private static final String GROBID_REST_HOST = "http://localhost:8080";

    private static final String GROBID_ISALIVE_PATH = "/grobid"; // isalive
    // doesn't work
    // nfc why

    private static final String GROBID_PROCESSHEADER_PATH = "/processHeaderDocument";

    private String restHostUrlStr;

    public GrobidRESTParser() {
        String restHostUrlStr = null;
        try {
            restHostUrlStr = readRestUrl();
        } catch (IOException e) {
            LOG.warn("can't read rest url", e);
        }

        if (restHostUrlStr == null
                || (restHostUrlStr != null && restHostUrlStr.equals(""))) {
            this.restHostUrlStr = GROBID_REST_HOST;
        } else {
            this.restHostUrlStr = restHostUrlStr;
        }
    }

    public void parse(String filePath, ContentHandler handler, Metadata metadata,
                      ParseContext context) throws FileNotFoundException {

        File pdfFile = new File(filePath);
        ContentDisposition cd = new ContentDisposition(
                "form-data; name=\"input\"; filename=\"" + pdfFile.getName() + "\"");
        Attachment att = new Attachment("input", new FileInputStream(pdfFile), cd);
        MultipartBody body = new MultipartBody(att);

        Response response = WebClient
                .create(restHostUrlStr + GROBID_PROCESSHEADER_PATH)
                .accept(MediaType.APPLICATION_XML).type(MediaType.MULTIPART_FORM_DATA)
                .post(body);

        try {
            String resp = response.readEntity(String.class);
            Metadata teiMet = new TEIDOMParser().parse(resp, context);
            for (String key : teiMet.names()) {
                metadata.add("grobid:header_" + key, teiMet.get(key));
            }
        } catch (Exception e) {
            LOG.warn("Couldn't read response", e);
        }
    }

    private static String readRestUrl() throws IOException {
        Properties grobidProperties = new Properties();
        grobidProperties.load(GrobidRESTParser.class
                .getResourceAsStream("GrobidExtractor.properties"));

        return grobidProperties.getProperty("grobid.server.url");
    }

    protected static boolean canRun() {
        Response response = null;

        try {
            response = WebClient.create(readRestUrl() + GROBID_ISALIVE_PATH)
                    .accept(MediaType.TEXT_HTML).get();
            String resp = response.readEntity(String.class);
            return resp != null && !resp.equals("") && resp.startsWith("<h4>");
        } catch (Exception e) {
            //swallow...can't run
            return false;
        }
    }

}
