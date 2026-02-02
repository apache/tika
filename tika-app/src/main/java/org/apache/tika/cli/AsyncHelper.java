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
    private static final String UNPACK_FORMAT_KEY = "--unpack-format=";
    private static final String UNPACK_MODE_KEY = "--unpack-mode=";
    private static final String UNPACK_INCLUDE_METADATA = "--unpack-include-metadata";

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
            } else if (arg.startsWith(UNPACK_FORMAT_KEY)) {
                // Translate --unpack-format=<format> to --unpack-format <format>
                String format = arg.substring(UNPACK_FORMAT_KEY.length());
                argList.add("--unpack-format");
                argList.add(format);
            } else if (arg.startsWith(UNPACK_MODE_KEY)) {
                // Translate --unpack-mode=<mode> to --unpack-mode <mode>
                String mode = arg.substring(UNPACK_MODE_KEY.length());
                argList.add("--unpack-mode");
                argList.add(mode);
            } else if (arg.equals(UNPACK_INCLUDE_METADATA)) {
                argList.add("--unpack-include-metadata");
            } else if (arg.equals("-t") || arg.equals("--text")) {
                // Translate TikaCLI text output to TikaAsyncCLI handler type
                argList.add("-h");
                argList.add("t");
            } else if (arg.equals("--html")) {
                // Translate TikaCLI html output to TikaAsyncCLI handler type
                // Note: TikaCLI uses -h for html, but TikaAsyncCLI uses -h for handler type
                argList.add("-h");
                argList.add("h");
            } else if (arg.equals("-x") || arg.equals("--xml")) {
                // Translate TikaCLI xml output to TikaAsyncCLI handler type
                argList.add("-h");
                argList.add("x");
            } else if (arg.equals("-J") || arg.equals("--jsonRecursive")) {
                // TikaAsyncCLI always outputs JSON with recursive metadata (RMETA mode)
                // This is already the default, so we just skip this arg
            } else {
                argList.add(args[i]);
            }
        }
        return argList.toArray(new String[0]);
    }
}
