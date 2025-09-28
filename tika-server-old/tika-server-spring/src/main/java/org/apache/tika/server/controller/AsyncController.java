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
import java.util.Set;

import org.apache.commons.lang3.StringUtils;
import org.apache.tika.exception.TikaException;
import org.apache.tika.pipes.core.FetchEmitTuple;
import org.apache.tika.pipes.core.async.AsyncProcessor;
import org.apache.tika.pipes.core.async.OfferLargerThanQueueSize;
import org.apache.tika.pipes.core.emitter.EmitterManager;
import org.apache.tika.pipes.core.extractor.EmbeddedDocumentBytesConfig;
import org.apache.tika.pipes.core.fetcher.FetchKey;
import org.apache.tika.pipes.core.serialization.JsonFetchEmitTupleList;
import org.apache.tika.server.core.resource.AsyncRequest;
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
import org.xml.sax.SAXException;

/**
 * Async Resource controller providing asynchronous document processing endpoints.
 */
@RestController
@RequestMapping("/async")
@ConditionalOnProperty(name = "tika.async.enabled", havingValue = "true", matchIfMissing = false)
public class AsyncController {

    private static final Logger LOG = LoggerFactory.getLogger(AsyncController.class);
    
    private final AsyncProcessor asyncProcessor;
    private final Set<String> supportedFetchers;
    private final EmitterManager emitterManager;
    private final long maxQueuePauseMs = 60000;

    @Autowired(required = false)
    public AsyncController() throws TikaException, IOException, SAXException {
        // For now, use default Tika config path - in production this should be configurable
        java.nio.file.Path tikaConfigPath = Paths.get("tika-config.xml");
        this.asyncProcessor = new AsyncProcessor(tikaConfigPath);
        this.supportedFetchers = Set.of("file", "http", "s3"); // This should be configured
        this.emitterManager = EmitterManager.load(tikaConfigPath);
    }

    @PostMapping(consumes = "application/json", produces = "application/json")
    public ResponseEntity<Map<String, Object>> processAsync(@RequestBody String requestBody) {
        try {
            AsyncRequest request = deserializeAsyncRequest(requestBody);

            // Validate fetchers and emitters
            for (FetchEmitTuple t : request.getTuples()) {
                if (!supportedFetchers.contains(t.getFetchKey().getFetcherName())) {
                    return ResponseEntity.badRequest().body(badFetcher(t.getFetchKey()));
                }
                
                if (!emitterManager.getSupported().contains(t.getEmitKey().getEmitterName())) {
                    return ResponseEntity.badRequest().body(badEmitter(t.getEmitKey().getEmitterName()));
                }
                
                // Check embedded document bytes emitter if configured
                org.apache.tika.parser.ParseContext parseContext = t.getParseContext();
                EmbeddedDocumentBytesConfig embeddedDocumentBytesConfig = 
                    parseContext.get(EmbeddedDocumentBytesConfig.class);
                    
                if (embeddedDocumentBytesConfig != null && 
                    embeddedDocumentBytesConfig.isExtractEmbeddedDocumentBytes() &&
                    !StringUtils.isAllBlank(embeddedDocumentBytesConfig.getEmitter())) {
                    
                    String bytesEmitter = embeddedDocumentBytesConfig.getEmitter();
                    if (!emitterManager.getSupported().contains(bytesEmitter)) {
                        return ResponseEntity.badRequest().body(badEmitter(bytesEmitter));
                    }
                }
            }

            try {
                boolean offered = asyncProcessor.offer(request.getTuples(), maxQueuePauseMs);
                if (offered) {
                    LOG.info("accepted {} tuples, capacity={}", 
                        request.getTuples().size(), asyncProcessor.getCapacity());
                    return ResponseEntity.ok(ok(request.getTuples().size()));
                } else {
                    LOG.info("throttling {} tuples, capacity={}", 
                        request.getTuples().size(), asyncProcessor.getCapacity());
                    return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                        .body(throttle(request.getTuples().size()));
                }
            } catch (OfferLargerThanQueueSize e) {
                LOG.info("throttling {} tuples, capacity={}", 
                    request.getTuples().size(), asyncProcessor.getCapacity());
                return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body(throttle(request.getTuples().size()));
            }

        } catch (Exception e) {
            LOG.error("Error processing async request", e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, 
                "Error processing async request: " + e.getMessage(), e);
        }
    }

    private AsyncRequest deserializeAsyncRequest(String requestBody) throws IOException {
        try (Reader reader = new java.io.StringReader(requestBody)) {
            return new AsyncRequest(JsonFetchEmitTupleList.fromJson(reader));
        }
    }

    private Map<String, Object> ok(int size) {
        Map<String, Object> map = new HashMap<>();
        map.put("status", "ok");
        map.put("added", size);
        return map;
    }

    private Map<String, Object> throttle(int requestSize) {
        Map<String, Object> map = new HashMap<>();
        map.put("status", "throttled");
        map.put("msg", "not able to receive request of size " + requestSize + " at this time");
        map.put("capacity", asyncProcessor.getCapacity());
        return map;
    }

    private Map<String, Object> badEmitter(String emitterName) {
        Map<String, Object> map = new HashMap<>();
        map.put("error", "can't find emitter for " + emitterName);
        return map;
    }

    private Map<String, Object> badFetcher(FetchKey fetchKey) {
        Map<String, Object> map = new HashMap<>();
        map.put("error", "can't find fetcher for " + fetchKey.getFetcherName());
        return map;
    }
}
