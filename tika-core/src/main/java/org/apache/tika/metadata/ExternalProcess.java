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
package org.apache.tika.metadata;

public interface ExternalProcess {

    String PREFIX_EXTERNAL_META = "external-process";

    /**
     * STD_OUT
     */
    Property STD_OUT = Property.externalText(
            PREFIX_EXTERNAL_META + TikaCoreProperties.NAMESPACE_PREFIX_DELIMITER + "stdout");

    /**
     * STD_ERR
     */
    Property STD_ERR = Property.externalText(
            PREFIX_EXTERNAL_META + TikaCoreProperties.NAMESPACE_PREFIX_DELIMITER + "stderr");


    /**
     * Whether or not stdout was truncated
     */
    Property STD_OUT_IS_TRUNCATED = Property.externalBoolean(
            PREFIX_EXTERNAL_META + TikaCoreProperties.NAMESPACE_PREFIX_DELIMITER +
                    "stdout-truncated");

    /**
     * Whether or not stderr was truncated
     */
    Property STD_ERR_IS_TRUNCATED = Property.externalBoolean(
            PREFIX_EXTERNAL_META + TikaCoreProperties.NAMESPACE_PREFIX_DELIMITER +
                    "stderr-truncated");

    /**
     * Stdout length whether or not it was truncated.  If it was truncated,
     * what would its length have been; if it wasn't, what is its length.
     */
    Property STD_OUT_LENGTH = Property.externalReal(
            PREFIX_EXTERNAL_META + TikaCoreProperties.NAMESPACE_PREFIX_DELIMITER +
                    "stdout-length");

    /**
     * Stderr length whether or not it was truncated.  If it was truncated,
     * what would its length have been; if it wasn't, what is its length.
     */
    Property STD_ERR_LENGTH = Property.externalReal(
            PREFIX_EXTERNAL_META + TikaCoreProperties.NAMESPACE_PREFIX_DELIMITER +
                    "stderr-length");

    /**
     * Exit value of the sub process
     */
    Property EXIT_VALUE = Property.externalInteger(
            PREFIX_EXTERNAL_META + TikaCoreProperties.NAMESPACE_PREFIX_DELIMITER +
                    "exit-value");

    /**
     * Was the process timed out
     */
    Property IS_TIMEOUT = Property.externalBoolean(
            PREFIX_EXTERNAL_META + TikaCoreProperties.NAMESPACE_PREFIX_DELIMITER + "timeout");

}
