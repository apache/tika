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
package org.apache.tika.pipes.pipesiterator.filelist;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

import org.apache.tika.config.InitializableProblemHandler;
import org.apache.tika.pipes.FetchEmitTuple;

public class FileListPipesIteratorTest {

    @Test
    public void testBasic() throws Exception {
        Path p = Paths.get(this.getClass().getResource("/test-documents/file-list.txt").toURI());
        FileListPipesIterator it = new FileListPipesIterator();
        it.setFetcherName("f");
        it.setEmitterName("e");
        it.setFileList(p.toAbsolutePath().toString());
        it.setHasHeader(false);
        it.checkInitialization(InitializableProblemHandler.DEFAULT);
        List<String> lines = new ArrayList<>();

        for (FetchEmitTuple t : it) {
            assertEquals(t.getFetchKey().getFetchKey(), t.getEmitKey().getEmitKey());
            assertEquals(t.getId(), t.getEmitKey().getEmitKey());
            assertEquals("f", t.getFetchKey().getFetcherName());
            assertEquals("e", t.getEmitKey().getEmitterName());
            lines.add(t.getId());
        }
        assertEquals("the", lines.get(0));
        assertEquals(8, lines.size());
        assertFalse(lines.contains("quick"));
    }

    @Test
    public void testHasHeader() throws Exception {
        Path p = Paths.get(this.getClass().getResource("/test-documents/file-list.txt").toURI());
        FileListPipesIterator it = new FileListPipesIterator();
        it.setFetcherName("f");
        it.setEmitterName("e");
        it.setFileList(p.toAbsolutePath().toString());
        it.setHasHeader(true);
        it.checkInitialization(InitializableProblemHandler.DEFAULT);
        List<String> lines = new ArrayList<>();

        for (FetchEmitTuple t : it) {
            assertEquals(t.getFetchKey().getFetchKey(), t.getEmitKey().getEmitKey());
            assertEquals(t.getId(), t.getEmitKey().getEmitKey());
            assertEquals("f", t.getFetchKey().getFetcherName());
            assertEquals("e", t.getEmitKey().getEmitterName());
            lines.add(t.getId());
        }
        assertEquals("brown", lines.get(0));
        assertFalse(lines.contains("quick"));
        assertEquals(7, lines.size());
    }
}
