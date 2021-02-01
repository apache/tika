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
package org.apache.tika.server.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import org.apache.tika.config.TikaConfig;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.pipes.fetchiterator.FetchEmitTuple;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class TikaClient {

    private static final Gson GSON = new GsonBuilder().create();

    private final Random random = new Random();
    private final TikaConfig tikaConfig;
    private List<TikaHttpClient> clients;


    public static TikaClient get(TikaConfig tikaConfig, List<String> tikaServers) throws TikaClientConfigException {
        List clients = new ArrayList<>();
        for (String url : tikaServers) {
            clients.add(TikaHttpClient.get(url));
        }
        return new TikaClient(tikaConfig, clients);
    }

    private TikaClient(TikaConfig tikaConfig, List<TikaHttpClient> clients) {
        this.tikaConfig = tikaConfig;
        this.clients = clients;
    }

    /*public List<Metadata> parse(InputStream is, Metadata metadata) throws IOException, TikaException {

    }*/

    public TikaEmitterResult parse(FetchEmitTuple fetchEmit, Metadata metadata)
            throws IOException, TikaException {
        TikaHttpClient client = getHttpClient();
        String jsonRequest = jsonifyRequest(fetchEmit, metadata);
        return client.postJson(jsonRequest);

    }

    private String jsonifyRequest(FetchEmitTuple fetchEmit, Metadata metadata) {
        JsonObject root = new JsonObject();
        root.add("fetcher", new JsonPrimitive(fetchEmit.getFetchKey().getFetcherName()));
        root.add("fetchKey", new JsonPrimitive(fetchEmit.getFetchKey().getKey()));
        root.add("emitter", new JsonPrimitive(fetchEmit.getEmitKey().getEmitterName()));
        root.add("emitKey", new JsonPrimitive(fetchEmit.getEmitKey().getKey()));
        if (metadata.size() > 0) {
            JsonObject m = new JsonObject();
            for (String n : metadata.names()) {
                String[] vals = metadata.getValues(n);
                if (vals.length == 1) {
                    m.add(n, new JsonPrimitive(vals[0]));
                } else if (vals.length > 1) {
                    JsonArray arr = new JsonArray();
                    for (int i = 0; i < vals.length; i++) {
                        arr.add(vals[i]);
                    }
                    m.add(n, arr);
                }
            }
            root.add("metadata", m);
        }
        return GSON.toJson(root);
    }

    private TikaHttpClient getHttpClient() {
        if (clients.size() == 1) {
            return clients.get(0);
        }
        int index = random.nextInt(clients.size());
        return clients.get(index);
    }
}
