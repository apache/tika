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
package org.apache.tika.parser;

import org.apache.tika.config.TikaComponent;
import org.apache.tika.metadata.Metadata;

/**
 * A simple {@link PasswordProvider} that returns a configured password
 * for all documents. This can be configured via JSON in the parseContext:
 * <pre>
 * {
 *   "parseContext": {
 *     "simple-password-provider": {
 *       "password": "secret"
 *     }
 *   }
 * }
 * </pre>
 *
 * @since Apache Tika 4.0
 */
@TikaComponent(contextKey = PasswordProvider.class)
public class SimplePasswordProvider implements PasswordProvider {

    private static final long serialVersionUID = 1L;

    private String password;

    public SimplePasswordProvider() {
    }

    public SimplePasswordProvider(String password) {
        this.password = password;
    }

    @Override
    public String getPassword(Metadata metadata) {
        return password;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }
}
