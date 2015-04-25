package org.apache.tika.batch;

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

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;

import org.apache.commons.cli.Options;
import org.apache.tika.batch.builders.CommandLineParserBuilder;
import org.apache.tika.batch.fs.FSBatchTestBase;
import org.apache.tika.io.IOUtils;
import org.junit.Test;


public class CommandLineParserBuilderTest extends FSBatchTestBase {

    @Test
    public void testBasic() throws Exception {
        String configFile = this.getClass().getResource(
                "/tika-batch-config-test.xml").getFile();
        InputStream is = null;
        try {
            is = new FileInputStream(new File(configFile));
            CommandLineParserBuilder builder = new CommandLineParserBuilder();
            Options options = builder.build(is);
            //TODO: insert actual tests :)
        } finally {
            IOUtils.closeQuietly(is);
        }

    }
}
