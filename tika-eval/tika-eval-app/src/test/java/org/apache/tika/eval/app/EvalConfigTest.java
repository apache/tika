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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.net.URISyntaxException;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.junit.jupiter.api.Test;

public class EvalConfigTest {

    @Test
    public void testBasic() throws Exception {
        EvalConfig evalConfig = EvalConfig.load(getConfig("eval-config-basic.json"));
        assertEquals(20000, evalConfig.getMaxExtractLength());
        assertNull(evalConfig.getErrorLogFile());
        assertNull(evalConfig.getJdbcString());
    }

    private Path getConfig(String fileName) throws URISyntaxException {
        return Paths.get(EvalConfigTest.class.getResource("/eval-configs/" + fileName).toURI());
    }
}
