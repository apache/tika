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
package org.apache.tika.server.resource;

import org.apache.tika.server.ServerStatus;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import java.util.LinkedHashMap;
import java.util.Map;

@Path("/status")
public class TikaServerStatus {

    public static final String SERVER_ID = "server_id";

    public static final String STATUS = "status";

    public static final String LAST_PARSE_MILLIS = "millis_since_last_parse_started";

    public static final String PROCESSED = "files_processed";

    public static final String RESTARTS = "num_restarts";

    private final ServerStatus serverStatus;

    public TikaServerStatus(ServerStatus serverStatus) {
        this.serverStatus = serverStatus;
    }

    @GET
    @Produces("application/json")
    public Map<String, Object> getStatus() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put(SERVER_ID, serverStatus.getServerId());
        map.put(STATUS, serverStatus.getStatus());
        map.put(LAST_PARSE_MILLIS, serverStatus.getMillisSinceLastParseStarted());
        map.put(PROCESSED, serverStatus.getFilesProcessed());
        map.put(RESTARTS, serverStatus.getNumRestarts());
        return map;
    }
}
