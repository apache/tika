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
package org.apache.tika.eval.app;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import org.h2.tools.Console;

import org.apache.tika.eval.app.reports.ResultsReporter;

public class TikaEvalCLI {
    static final String[] tools = {"Profile", "Compare", "Report", "StartDB"};

    private static String specifyTools() {
        StringBuilder sb = new StringBuilder();
        sb.append("Must specify one of the following tools in the first parameter:\n");
        for (String s : tools) {
            sb
                    .append(s)
                    .append("\n");
        }
        return sb.toString();

    }

    public static void main(String[] args) throws Exception {
        TikaEvalCLI cli = new TikaEvalCLI();
        if (args.length == 0) {
            System.err.println(specifyTools());
            return;
        }
        cli.execute(args);
    }

    private void execute(String[] args) throws Exception {
        String tool = args[0];
        String[] subsetArgs = new String[args.length - 1];
        System.arraycopy(args, 1, subsetArgs, 0, args.length - 1);
        switch (tool) {
            case "Report":
                handleReport(subsetArgs);
                break;
            case "Compare":
                handleCompare(subsetArgs);
                break;
            case "Profile":
                handleProfile(subsetArgs);
                break;
            case "StartDB":
                handleStartDB(subsetArgs);
                break;

            default:
                System.out.println(specifyTools());
                break;
        }
    }

    private void handleStartDB(String[] args) throws SQLException {
        List<String> argList = new ArrayList<>();
        argList.add("-web");
        Console.main(argList.toArray(new String[0]));
        while (true) {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                break;
            }
        }
    }

    private void handleProfile(String[] subsetArgs) throws Exception {
        ExtractProfileRunner.main(subsetArgs);
    }

    private void handleCompare(String[] subsetArgs) throws Exception {
        ExtractComparerRunner.main(subsetArgs);
    }

    private void handleReport(String[] subsetArgs) throws Exception {
        ResultsReporter.main(subsetArgs);
    }
}
