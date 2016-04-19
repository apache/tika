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
package org.apache.tika.batch.fs;

import static org.junit.Assert.assertTrue;

import java.nio.file.Path;
import java.nio.file.Paths;

import org.junit.Test;

public class FSUtilTest {

    @Test
    public void testSafeResolution() throws Exception {
        Path cwd = Paths.get(".");
        String windows = "C:/temp/file.txt";
        String linux = "/root/dir/file.txt";
        boolean ex = false;
        try {
            FSUtil.resolveRelative(cwd, windows);
        } catch (IllegalArgumentException e) {
            ex = true;
        }

        try {
            FSUtil.resolveRelative(cwd, linux);
        } catch (IllegalArgumentException e) {
            ex = true;
        }

        assertTrue("IllegalArgumentException should have been thrown", ex);
    }

}
