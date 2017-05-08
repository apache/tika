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
package org.apache.tika.parser.exiftool;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * Class for getting the exiftool command line executable
 * path from properties or an override parameter.
 */
public class ExiftoolExecutableUtils {

    private static final Log logger = LogFactory.getLog(ExiftoolExecutableUtils.class);

    private static final String DEFAULT_EXIFTOOL_EXECUTABLE = "exiftool";

    private static final String PROPERTIES_OVERRIDE_FILE = "tika.exiftool.override.properties";
    private static final String PROPERTIES_FILE = "tika.exiftool.properties";
    private static final String PROPERTY_EXIFTOOL_EXECUTABLE = "exiftool.executable";

    /**
     * Gets the command line executable path for exiftool.
     *
     * If the runtimeExiftoolExecutable parameter is not null that is returned.
     * If "org/apache/tika/parser/exiftool/tika.exiftool.override.properties" is found on the classpath 
     * the value for "exiftool.executable" is returned.
     * Otherwise the value from "org/apache/tika/parser/exiftool/tika.exiftool.properties"
     * for "exiftool.executable" is returned.
     * 
     * @param runtimeExiftoolExecutable
     * @return the exiftool executable path
     */
    public static final String getExiftoolExecutable(String runtimeExiftoolExecutable) {
        if (runtimeExiftoolExecutable != null) {
            return runtimeExiftoolExecutable;
        }
        String executable = DEFAULT_EXIFTOOL_EXECUTABLE;
        InputStream stream;
        stream = ExiftoolExecutableUtils.class.getResourceAsStream(PROPERTIES_OVERRIDE_FILE);
        if (stream == null) {
            stream = ExiftoolExecutableUtils.class.getResourceAsStream(PROPERTIES_FILE);
        }
        if (stream != null) {
            try {
                Properties props = new Properties();
                props.load(stream);
                executable = (String) props.get(PROPERTY_EXIFTOOL_EXECUTABLE);
            } catch (IOException e) {
                logger.warn("IOException while trying to load property file. Message: " + e.getMessage());
            }
        }
        return executable;
    }

}
