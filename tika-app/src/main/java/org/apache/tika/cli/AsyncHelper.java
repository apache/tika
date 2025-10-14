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
import java.util.Arrays;
import java.util.List;


public class AsyncHelper {
    public static String[] translateArgs(String[] args) {
        List<String> argList = new ArrayList<>();
        if (args.length == 2) {
            if (args[0].startsWith("-Z")) {
                argList.add("-Z");
                argList.add("-i");
                argList.add(args[1]);
                argList.add("-o");
                argList.add(args[1]);
                return argList.toArray(new String[0]);
            } else if (args[0].startsWith("-") || args[1].startsWith("-")) {
                argList.add(args[0]);
                argList.add(args[1]);
                return argList.toArray(new String[0]);
            } else {
                argList.add("-i");
                argList.add(args[0]);
                argList.add("-o");
                argList.add(args[1]);
                return argList.toArray(new String[0]);
            }
        }
        if (args.length == 3) {
            if (args[0].equals("-Z") && ! args[1].startsWith("-") && ! args[2].startsWith("-")) {
                argList.add("-Z");
                argList.add("-i");
                argList.add(args[1]);
                argList.add("-o");
                argList.add(args[2]);
                return argList.toArray(new String[0]);
            }
        }
        argList.addAll(Arrays.asList(args));
        argList.remove("-a");
        return argList.toArray(new String[0]);
    }
}
