/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.tika.utils;


public class ProcessUtils {

    /**
     * This should correctly put double-quotes around an argument if
     * ProcessBuilder doesn't seem to work (as it doesn't
     * on paths with spaces on Windows)
     *
     * @param arg
     * @return
     */
    public static String escapeCommandLine(String arg) {
        if (arg == null) {
            return arg;
        }
        //need to test for " " on windows, can't just add double quotes
        //across platforms.
        if (arg.contains(" ") && SystemUtils.IS_OS_WINDOWS &&
                (!arg.startsWith("\"") && !arg.endsWith("\""))) {
            arg = "\"" + arg + "\"";
        }
        return arg;
    }
}
