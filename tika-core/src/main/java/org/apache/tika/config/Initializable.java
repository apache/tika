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
package org.apache.tika.config;

import java.util.Map;

import org.apache.tika.exception.TikaConfigException;

/**
 * Components that must do special processing across multiple fields
 * at initialization time should implement this interface.
 * <p>
 * TikaConfig will call initialize on Initializable classes after
 * setting the parameters for non-statically service loaded classes.
 * <p>
 * TikaConfig will call checkInitialization on all Initializables,
 * whether loaded statically
 */
public interface Initializable {

    /**
     * @param params params to use for initialization
     * @throws TikaConfigException
     */
    void initialize(Map<String, Param> params) throws TikaConfigException;


    /**
     *
     *
     * @param problemHandler if there is a problem and no
     *                                           custom initializableProblemHandler has been configured
     *                                           via Initializable parameters,
     *                                           this is called to respond.
     * @throws TikaConfigException
     */
    void checkInitialization(InitializableProblemHandler problemHandler)
            throws TikaConfigException;


}
