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
package org.apache.tika.grpc.client.exception;

/**
 * Exception thrown by TikaGrpcClient operations.
 * 
 * This exception wraps all errors that can occur during gRPC communication with the Tika server,
 * including network errors, server errors, and client configuration issues.
 */
public class TikaGrpcClientException extends Exception {

    private static final long serialVersionUID = 1L;

    /**
     * Constructs a new TikaGrpcClientException with the specified message.
     * 
     * @param message the error message
     */
    public TikaGrpcClientException(String message) {
        super(message);
    }

    /**
     * Constructs a new TikaGrpcClientException with the specified message and cause.
     * 
     * @param message the error message
     * @param cause the underlying cause
     */
    public TikaGrpcClientException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * Constructs a new TikaGrpcClientException with the specified cause.
     * 
     * @param cause the underlying cause
     */
    public TikaGrpcClientException(Throwable cause) {
        super(cause);
    }
}