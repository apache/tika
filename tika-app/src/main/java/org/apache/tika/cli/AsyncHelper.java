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
package org.apache.tika.cli;

import java.util.ArrayList;
import java.util.List;


public class AsyncHelper {

    private static final String TIKA_CONFIG_KEY = "--config=";
    private static final String EXTRACT_DIR_KEY = "--extract-dir=";

    public static String[] translateArgs(String[] args) {
        List<String> argList = new ArrayList<>();
        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            if (arg.startsWith(TIKA_CONFIG_KEY)) {
                String c = arg.substring(TIKA_CONFIG_KEY.length());
                argList.add("-c");
                argList.add(c);
            } else if (arg.startsWith(EXTRACT_DIR_KEY)) {
                // Translate --extract-dir=<dir> to -o <dir> for TikaAsyncCLI
                String dir = arg.substring(EXTRACT_DIR_KEY.length());
                if (dir.isEmpty()) {
                    dir = ".";
                }
                argList.add("-o");
                argList.add(dir);
            } else if ("-a".equals(arg)) {
                //do nothing
            } else {
                argList.add(args[i]);
            }
        }
        return argList.toArray(new String[0]);
    }
}
