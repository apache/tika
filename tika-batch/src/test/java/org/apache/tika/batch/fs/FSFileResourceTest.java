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

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.nio.file.Path;
import java.nio.file.Paths;

import org.junit.jupiter.api.Test;

public class FSFileResourceTest {
    @Test
    public void testRelativization() throws Exception {
        //test assertion error if alleged child is not actually child
        Path root = Paths.get("root/abc/def").toAbsolutePath();
        Path allegedChild = Paths.get(root.getParent().getParent().toAbsolutePath().toString());
        try {
            FSFileResource r = new FSFileResource(root, allegedChild);
            fail("should have had assertion error: alleged child not actually child of root");
        } catch (AssertionError e) {
            //swallow
        }

        //test regular workings
        root = Paths.get("root/abc/def");
        Path child = Paths.get(root.toString(), "ghi/jkl/lmnop.doc");
        FSFileResource r = new FSFileResource(root, child);
        String id = r.getResourceId();
        assertTrue(id.startsWith("ghi"));
        assertTrue(id.endsWith("lmnop.doc"));
    }
}
