/*
 *
 *  * Licensed to the Apache Software Foundation (ASF) under one or more
 *  * contributor license agreements.  See the NOTICE file distributed with
 *  * this work for additional information regarding copyright ownership.
 *  * The ASF licenses this file to You under the Apache License, Version 2.0
 *  * (the "License"); you may not use this file except in compliance with
 *  * the License.  You may obtain a copy of the License at
 *  *
 *  *     http://www.apache.org/licenses/LICENSE-2.0
 *  *
 *  * Unless required by applicable law or agreed to in writing, software
 *  * distributed under the License is distributed on an "AS IS" BASIS,
 *  * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  * See the License for the specific language governing permissions and
 *  * limitations under the License.
 *
 *
 */

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
package org.apache.tika.server.controller;

import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.tika.Tika;
import org.apache.tika.config.TikaConfig;
import org.apache.tika.server.core.ServerStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Server Status controller providing server status and version information endpoints.
 */
@RestController
public class ServerStatusController {

    private final TikaConfig tikaConfig = TikaConfig.getDefaultConfig();
    private final Tika tika = new Tika(tikaConfig);
    
    @Autowired(required = false)
    private ServerStatus serverStatus;

    @GetMapping(value = "/status", produces = "application/json")
    public ResponseEntity<Map<String, Object>> getStatus() {
        try {
            if (serverStatus == null) {
                // Create a mock status if ServerStatus is not available
                Map<String, Object> map = new LinkedHashMap<>();
                map.put("server_id", "spring-boot-server");
                map.put("status", "OPERATING");
                map.put("millis_since_last_parse_started", -1L);
                map.put("files_processed", -1L);
                map.put("num_restarts", 0);
                return ResponseEntity.ok(map);
            }
            
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("server_id", serverStatus.getServerId());
            map.put("status", serverStatus.getStatus());
            map.put("millis_since_last_parse_started", serverStatus.getMillisSinceLastParseStarted());
            map.put("files_processed", serverStatus.getFilesProcessed());
            map.put("num_restarts", serverStatus.getNumRestarts());
            return ResponseEntity.ok(map);
            
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, 
                "Error retrieving server status: " + e.getMessage(), e);
        }
    }

    @GetMapping(value = "/version", produces = "text/plain")
    public ResponseEntity<String> getVersion() {
        try {
            return ResponseEntity.ok(tika.toString());
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, 
                "Error retrieving version: " + e.getMessage(), e);
        }
    }
}
