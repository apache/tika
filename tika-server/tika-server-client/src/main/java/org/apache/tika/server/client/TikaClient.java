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

import java.io.IOException;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.apache.tika.client.HttpClientFactory;
import org.apache.tika.exception.TikaConfigException;
import org.apache.tika.exception.TikaException;
import org.apache.tika.serialization.JsonFetchEmitTuple;
import org.apache.tika.pipes.FetchEmitTuple;

public class TikaClient {

    private final Random random = new Random();
    private final List<TikaPipesHttpClient> clients;


    private TikaClient(List<TikaPipesHttpClient> clients) {
        this.clients = clients;
    }

    public static TikaClient get(HttpClientFactory httpClientFactory, List<String> tikaServers)
            throws TikaConfigException {
        List clients = new ArrayList<>();
        for (String url : tikaServers) {
            //client factory is not thread safe, create a copy per client
            clients.add(new TikaPipesHttpClient(url, httpClientFactory.copy()));
        }
        return new TikaClient(clients);
    }

    /*public List<Metadata> parse(InputStream is, Metadata metadata)
    throws IOException, TikaException {

    }*/

    public TikaEmitterResult parse(FetchEmitTuple fetchEmit) throws IOException, TikaException {
        TikaPipesHttpClient client = getHttpClient();
        StringWriter writer = new StringWriter();
        JsonFetchEmitTuple.toJson(fetchEmit, writer);
        return client.postJson(writer.toString());
    }

    private TikaPipesHttpClient getHttpClient() {
        if (clients.size() == 1) {
            return clients.get(0);
        }
        int index = random.nextInt(clients.size());
        return clients.get(index);
    }
}
