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

import java.util.ArrayList;
import java.util.List;

import org.junit.Ignore;
import org.junit.Test;

public class TikaEvalCLITest {

    @Test
    @Ignore("TODO: add real tests")
    public void testBasicCompare() throws Exception {
        List<String> args = new ArrayList<>();
        args.add("Compare");
        args.add("-extractsA");
        args.add("watson2");
        args.add("-extractsB");
        args.add("tika");
        args.add("-db");
        args.add("comparedb");
        args.add("-drop");
        args.add("-jdbc");
        args.add("jdbc:postgresql:tika_eval?user=user&password=password");
        args.add("-maxFilesToAdd");
        args.add("100");
        TikaEvalCLI.main(args.toArray(new String[args.size()]));
    }

    @Ignore("TODO: add real tests")
    @Test
    public void testBasicProfile() throws Exception {
        List<String> args = new ArrayList<>();
        args.add("Profile");
        args.add("-extracts");
        args.add("watson2");
        args.add("-db");
        args.add("testdb");
        TikaEvalCLI.main(args.toArray(new String[args.size()]));
    }

    @Test
    @Ignore("TODO: add real tests")
    public void testReports() throws Exception {
        List<String> args = new ArrayList<>();
        args.add("Report");
        args.add("-db");
        args.add("comparedb.mv.db");

        TikaEvalCLI.main(args.toArray(new String[args.size()]));
    }

}
