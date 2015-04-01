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

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * This takes a TikaCLI commandline and builds the full commandline for
 * org.apache.tika.batch.fs.FSBatchProcessCLI.
 * <p>
 * The "default" batch config file that this relies on
 * if no batch config file is specified on the commandline
 * is: tika-batch/src/main/resources/.../default-tika-batch-config.xml
 */
class BatchCommandLineBuilder {

    static Pattern JVM_OPTS_PATTERN = Pattern.compile("^(--?)J(.+)");

    protected static String[] build(String[] args) throws IOException {
        Map<String, String> processArgs = new LinkedHashMap<String, String>();
        Map<String, String> jvmOpts = new LinkedHashMap<String,String>();
        //take the args, and divide them into process args and options for
        //the child jvm process (i.e. log files, etc)
        mapifyArgs(args, processArgs, jvmOpts);

        //now modify processArgs in place
        translateCommandLine(args, processArgs);

        //maybe the user specified a different classpath?!
        if (! jvmOpts.containsKey("-cp") && ! jvmOpts.containsKey("--classpath")) {
            String cp = System.getProperty("java.class.path");
            //need to test for " " on *nix, can't just add double quotes
            //across platforms.
            if (cp.contains(" ")){
                cp = "\""+cp+"\"";
            }
            jvmOpts.put("-cp", cp);
        }

        boolean hasLog4j = false;
        for (String k : jvmOpts.keySet()) {
            if (k.startsWith("-Dlog4j.configuration=")) {
                hasLog4j = true;
                break;
            }
        }
        //use the log4j config file inside the app /resources/log4j_batch_process.properties
        if (! hasLog4j) {
            jvmOpts.put("-Dlog4j.configuration=\"log4j_batch_process.properties\"", "");
        }
        //now build the full command line
        List<String> fullCommand = new ArrayList<String>();
        fullCommand.add("java");
        for (Map.Entry<String, String> e : jvmOpts.entrySet()) {
            fullCommand.add(e.getKey());
            if (e.getValue().length() > 0) {
                fullCommand.add(e.getValue());
            }
        }
        fullCommand.add("org.apache.tika.batch.fs.FSBatchProcessCLI");
        //now add the process commands
        for (Map.Entry<String, String> e : processArgs.entrySet()) {
            fullCommand.add(e.getKey());
            if (e.getValue().length() > 0) {
                fullCommand.add(e.getValue());
            }
        }
        return fullCommand.toArray(new String[fullCommand.size()]);
    }


    /**
     * Take the input args and separate them into args that belong on the commandline
     * and those that belong as jvm args for the child process.
     * @param args -- literal args from TikaCLI commandline
     * @param commandLine args that should be part of the batch commandline
     * @param jvmArgs args that belong as jvm arguments for the child process
     */
    private static void mapifyArgs(final String[] args,
                                   final Map<String, String> commandLine,
                                   final Map<String, String> jvmArgs) {

        if (args.length == 0) {
            return;
        }

        Matcher matcher = JVM_OPTS_PATTERN.matcher("");
        for (int i = 0; i < args.length; i++) {
            if (matcher.reset(args[i]).find()) {
                String jvmArg = matcher.group(1)+matcher.group(2);
                String v = "";
                if (i < args.length-1 && ! args[i+1].startsWith("-")){
                    v = args[i+1];
                    i++;
                }
                jvmArgs.put(jvmArg, v);
            } else if (args[i].startsWith("-")) {
                String k = args[i];
                String v = "";
                if (i < args.length-1 && ! args[i+1].startsWith("-")){
                    v = args[i+1];
                    i++;
                }
                commandLine.put(k, v);
            }
        }
    }

    private static void translateCommandLine(String[] args, Map<String, String> map) throws IOException {
        //if there are only two args and they are both directories, treat the first
        //as input and the second as output.
        if (args.length == 2 && !args[0].startsWith("-") && ! args[1].startsWith("-")) {
            File candInput = new File(args[0]);
            File candOutput = new File(args[1]);
            if (candOutput.isFile()) {
                throw new IllegalArgumentException("Can't specify an existing file as the "+
                "second argument for the output directory of a batch process");
            }

            if (candInput.isDirectory()){
                map.put("-inputDir", args[0]);
                map.put("-outputDir", args[1]);
            }
        }
        //look for tikaConfig
        for (String arg : args) {
            if (arg.startsWith("--config=")) {
                String configPath = arg.substring("--config=".length());
                map.put("-c", configPath);
                break;
            }
        }
        //now translate output types
        if (map.containsKey("-h") || map.containsKey("--html")) {
            map.remove("-h");
            map.remove("--html");
            map.put("-basicHandlerType", "html");
            map.put("-outputSuffix", "html");
        } else if (map.containsKey("-x") || map.containsKey("--xml")) {
            map.remove("-x");
            map.remove("--xml");
            map.put("-basicHandlerType", "xml");
            map.put("-outputSuffix", "xml");
        } else if (map.containsKey("-t") || map.containsKey("--text")) {
            map.remove("-t");
            map.remove("--text");
            map.put("-basicHandlerType", "text");
            map.put("-outputSuffix", "txt");
        } else if (map.containsKey("-m") || map.containsKey("--metadata")) {
            map.remove("-m");
            map.remove("--metadata");
            map.put("-basicHandlerType", "ignore");
            map.put("-outputSuffix", "json");
        } else if (map.containsKey("-T") || map.containsKey("--text-main")) {
            map.remove("-T");
            map.remove("--text-main");
            map.put("-basicHandlerType", "body");
            map.put("-outputSuffix", "txt");
        }

        if (map.containsKey("-J") || map.containsKey("--jsonRecursive")) {
            map.remove("-J");
            map.remove("--jsonRecursive");
            map.put("-recursiveParserWrapper", "true");
            //overwrite outputSuffix
            map.put("-outputSuffix", "json");
        }

        if (map.containsKey("--inputDir") || map.containsKey("-i")) {
            String v1 = map.remove("--inputDir");
            String v2 = map.remove("-i");
            String v = (v1 == null) ? v2 : v1;
            map.put("-inputDir", v);
        }

        if (map.containsKey("--outputDir") || map.containsKey("-o")) {
            String v1 = map.remove("--outputDir");
            String v2 = map.remove("-o");
            String v = (v1 == null) ? v2 : v1;
            map.put("-outputDir", v);
        }

    }
}
