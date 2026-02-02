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
package org.apache.tika.cli;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

import org.junit.jupiter.api.Test;

public class AsyncHelperTest {

    @Test
    public void testBasic() throws Exception {
        String[] args = new String[]{"-a", "--config=blah.json", "-i", "input.docx", "-o", "output/dir"};
        String[] expected = new String[]{"-c", "blah.json", "-i", "input.docx", "-o", "output/dir"};
        assertArrayEquals(expected, AsyncHelper.translateArgs(args));
    }

    @Test
    public void testTextHandler() throws Exception {
        String[] args = new String[]{"-t", "input", "output"};
        String[] expected = new String[]{"-h", "t", "input", "output"};
        assertArrayEquals(expected, AsyncHelper.translateArgs(args));
    }

    @Test
    public void testTextHandlerLong() throws Exception {
        String[] args = new String[]{"--text", "input", "output"};
        String[] expected = new String[]{"-h", "t", "input", "output"};
        assertArrayEquals(expected, AsyncHelper.translateArgs(args));
    }

    @Test
    public void testHtmlHandler() throws Exception {
        String[] args = new String[]{"--html", "input", "output"};
        String[] expected = new String[]{"-h", "h", "input", "output"};
        assertArrayEquals(expected, AsyncHelper.translateArgs(args));
    }

    @Test
    public void testXmlHandler() throws Exception {
        String[] args = new String[]{"-x", "input", "output"};
        String[] expected = new String[]{"-h", "x", "input", "output"};
        assertArrayEquals(expected, AsyncHelper.translateArgs(args));
    }

    @Test
    public void testJsonRecursiveSkipped() throws Exception {
        // -J is the default in async mode, so it's just skipped
        String[] args = new String[]{"-J", "-t", "input", "output"};
        String[] expected = new String[]{"-h", "t", "input", "output"};
        assertArrayEquals(expected, AsyncHelper.translateArgs(args));
    }

    @Test
    public void testBatchModeWithOptions() throws Exception {
        String[] args = new String[]{"-J", "-t", "/path/to/input", "/path/to/output"};
        String[] expected = new String[]{"-h", "t", "/path/to/input", "/path/to/output"};
        assertArrayEquals(expected, AsyncHelper.translateArgs(args));
    }
}
