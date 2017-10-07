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

package org.apache.tika.parser.captioning.tf;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.Collections;
import java.util.HashSet;
import java.util.Arrays;

import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.tika.config.Field;
import org.apache.tika.config.InitializableProblemHandler;
import org.apache.tika.config.Param;
import org.apache.tika.exception.TikaConfigException;
import org.apache.tika.exception.TikaException;
import org.apache.tika.io.IOUtils;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.recognition.ObjectRecogniser;
import org.apache.tika.parser.captioning.CaptionObject;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

/**
 * Tensorflow image captioner.
 * This implementation uses Tensorflow via REST API.
 * <p>
 * NOTE : https://wiki.apache.org/tika/ImageCaption
 *
 * @since Apache Tika 1.17
 */
public class TensorflowRESTCaptioner implements ObjectRecogniser {
    private static final Logger LOG = LoggerFactory.getLogger(TensorflowRESTCaptioner.class);

    private static final Set<MediaType> SUPPORTED_MIMES = Collections.unmodifiableSet(
            new HashSet<>(Arrays.asList(new MediaType[]{
                    MediaType.image("jpeg"),
                    MediaType.image("png"),
                    MediaType.image("gif")
            })));

    private static final String LABEL_LANG = "eng";

    @Field
    private URI apiBaseUri = URI.create("http://localhost:8764/inception/v3");

    @Field
    private int captions = 5;

    @Field
    private int maxCaptionLength = 15;

    private URI apiUri;

    private URI healthUri;

    private boolean available;

    protected URI getApiUri(Metadata metadata) {
        return apiUri;
    }

    @Override
    public Set<MediaType> getSupportedMimes() {
        return SUPPORTED_MIMES;
    }

    @Override
    public boolean isAvailable() {
        return available;
    }

    @Override
    public void initialize(Map<String, Param> params) throws TikaConfigException {
        try {
            healthUri = URI.create(apiBaseUri + "/ping");
            apiUri = URI.create(apiBaseUri + String.format(Locale.getDefault(), "/caption/image?beam_size=%1$d&max_caption_length=%2$d",
                    captions, maxCaptionLength));

            DefaultHttpClient client = new DefaultHttpClient();
            HttpResponse response = client.execute(new HttpGet(healthUri));
            available = response.getStatusLine().getStatusCode() == 200;

            LOG.info("Available = {}, API Status = {}", available, response.getStatusLine());
            LOG.info("Captions = {}, MaxCaptionLength = {}", captions, maxCaptionLength);
        } catch (Exception e) {
            available = false;
            throw new TikaConfigException(e.getMessage(), e);
        }
    }

    @Override
    public void checkInitialization(InitializableProblemHandler handler) throws TikaConfigException {
        //TODO -- what do we want to check?
    }

    @Override
    public List<CaptionObject> recognise(InputStream stream,
                                         ContentHandler handler, Metadata metadata, ParseContext context)
            throws IOException, SAXException, TikaException {
        List<CaptionObject> capObjs = new ArrayList<>();
        try {
            DefaultHttpClient client = new DefaultHttpClient();

            HttpPost request = new HttpPost(getApiUri(metadata));

            try (ByteArrayOutputStream byteStream = new ByteArrayOutputStream()) {
                //TODO: convert this to stream, this might cause OOM issue
                // InputStreamEntity is not working
                // request.setEntity(new InputStreamEntity(stream, -1));
                IOUtils.copy(stream, byteStream);
                request.setEntity(new ByteArrayEntity(byteStream.toByteArray()));
            }

            HttpResponse response = client.execute(request);
            try (InputStream reply = response.getEntity().getContent()) {
                String replyMessage = IOUtils.toString(reply);
                if (response.getStatusLine().getStatusCode() == 200) {
                    JSONObject jReply = (JSONObject) new JSONParser().parse(replyMessage);
                    JSONArray jCaptions = (JSONArray) jReply.get("captions");
                    for (int i = 0; i < jCaptions.size(); i++) {
                        JSONObject jCaption = (JSONObject) jCaptions.get(i);
                        String sentence = (String) jCaption.get("sentence");
                        Double confidence = (Double) jCaption.get("confidence");
                        capObjs.add(new CaptionObject(sentence, LABEL_LANG, confidence));
                    }
                } else {
                    LOG.warn("Status = {}", response.getStatusLine());
                    LOG.warn("Response = {}", replyMessage);
                }
            }
        } catch (Exception e) {
            LOG.warn(e.getMessage(), e);
        }
        return capObjs;
    }
}
