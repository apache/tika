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

import java.nio.file.Path;

import org.apache.tika.exception.TikaConfigException;
import org.apache.tika.utils.StringUtils;

/**
 * Utility class for validating configuration parameters.
 */
public class ConfigValidator {

    private ConfigValidator() {
        // utility class
    }

    /**
     * Validates that a string parameter is not null or blank.
     *
     * @param paramName the name of the parameter (for error messages)
     * @param paramValue the value to validate
     * @throws TikaConfigException if the value is null or blank
     */
    public static void mustNotBeEmpty(String paramName, String paramValue)
            throws TikaConfigException {
        if (StringUtils.isBlank(paramValue)) {
            throw new TikaConfigException(
                    "parameter '" + paramName + "' must be set in the config file");
        }
    }

    /**
     * Validates that a Path parameter is not null.
     *
     * @param paramName the name of the parameter (for error messages)
     * @param paramValue the value to validate
     * @throws TikaConfigException if the value is null
     */
    public static void mustNotBeEmpty(String paramName, Path paramValue)
            throws TikaConfigException {
        if (paramValue == null) {
            throw new TikaConfigException(
                    "parameter '" + paramName + "' must be set in the config file");
        }
    }
}
