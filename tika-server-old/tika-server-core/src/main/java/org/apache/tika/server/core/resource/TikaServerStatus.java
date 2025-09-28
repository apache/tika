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

import java.util.LinkedHashMap;
import java.util.Map;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;

import org.apache.tika.server.core.ServerStatus;

@Path("/status")
public class TikaServerStatus {
    private final ServerStatus serverStatus;

    public TikaServerStatus(ServerStatus serverStatus) {
        this.serverStatus = serverStatus;
    }

    @GET
    @Produces("application/json")
    public Map<String, Object> getStatus() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("server_id", serverStatus.getServerId());
        map.put("status", serverStatus.getStatus());
        map.put("millis_since_last_parse_started", serverStatus.getMillisSinceLastParseStarted());
        map.put("files_processed", serverStatus.getFilesProcessed());
        map.put("num_restarts", serverStatus.getNumRestarts());
        return map;
    }
}
