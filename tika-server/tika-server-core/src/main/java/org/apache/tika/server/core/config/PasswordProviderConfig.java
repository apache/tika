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
package org.apache.tika.server.core.config;

import static java.nio.charset.StandardCharsets.UTF_8;

import jakarta.ws.rs.core.MultivaluedMap;

import org.apache.commons.codec.binary.Base64;

import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.PasswordProvider;
import org.apache.tika.server.core.ParseContextConfig;

public class PasswordProviderConfig implements ParseContextConfig {
    public static final String PASSWORD = "Password";
    public static final String PASSWORD_BASE64_UTF8 = "Password_Base64_UTF-8";
    private static final Base64 BASE_64 = new Base64();

    private static String decodeBase64UTF8(String s) {
        byte[] bytes = BASE_64.decode(s);
        return new String(bytes, UTF_8);
    }

    @Override
    public void configure(MultivaluedMap<String, String> httpHeaders, Metadata metadata,
                          ParseContext context) {
        String tmpPassword = httpHeaders.getFirst(PASSWORD_BASE64_UTF8);
        if (tmpPassword != null) {
            tmpPassword = decodeBase64UTF8(tmpPassword);
        } else {
            tmpPassword = httpHeaders.getFirst(PASSWORD);
        }
        if (tmpPassword != null) {
            final String password = tmpPassword;
            context.set(PasswordProvider.class, metadata1 -> password);
        }
    }

}
