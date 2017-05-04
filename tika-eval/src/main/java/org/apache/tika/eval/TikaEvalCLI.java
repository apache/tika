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
package org.apache.tika.eval;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.ParseException;
import org.apache.tika.batch.fs.FSBatchProcessCLI;
import org.apache.tika.eval.reports.ResultsReporter;
import org.h2.tools.Console;

public class TikaEvalCLI {
    static final String[] tools = {"Profile", "Compare", "Report", "StartDB"};

    private static String specifyTools() {
        StringBuilder sb = new StringBuilder();
        sb.append("Must specify one of the following tools in the first parameter:\n");
        for (String s : tools) {
            sb.append(s+"\n");
        }
        return sb.toString();

    }

    private void execute(String[] args) throws Exception {
        String tool = args[0];
        String[] subsetArgs = new String[args.length-1];
        System.arraycopy(args, 1, subsetArgs, 0, args.length - 1);
        if (tool.equals("Report")) {
            handleReport(subsetArgs);
        } else if (tool.equals("Compare")) {
            handleCompare(subsetArgs);
        } else if (tool.equals("Profile")) {
            handleProfile(subsetArgs);
        } else if (tool.equals("StartDB")) {
            handleStartDB(subsetArgs);
        } else {
            System.out.println(specifyTools());
        }
    }

    private void handleStartDB(String[] args) throws SQLException {
        List<String> argList = new ArrayList<>();
        argList.add("-web");
        Console.main(argList.toArray(new String[argList.size()]));
        while(true) {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e){
                break;
            }
        }
    }

    private void handleProfile(String[] subsetArgs) throws Exception {
        List<String> argList = new ArrayList(Arrays.asList(subsetArgs));

        boolean containsBC = false;
        String inputDir = null;
        String extracts = null;
        String alterExtract = null;
        //confirm there's a batch-config file
        for (int i = 0; i < argList.size(); i++) {
            String arg = argList.get(i);
            if (arg.equals("-bc")) {
                containsBC = true;
            } else if (arg.equals("-inputDir")) {
                if (i+1 >= argList.size()) {
                    System.err.println("Must specify directory after -inputDir");
                    ExtractProfiler.USAGE();
                    return;
                }
                inputDir = argList.get(i+1);
                i++;
            } else if (arg.equals("-extracts")) {
                if (i+1 >= argList.size()) {
                    System.err.println("Must specify directory after -extracts");
                    ExtractProfiler.USAGE();
                    return;
                }
                extracts = argList.get(i+1);
                i++;
            } else if (arg.equals("-alterExtract")) {
                if (i+1 >= argList.size()) {
                    System.err.println("Must specify type 'as_is', 'first_only' or " +
                            "'concatenate_content' after -alterExtract");
                    ExtractComparer.USAGE();
                    return;
                }
                alterExtract = argList.get(i+1);
                i++;
            }
        }

        if (alterExtract != null && !alterExtract.equals("as_is") &&
                !alterExtract.equals("concatenate_content") &&
                !alterExtract.equals("first_only")) {
            System.out.println("Sorry, I don't understand:"+alterExtract+
                    ". The values must be one of: as_is, first_only, concatenate_content");
            ExtractProfiler.USAGE();
            return;
        }

        //need to specify each in this commandline
        //if only extracts is passed to tika-batch,
        //the crawler will see no inputDir and start crawling "input".
        //this allows the user to specify either extracts or inputDir
        if (extracts == null && inputDir != null) {
            argList.add("-extracts");
            argList.add(inputDir);
        } else if (inputDir == null && extracts != null) {
            argList.add("-inputDir");
            argList.add(extracts);
        }

        Path tmpBCConfig = null;
        try {
            tmpBCConfig = Files.createTempFile("tika-eval-profiler", ".xml");
            if (! containsBC) {
                Files.copy(
                        this.getClass().getResourceAsStream("/tika-eval-profiler-config.xml"),
                        tmpBCConfig, StandardCopyOption.REPLACE_EXISTING);
                argList.add("-bc");
                argList.add(tmpBCConfig.toAbsolutePath().toString());
            }

            String[] updatedArgs = argList.toArray(new String[argList.size()]);
            DefaultParser defaultCLIParser = new DefaultParser();
            try {
                CommandLine commandLine = defaultCLIParser.parse(ExtractProfiler.OPTIONS, updatedArgs);
                if (commandLine.hasOption("db") && commandLine.hasOption("jdbc")) {
                    System.out.println("Please specify either the default -db or the full -jdbc, not both");
                    ExtractProfiler.USAGE();
                    return;
                }
            } catch (ParseException e) {
                System.out.println(e.getMessage()+"\n");
                ExtractProfiler.USAGE();
                return;
            }

            FSBatchProcessCLI.main(updatedArgs);
        } finally {
            if (tmpBCConfig != null && Files.isRegularFile(tmpBCConfig)) {
                Files.delete(tmpBCConfig);
            }
        }
    }

    private void handleCompare(String[] subsetArgs) throws Exception{
        List<String> argList = new ArrayList(Arrays.asList(subsetArgs));

        boolean containsBC = false;
        String inputDir = null;
        String extractsA = null;
        String alterExtract = null;
        //confirm there's a batch-config file
        for (int i = 0; i < argList.size(); i++) {
            String arg = argList.get(i);
            if (arg.equals("-bc")) {
                containsBC = true;
            } else if (arg.equals("-inputDir")) {
                if (i+1 >= argList.size()) {
                    System.err.println("Must specify directory after -inputDir");
                    ExtractComparer.USAGE();
                    return;
                }
                inputDir = argList.get(i+1);
                i++;
            } else if (arg.equals("-extractsA")) {
                if (i+1 >= argList.size()) {
                    System.err.println("Must specify directory after -extractsA");
                    ExtractComparer.USAGE();
                    return;
                }
                extractsA = argList.get(i+1);
                i++;
            } else if (arg.equals("-alterExtract")) {
                if (i+1 >= argList.size()) {
                    System.err.println("Must specify type 'as_is', 'first_only' or " +
                            "'concatenate_content' after -alterExtract");
                    ExtractComparer.USAGE();
                    return;
                }
                alterExtract = argList.get(i+1);
                i++;
            }
        }
        if (alterExtract != null && !alterExtract.equals("as_is") &&
                !alterExtract.equals("concatenate_content") &&
                !alterExtract.equals("first_only")) {
            System.out.println("Sorry, I don't understand:"+alterExtract+
            ". The values must be one of: as_is, first_only, concatenate_content");
            ExtractComparer.USAGE();
            return;
        }

        //need to specify each in the commandline that goes into tika-batch
        //if only extracts is passed to tika-batch,
        //the crawler will see no inputDir and start crawling "input".
        //if the user doesn't specify inputDir, crawl extractsA
        if (inputDir == null && extractsA != null) {
            argList.add("-inputDir");
            argList.add(extractsA);
        }

        Path tmpBCConfig = null;
        try {
            tmpBCConfig = Files.createTempFile("tika-eval", ".xml");
            if (! containsBC) {
                Files.copy(
                        this.getClass().getResourceAsStream("/tika-eval-comparison-config.xml"),
                        tmpBCConfig, StandardCopyOption.REPLACE_EXISTING);
                argList.add("-bc");
                argList.add(tmpBCConfig.toAbsolutePath().toString());

            }
            String[] updatedArgs = argList.toArray(new String[argList.size()]);
            DefaultParser defaultCLIParser = new DefaultParser();
            try {
                CommandLine commandLine = defaultCLIParser.parse(ExtractComparer.OPTIONS, updatedArgs);
                if (commandLine.hasOption("db") && commandLine.hasOption("jdbc")) {
                    System.out.println("Please specify either the default -db or the full -jdbc, not both");
                    ExtractComparer.USAGE();
                    return;
                }
            } catch (ParseException e) {
                System.out.println(e.getMessage()+"\n");
                ExtractComparer.USAGE();
                return;
            }

            FSBatchProcessCLI.main(updatedArgs);
        } finally {
            if (tmpBCConfig != null && Files.isRegularFile(tmpBCConfig)) {
                Files.delete(tmpBCConfig);
            }
        }
    }

    private void handleReport(String[] subsetArgs) throws Exception {
        ResultsReporter.main(subsetArgs);
    }

    public static void main(String[] args) throws Exception {
        TikaEvalCLI cli = new TikaEvalCLI();
        if (args.length == 0) {
            System.err.println(specifyTools());
            return;
        }
        cli.execute(args);
    }
}
