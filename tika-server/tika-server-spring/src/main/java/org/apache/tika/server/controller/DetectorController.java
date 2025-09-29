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
import java.io.InputStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.server.api.DetectorResourceApi;
import org.apache.tika.server.component.ServerStatus;
import org.apache.tika.server.util.TikaResource;

/**
 * Controller for MIME/media type detection using the default detector.
 * Handles the /detect endpoint for the Detector Resource tag.
 */
@RestController
public class DetectorController implements DetectorResourceApi {
    
    private static final Logger LOG = LoggerFactory.getLogger(DetectorController.class);
    private final ServerStatus serverStatus;

    @Autowired
    public DetectorController(ServerStatus serverStatus) {
        this.serverStatus = serverStatus;
    }

    @Override
    public ResponseEntity<String> putStream(Resource body) {
        if (body == null) {
            return ResponseEntity.badRequest().body("No document provided");
        }
        
        Metadata metadata = new Metadata();
        String filename = body.getFilename();
        LOG.info("Detecting media type for Filename: {}", filename);
        
        if (filename != null) {
            metadata.add(TikaCoreProperties.RESOURCE_NAME_KEY, filename);
        }
        
        ParseContext parseContext = new ParseContext();
        long timeoutMillis = TikaResource.getTaskTimeout(parseContext);
        long taskId = serverStatus.start(ServerStatus.TASK.DETECT, filename, timeoutMillis);

        try (InputStream is = body.getInputStream();
             TikaInputStream tis = TikaInputStream.get(is)) {
            
            String mediaType = TikaResource
                    .getConfig()
                    .getDetector()
                    .detect(tis, metadata)
                    .toString();
            
            LOG.info("Detected media type: {} for file: {}", mediaType, filename);
            return ResponseEntity.ok(mediaType);
            
        } catch (IOException e) {
            LOG.warn("Unable to detect MIME type for file. Reason: {} ({})", e.getMessage(), filename, e);
            return ResponseEntity.ok(MediaType.OCTET_STREAM.toString());
        } catch (OutOfMemoryError e) {
            LOG.error("OOM while detecting: ({})", filename, e);
            serverStatus.setStatus(ServerStatus.STATUS.ERROR);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Out of memory error during detection");
        } catch (Throwable e) {
            LOG.error("Exception while detecting: ({})", filename, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error during MIME type detection: " + e.getMessage());
        } finally {
            serverStatus.complete(taskId);
        }
    }
}
