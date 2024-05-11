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
package org.apache.tika.pipes.fetcher;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import org.apache.tika.config.Field;
import org.apache.tika.config.Initializable;
import org.apache.tika.config.InitializableProblemHandler;
import org.apache.tika.config.Param;
import org.apache.tika.exception.TikaConfigException;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;

public class MockFetcher extends AbstractFetcher implements Initializable {

    private Map<String, Param> params;

    @Field
    private String byteString = null;

    @Field
    private boolean throwOnCheck = false;


    public void setThrowOnCheck(boolean throwOnCheck) {
        this.throwOnCheck = throwOnCheck;
    }

    public void setByteString(String byteString) {
        this.byteString = byteString;
    }

    @Override
    public void initialize(Map<String, Param> params) throws TikaConfigException {
        this.params = params;
    }

    @Override
    public void checkInitialization(InitializableProblemHandler problemHandler)
            throws TikaConfigException {
        if (throwOnCheck) {
            throw new TikaConfigException("throw on check");
        }
    }


    @Override
    public InputStream fetch(String fetchKey, Metadata metadata) throws TikaException, IOException {
        return byteString == null ? new ByteArrayInputStream(new byte[0]) :
                new ByteArrayInputStream(byteString.getBytes(StandardCharsets.UTF_8));
    }
}
