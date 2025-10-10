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
package org.apache.tika.server.service;

import java.io.IOException;
import java.io.InputStream;

import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import org.apache.tika.config.TikaTaskTimeout;
import org.apache.tika.exception.EncryptedDocumentException;
import org.apache.tika.exception.WriteLimitReachedException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.pipes.core.exception.TikaServerParseException;

/**
 * Service for parsing documents using Tika.
 * Replaces the static parse method from TikaResource with Spring Boot adaptation.
 * 
 * Note: This simplified version doesn't include the full ServerStatus task management
 * that was present in the original TikaResource.parse method, as that was designed
 * for the fork-mode server architecture. In Spring Boot, we rely on the container's
 * request handling and timeout mechanisms.
 */
@Service
public class TikaParsingService {
    
    private static final long DEFAULT_TASK_TIMEOUT_MILLIS = 300000; // 5 minutes
    private static final long DEFAULT_MINIMUM_TIMEOUT_MILLIS = 30000; // 30 seconds
    
    private final Environment environment;
    private final boolean isOperating;
    
    @Autowired
    public TikaParsingService(Environment environment) {
        this.environment = environment;
        // In Spring Boot mode, we're always operating (no fork mode complexity)
        this.isOperating = true;
    }
    
    /**
     * Parses a document using the specified parser and handler.
     * This is equivalent to the static parse method from TikaResource, adapted for Spring Boot.
     * 
     * @param parser the parser to use
     * @param logger the logger to use for logging errors
     * @param path the file path (for logging purposes)
     * @param inputStream the input stream to parse (will be closed by this method)
     * @param handler the content handler to receive parsing events
     * @param metadata the metadata object
     * @param parseContext the parse context
     * @throws IOException if an error occurs during parsing
     */
    public void parse(Parser parser, Logger logger, String path, InputStream inputStream, 
                     ContentHandler handler, Metadata metadata, ParseContext parseContext) throws IOException {
        
        checkIsOperating();
        
        String fileName = metadata.get(TikaCoreProperties.RESOURCE_NAME_KEY);
        long timeoutMillis = getTaskTimeout(parseContext);
        
        // Note: In the original TikaResource, there was complex task tracking with ServerStatus.
        // In Spring Boot, we rely on the container's request handling and don't need the same
        // level of task management since we're not in fork mode.
        
        try {
            // Validate timeout before parsing
            validateTimeout(timeoutMillis);
            
            parser.parse(inputStream, handler, metadata, parseContext);
            
        } catch (SAXException e) {
            throw new TikaServerParseException(e);
        } catch (EncryptedDocumentException e) {
            logger.warn("{}: Encrypted document ({})", path, fileName, e);
            throw new TikaServerParseException(e);
        } catch (Exception e) {
            if (!WriteLimitReachedException.isWriteLimitReached(e)) {
                logger.warn("{}: Text extraction failed ({})", path, fileName, e);
            }
            throw new TikaServerParseException(e);
        } catch (OutOfMemoryError e) {
            logger.error("{}: Out of memory error ({})", path, fileName, e);
            // In the original, this would set SERVER_STATUS to ERROR and potentially restart the fork
            // In Spring Boot, we just log and rethrow - the container will handle the error
            throw e;
        } finally {
            // Always close the input stream
            if (inputStream != null) {
                try {
                    inputStream.close();
                } catch (IOException e) {
                    logger.warn("Error closing input stream", e);
                }
            }
        }
    }
    
    /**
     * Checks if the server is operating. In Spring Boot mode, this is always true.
     * In the original TikaResource, this checked ServerStatus for fork mode.
     */
    private void checkIsOperating() {
        if (!isOperating) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Server is not operating");
        }
    }
    
    /**
     * Gets the task timeout from the parse context or configuration.
     * Adapted from TikaResource.getTaskTimeout().
     * 
     * @param parseContext the parse context
     * @return timeout in milliseconds
     */
    private long getTaskTimeout(ParseContext parseContext) {
        TikaTaskTimeout tikaTaskTimeout = parseContext.get(TikaTaskTimeout.class);
        long defaultTimeout = environment.getProperty("tika.server.taskTimeoutMillis", 
                Long.class, DEFAULT_TASK_TIMEOUT_MILLIS);
        
        if (tikaTaskTimeout != null) {
            long requestedTimeout = tikaTaskTimeout.getTimeoutMillis();
            
            if (requestedTimeout > defaultTimeout) {
                throw new IllegalArgumentException(
                        "Can't request a timeout (" + requestedTimeout + "ms) greater than the " +
                        "taskTimeoutMillis set in the server config (" + defaultTimeout + "ms)");
            }
            
            long minimumTimeout = environment.getProperty("tika.server.minimumTimeoutMillis", 
                    Long.class, DEFAULT_MINIMUM_TIMEOUT_MILLIS);
                    
            if (requestedTimeout < minimumTimeout) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "taskTimeoutMillis must be > minimumTimeoutMillis, currently set to (" + 
                        minimumTimeout + "ms)");
            }
            
            return requestedTimeout;
        }
        
        return defaultTimeout;
    }
    
    /**
     * Validates that the timeout is within acceptable bounds.
     * 
     * @param timeoutMillis the timeout to validate
     */
    private void validateTimeout(long timeoutMillis) {
        long minimumTimeout = environment.getProperty("tika.server.minimumTimeoutMillis", 
                Long.class, DEFAULT_MINIMUM_TIMEOUT_MILLIS);
                
        if (timeoutMillis < minimumTimeout) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Timeout (" + timeoutMillis + "ms) is less than minimum allowed (" + 
                    minimumTimeout + "ms)");
        }
    }
}
