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
package org.apache.tika.pipes.emitter.es;

/**
 * HTTP client settings for the ES emitter and reporter.
 * Field names and semantics are intentionally aligned with the OpenSearch
 * emitter's HttpClientConfig for configuration consistency.
 *
 * @param userName          Username for basic authentication (optional)
 * @param password          Password for basic authentication (optional)
 * @param authScheme        Auth scheme passed to HttpClientFactory, e.g. {@code "basic"} or {@code "ntlm"}
 * @param connectionTimeout Connect timeout in milliseconds
 * @param socketTimeout     Socket read timeout in milliseconds
 * @param proxyHost         HTTP proxy host (optional)
 * @param proxyPort         HTTP proxy port
 * @param verifySsl         When {@code true}, the HTTP client validates server certificates and
 *                          hostnames using the JVM's default trust store.  Defaults to
 *                          {@code false} for backward compatibility (trust-all / no hostname check).
 */
public record HttpClientConfig(String userName, String password,
                               String authScheme, int connectionTimeout,
                               int socketTimeout, String proxyHost, int proxyPort,
                               boolean verifySsl) {
}
