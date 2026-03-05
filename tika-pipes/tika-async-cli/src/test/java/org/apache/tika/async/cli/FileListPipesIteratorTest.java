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
package org.apache.tika.async.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import org.apache.tika.pipes.api.FetchEmitTuple;

public class FileListPipesIteratorTest {

    @TempDir
    Path tempDir;

    @Test
    public void testBasicFileList() throws Exception {
        Path fileList = tempDir.resolve("files.txt");
        Files.writeString(fileList, "doc1.pdf\nsubdir/doc2.txt\ndoc3.html\n");

        FileListPipesIterator iter = new FileListPipesIterator(fileList, tempDir);
        List<FetchEmitTuple> tuples = new ArrayList<>();
        iter.iterator().forEachRemaining(tuples::add);

        assertEquals(3, tuples.size());
        assertEquals("doc1.pdf", tuples.get(0).getFetchKey().getFetchKey());
        assertEquals("subdir/doc2.txt", tuples.get(1).getFetchKey().getFetchKey());
        assertEquals("doc3.html", tuples.get(2).getFetchKey().getFetchKey());

        assertEquals("fsf", tuples.get(0).getFetchKey().getFetcherId());
        assertEquals("fse", tuples.get(0).getEmitKey().getEmitterId());
    }

    @Test
    public void testSkipsBlankLinesAndComments() throws Exception {
        Path fileList = tempDir.resolve("files.txt");
        Files.writeString(fileList, "doc1.pdf\n\n# this is a comment\n  \ndoc2.pdf\n");

        FileListPipesIterator iter = new FileListPipesIterator(fileList, null);
        List<FetchEmitTuple> tuples = new ArrayList<>();
        iter.iterator().forEachRemaining(tuples::add);

        assertEquals(2, tuples.size());
        assertEquals("doc1.pdf", tuples.get(0).getFetchKey().getFetchKey());
        assertEquals("doc2.pdf", tuples.get(1).getFetchKey().getFetchKey());
    }

    @Test
    public void testCallReturnsCount() throws Exception {
        Path fileList = tempDir.resolve("files.txt");
        Files.writeString(fileList, "a.pdf\nb.pdf\n# comment\n\nc.pdf\n");

        FileListPipesIterator iter = new FileListPipesIterator(fileList, null);
        assertEquals(3, iter.call());
    }

    @Test
    public void testEmptyFile() throws Exception {
        Path fileList = tempDir.resolve("empty.txt");
        Files.writeString(fileList, "");

        FileListPipesIterator iter = new FileListPipesIterator(fileList, null);
        List<FetchEmitTuple> tuples = new ArrayList<>();
        iter.iterator().forEachRemaining(tuples::add);

        assertEquals(0, tuples.size());
        assertEquals(0, iter.call());
    }

    @Test
    public void testTrimsWhitespace() throws Exception {
        Path fileList = tempDir.resolve("files.txt");
        Files.writeString(fileList, "  doc1.pdf  \n  doc2.pdf\n");

        FileListPipesIterator iter = new FileListPipesIterator(fileList, null);
        List<FetchEmitTuple> tuples = new ArrayList<>();
        iter.iterator().forEachRemaining(tuples::add);

        assertEquals(2, tuples.size());
        assertEquals("doc1.pdf", tuples.get(0).getFetchKey().getFetchKey());
        assertEquals("doc2.pdf", tuples.get(1).getFetchKey().getFetchKey());
    }
}
