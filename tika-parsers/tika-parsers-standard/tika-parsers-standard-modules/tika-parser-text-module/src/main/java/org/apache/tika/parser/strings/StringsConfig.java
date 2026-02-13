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
package org.apache.tika.parser.strings;

import java.io.File;
import java.io.Serializable;

import org.apache.tika.exception.TikaConfigException;
import org.apache.tika.utils.StringUtils;

/**
 * Configuration for the "strings" (or strings-alternative) command.
 */
public class StringsConfig implements Serializable {
    /**
     * Serial version UID
     */
    private static final long serialVersionUID = -1465227101645003594L;

    private String stringsPath = "";

    private String filePath = "";

    // Minimum sequence length (characters) to print
    private int minLength = 4;

    // Character encoding of the strings that are to be found
    private StringsEncoding encoding = StringsEncoding.SINGLE_7_BIT;

    // Maximum time (seconds) to wait for the strings process termination
    private int timeoutSeconds = 120;

    /**
     * Returns the "strings" installation folder.
     *
     * @return the "strings" installation folder.
     */
    public String getStringsPath() {
        return stringsPath;
    }

    /**
     * Sets the "strings" installation folder.
     *
     * @param stringsPath the "strings" installation folder.
     */
    public void setStringsPath(String stringsPath) throws TikaConfigException {
        if (stringsPath != null && !stringsPath.isEmpty() &&
                !stringsPath.endsWith(File.separator)) {
            stringsPath += File.separatorChar;
        }
        this.stringsPath = stringsPath;
    }

    /**
     * Returns the path to the "file" command.
     *
     * @return the path to the "file" command.
     */
    public String getFilePath() {
        return filePath;
    }

    /**
     * Sets the path to the "file" command.
     *
     * @param filePath the path to the "file" command.
     */
    public void setFilePath(String filePath) throws TikaConfigException {
        this.filePath = filePath;
    }

    /**
     * Returns the minimum sequence length (characters) to print.
     *
     * @return the minimum sequence length (characters) to print.
     */
    public int getMinLength() {
        return this.minLength;
    }

    /**
     * Sets the minimum sequence length (characters) to print.
     *
     * @param minLength the minimum sequence length (characters) to print.
     */
    public void setMinLength(int minLength) {
        if (minLength < 1) {
            throw new IllegalArgumentException("Invalid minimum length");
        }
        this.minLength = minLength;
    }

    /**
     * Returns the character encoding of the strings that are to be found.
     *
     * @return {@link StringsEncoding} enum that represents the character
     * encoding of the strings that are to be found.
     */
    public StringsEncoding getEncoding() {
        return this.encoding;
    }

    /**
     * Sets the character encoding of the strings that are to be found.
     *
     * @param encoding {@link StringsEncoding} enum that represents the character
     *                 encoding of the strings that are to be found.
     */
    public void setEncoding(StringsEncoding encoding) {
        this.encoding = encoding;
    }

    /**
     * Returns the maximum time (in seconds) to wait for the "strings" command
     * to terminate.
     *
     * @return the maximum time (in seconds) to wait for the "strings" command
     * to terminate.
     */
    public int getTimeoutSeconds() {
        return this.timeoutSeconds;
    }

    /**
     * Sets the maximum time (in seconds) to wait for the "strings" command to
     * terminate.
     *
     * @param timeoutSeconds the maximum time (in seconds) to wait for the "strings"
     *                       command to terminate.
     */
    public void setTimeoutSeconds(int timeoutSeconds) {
        if (timeoutSeconds < 1) {
            throw new IllegalArgumentException("Invalid timeout");
        }
        this.timeoutSeconds = timeoutSeconds;
    }

    /**
     * RuntimeConfig blocks modification of security-sensitive path fields at runtime.
     * When a config is obtained from ParseContext (i.e. user-provided at parse time),
     * it should be deserialized as a RuntimeConfig to prevent path injection.
     * <p>
     * This class is deserialized by ConfigDeserializer (in tika-serialization) which uses
     * Jackson to populate fields via setters. If the JSON contains any path fields, the
     * overridden setters will throw TikaConfigException.
     */
    public static class RuntimeConfig extends StringsConfig {

        public RuntimeConfig() {
            super();
        }

        @Override
        public void setStringsPath(String stringsPath) throws TikaConfigException {
            if (!StringUtils.isBlank(stringsPath)) {
                throw new TikaConfigException(
                        "Cannot modify stringsPath at runtime. " +
                                "Paths must be configured at parser initialization time.");
            }
        }

        @Override
        public void setFilePath(String filePath) throws TikaConfigException {
            if (!StringUtils.isBlank(filePath)) {
                throw new TikaConfigException(
                        "Cannot modify filePath at runtime. " +
                                "Paths must be configured at parser initialization time.");
            }
        }
    }
}
