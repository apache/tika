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

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

import org.apache.tika.exception.TikaConfigException;
import org.apache.tika.pipes.core.FetchEmitTuple;
import org.apache.tika.pipes.core.PipesConfig;
import org.apache.tika.pipes.core.PipesException;
import org.apache.tika.pipes.core.PipesParser;
import org.apache.tika.pipes.core.PipesResult;
import org.apache.tika.pipes.core.serialization.JsonFetchEmitTuple;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Pipes Resource controller providing document processing through the Tika pipes framework.
 */
@RestController
@RequestMapping("/pipes")
@ConditionalOnProperty(name = "tika.pipes.enabled", havingValue = "true", matchIfMissing = false)
public class PipesController {

    private static final Logger LOG = LoggerFactory.getLogger(PipesController.class);
    
    private final PipesParser pipesParser;

    @Autowired(required = false)
    public PipesController() throws TikaConfigException, IOException {
        // For now, use default Tika config path - in production this should be configurable
        java.nio.file.Path tikaConfigPath = Paths.get("tika-config.xml");
        PipesConfig pipesConfig = PipesConfig.load(tikaConfigPath);
        
        // This has to be zero. Everything must be emitted through the PipesServer
        long maxEmit = pipesConfig.getMaxForEmitBatchBytes();
        if (maxEmit != 0) {
            pipesConfig.setMaxForEmitBatchBytes(0);
            if (maxEmit != PipesConfig.DEFAULT_MAX_FOR_EMIT_BATCH) {
                LOG.warn("resetting max for emit batch to 0");
            }
        }
        
        this.pipesParser = new PipesParser(pipesConfig);
    }

    @PostMapping(consumes = "application/json", produces = "application/json")
    public ResponseEntity<Map<String, String>> processPipes(@RequestBody String requestBody) {
        try {
            FetchEmitTuple fetchEmitTuple;
            try (Reader reader = new java.io.StringReader(requestBody)) {
                fetchEmitTuple = JsonFetchEmitTuple.fromJson(reader);
            }

            Map<String, String> result = processTuple(fetchEmitTuple);
            return ResponseEntity.ok(result);

        } catch (Exception e) {
            LOG.error("Error processing pipes request", e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, 
                "Error processing pipes request: " + e.getMessage(), e);
        }
    }

    private Map<String, String> processTuple(FetchEmitTuple fetchEmitTuple) 
            throws InterruptedException, PipesException, IOException {

        PipesResult pipesResult = pipesParser.parse(fetchEmitTuple);
        
        switch (pipesResult.getStatus()) {
            case CLIENT_UNAVAILABLE_WITHIN_MS:
                return returnError("client_unavailable", 
                    "client not available within allotted amount of time");
                    
            case EMIT_EXCEPTION:
                return returnEmitException(pipesResult.getMessage());
                
            case PARSE_SUCCESS:
            case PARSE_SUCCESS_WITH_EXCEPTION:
                return returnError("unexpected_status", 
                    "Should have emitted in forked process?!");
                    
            case EMIT_SUCCESS:
                return returnSuccess();
                
            case EMIT_SUCCESS_PARSE_EXCEPTION:
                return parseException(pipesResult.getMessage(), true);
                
            case PARSE_EXCEPTION_EMIT:
                return returnError("unexpected_status", 
                    "Should have tried to emit in forked process?!");
                    
            case PARSE_EXCEPTION_NO_EMIT:
                return parseException(pipesResult.getMessage(), false);
                
            case TIMEOUT:
                return returnError("timeout", "Processing timed out");
                
            case OOM:
                return returnError("oom", "Out of memory error occurred");
                
            case UNSPECIFIED_CRASH:
                return returnError("unknown_crash", "Unknown crash occurred");
                
            case NO_EMITTER_FOUND:
                return returnError("no_emitter_found", 
                    "Couldn't find emitter that matched: " + 
                    fetchEmitTuple.getEmitKey().getEmitterName());
                    
            default:
                return returnError("unknown_status", 
                    "I'm sorry, I don't yet handle a status of this type: " + 
                    pipesResult.getStatus());
        }
    }

    private Map<String, String> parseException(String msg, boolean emitted) {
        Map<String, String> statusMap = new HashMap<>();
        statusMap.put("status", "ok");
        statusMap.put("parse_exception", msg);
        statusMap.put("emitted", Boolean.toString(emitted));
        return statusMap;
    }

    private Map<String, String> returnEmitException(String msg) {
        Map<String, String> statusMap = new HashMap<>();
        statusMap.put("status", "emit_exception");
        statusMap.put("message", msg);
        return statusMap;
    }

    private Map<String, String> returnSuccess() {
        Map<String, String> statusMap = new HashMap<>();
        statusMap.put("status", "ok");
        return statusMap;
    }

    private Map<String, String> returnError(String type, String message) {
        Map<String, String> statusMap = new HashMap<>();
        statusMap.put("status", "parse_error");
        statusMap.put("parse_error", type);
        if (message != null) {
            statusMap.put("message", message);
        }
        return statusMap;
    }
}
