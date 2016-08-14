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

import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.tika.config.Field;
import org.apache.tika.config.Param;
import org.apache.tika.exception.TikaConfigException;
import org.apache.tika.exception.TikaException;
import org.apache.tika.io.IOUtils;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.recognition.ObjectRecogniser;
import org.apache.tika.parser.recognition.RecognisedObject;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Tensor Flow image recogniser which has high performance.
 * This implementation uses Tensorflow via REST API.
 * <p>
 * NOTE : //TODO: link to wiki page here
 *
 * @since Apache Tika 1.14
 */
public class TensorflowRESTRecogniser implements ObjectRecogniser {

    /**
     * Maximum buffer size for image
     */
    private static final Logger LOG = LoggerFactory.getLogger(TensorflowRESTRecogniser.class);
    private static final String LABEL_LANG = "en";

    @Field
    private URI apiUri = URI.create("http://localhost:8764/inception/v3/classify?topk=10");
    @Field
    private URI healthUri = URI.create("http://localhost:8764/inception/v3/ping");

    private boolean available;

    @Override
    public Set<MediaType> getSupportedMimes() {
        return TensorflowImageRecParser.SUPPORTED_MIMES;
    }

    @Override
    public boolean isAvailable() {
        return available;
    }

    @Override
    public void initialize(Map<String, Param> params) throws TikaConfigException {
        try {
            DefaultHttpClient client = new DefaultHttpClient();
            HttpResponse response = client.execute(new HttpGet(healthUri));
            available = response.getStatusLine().getStatusCode() == 200;
            LOG.info("Available = {}, API Status = {}", available, response.getStatusLine());
        } catch (Exception e) {
            available = false;
            throw new TikaConfigException(e.getMessage(), e);
        }
    }

    @Override
    public List<RecognisedObject> recognise(InputStream stream,
                                            ContentHandler handler, Metadata metadata, ParseContext context)
            throws IOException, SAXException, TikaException {
        List<RecognisedObject> recObjs = new ArrayList<>();
        try {
            DefaultHttpClient client = new DefaultHttpClient();

            HttpPost request = new HttpPost(apiUri);

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
                    JSONObject jReply = new JSONObject(replyMessage);
                    JSONArray jClasses = jReply.getJSONArray("classnames");
                    JSONArray jConfidence = jReply.getJSONArray("confidence");
		    if (jClasses.length() != jConfidence.length()){
			LOG.warn("Classes of size {} is not equal to confidence of size {}", jClasses.length(), jConfidence.length());
		    }
                    assert jClasses.length() == jConfidence.length();
                    for (int i = 0; i < jClasses.length(); i++) {
                        RecognisedObject recObj = new RecognisedObject(jClasses.getString(i),
                                LABEL_LANG, jClasses.getString(i), jConfidence.getDouble(i));
                        recObjs.add(recObj);
                    }
                } else {
                    LOG.warn("Status = {}", response.getStatusLine());
                    LOG.warn("Response = {}", replyMessage);
                }
            }
        } catch (Exception e) {
            LOG.warn(e.getMessage(), e);
        }
        LOG.debug("Num Objects found {}", recObjs.size());
        return recObjs;
    }
}
