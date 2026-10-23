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
package org.apache.tika.config.loader;

import java.util.ArrayList;
import java.util.List;

import org.apache.tika.annotation.TikaComponent;
import org.apache.tika.parser.inference.TextChunker;

/** Cuts text into fixed-size pieces; a stand-in for a real chunker in loader tests. */
@TikaComponent(name = "test-chunker", spi = false)
public class TestChunker implements TextChunker {

    private int size = 4;

    public int getSize() {
        return size;
    }

    public void setSize(int size) {
        this.size = size;
    }

    @Override
    public List<int[]> spans(String text) {
        List<int[]> spans = new ArrayList<>();
        for (int start = 0; start < text.length(); start += size) {
            spans.add(new int[]{start, Math.min(start + size, text.length())});
        }
        return spans;
    }
}
