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
package org.apache.tika.client;

import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.BasicHttpEntity;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

public class HttpClientUtil {

    private static HttpClient CLIENT = HttpClients.createDefault();

    public static boolean postJson(String url, String json) throws IOException,
            TikaClientException {
        HttpPost post = new HttpPost(url);
        ByteArrayEntity entity = new ByteArrayEntity(json.getBytes(StandardCharsets.UTF_8));
        post.setEntity(entity);
        post.setHeader("Content-Type", "application/json");
        HttpResponse response = CLIENT.execute(post);


        if (response.getStatusLine().getStatusCode() != 200) {
            String msg = EntityUtils.toString(response.getEntity());
            throw new TikaClientException("Bad status: " +
                    response.getStatusLine().getStatusCode() + " : "+
                    msg);
        } else {
            String msg = EntityUtils.toString(response.getEntity());
            System.out.println("httputil: " + msg);
        }
        return true;
    }
}
