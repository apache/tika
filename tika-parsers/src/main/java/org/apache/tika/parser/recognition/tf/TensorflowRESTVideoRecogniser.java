/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.tika.parser.recognition.tf;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.Collections;
import java.util.HashSet;

import javax.ws.rs.core.UriBuilder;

import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.tika.Tika;
import org.apache.tika.config.Field;
import org.apache.tika.config.Param;
import org.apache.tika.config.TikaConfig;
import org.apache.tika.exception.TikaConfigException;
import org.apache.tika.exception.TikaException;
import org.apache.tika.io.IOUtils;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;
import org.apache.tika.mime.MimeType;
import org.apache.tika.mime.MimeTypeException;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.recognition.RecognisedObject;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

/**
 * Tensor Flow video recogniser which has high performance.
 * This implementation uses Tensorflow via REST API.
 * <p>
 * NOTE : https://wiki.apache.org/tika/TikaAndVisionVideo
 *
 * @since Apache Tika 1.15
 */
public class TensorflowRESTVideoRecogniser extends TensorflowRESTRecogniser {

    private static final Logger LOG = LoggerFactory.getLogger(TensorflowRESTVideoRecogniser.class);

    private static final Set<MediaType> SUPPORTED_MIMES = Collections.singleton(MediaType.video("mp4"));

    @Field
    private String mode = "fixed";

    @Override
    protected URI getApiUri(Metadata metadata) {
        TikaConfig config = TikaConfig.getDefaultConfig();
        String ext = null;
        //Find extension for video. It's required for OpenCV in InceptionAPI to decode video
        try {
            MimeType mimeType = config.getMimeRepository().forName(metadata.get("Content-Type"));
            ext = mimeType.getExtension();
            return UriBuilder.fromUri(apiUri).queryParam("ext", ext).build();
        } catch (MimeTypeException e) {
            LOG.error("Can't find extension from metadata");
            return apiUri;
        }
    }

    @Override
    public void initialize(Map<String, Param> params) throws TikaConfigException {
        try {
            healthUri = URI.create(apiBaseUri + "/ping");
            apiUri = URI.create(apiBaseUri + String.format(Locale.getDefault(), "/classify/video?topn=%1$d&min_confidence=%2$f&mode=%3$s",
                    topN, minConfidence, mode));

            DefaultHttpClient client = new DefaultHttpClient();
            HttpResponse response = client.execute(new HttpGet(healthUri));
            available = response.getStatusLine().getStatusCode() == 200;

            LOG.info("Available = {}, API Status = {}", available, response.getStatusLine());
            LOG.info("topN = {}, minConfidence = {}", topN, minConfidence);
        } catch (Exception e) {
            available = false;
            throw new TikaConfigException(e.getMessage(), e);
        }
    }
}
