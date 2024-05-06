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
package org.apache.tika.pipes.emitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import org.apache.tika.config.Field;
import org.apache.tika.config.Initializable;
import org.apache.tika.config.InitializableProblemHandler;
import org.apache.tika.config.Param;
import org.apache.tika.exception.TikaConfigException;
import org.apache.tika.metadata.Metadata;

public class MockEmitter extends AbstractEmitter implements Initializable {

    @Field private boolean throwOnCheck = false;

    @Override
    public void initialize(Map<String, Param> params) throws TikaConfigException {}

    public void setThrowOnCheck(boolean throwOnCheck) {
        this.throwOnCheck = throwOnCheck;
    }

    @Override
    public void checkInitialization(InitializableProblemHandler problemHandler)
            throws TikaConfigException {

        if (throwOnCheck) {
            throw new TikaConfigException("throw on check");
        }
    }

    @Override
    public void emit(String emitKey, List<Metadata> metadataList)
            throws IOException, TikaEmitterException {}
}
