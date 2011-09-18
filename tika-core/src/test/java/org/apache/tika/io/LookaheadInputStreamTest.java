/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.tika.io;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

import junit.framework.TestCase;

/**
 * Test cases for the {@link LookaheadInputStream} class.
 */
public class LookaheadInputStreamTest extends TestCase {

    public void testNullStream() throws IOException {
        InputStream lookahead = new LookaheadInputStream(null, 100);
        assertEquals(-1, lookahead.read());
    }

    public void testEmptyStream() throws IOException {
        InputStream stream = new ByteArrayInputStream(new byte[0]);
        InputStream lookahead = new LookaheadInputStream(stream, 100);
        assertEquals(-1, lookahead.read());
        lookahead.close();
        assertEquals(-1, stream.read());
    }

    public void testBasicLookahead() throws IOException {
        InputStream stream =
            new ByteArrayInputStream(new byte[] { 'a', 'b', 'c' });
        InputStream lookahead = new LookaheadInputStream(stream, 2);
        assertEquals('a', lookahead.read());
        assertEquals('b', lookahead.read());
        assertEquals(-1, lookahead.read());
        lookahead.close();
        assertEquals('a', stream.read());
        assertEquals('b', stream.read());
        assertEquals('c', stream.read());
        assertEquals(-1, stream.read());
    }

    public void testZeroLookahead() throws IOException {
        InputStream stream =
            new ByteArrayInputStream(new byte[] { 'a', 'b', 'c' });
        InputStream lookahead = new LookaheadInputStream(stream, 0);
        assertEquals(-1, lookahead.read());
        lookahead.close();
        assertEquals('a', stream.read());
        assertEquals('b', stream.read());
        assertEquals('c', stream.read());
        assertEquals(-1, stream.read());
    }

    public void testMarkLookahead() throws IOException {
        InputStream stream =
            new ByteArrayInputStream(new byte[] { 'a', 'b', 'c' });
        InputStream lookahead = new LookaheadInputStream(stream, 2);
        lookahead.mark(1);
        assertEquals('a', lookahead.read());
        lookahead.reset();
        assertEquals('a', lookahead.read());
        lookahead.mark(2);
        assertEquals('b', lookahead.read());
        assertEquals(-1, lookahead.read());
        lookahead.reset();
        assertEquals('b', lookahead.read());
        assertEquals(-1, lookahead.read());
        lookahead.close();
        assertEquals('a', stream.read());
        assertEquals('b', stream.read());
        assertEquals('c', stream.read());
        assertEquals(-1, stream.read());
    }

    public void testSkipLookahead() throws IOException {
        InputStream stream =
            new ByteArrayInputStream(new byte[] { 'a', 'b', 'c' });
        InputStream lookahead = new LookaheadInputStream(stream, 2);
        assertEquals(1, lookahead.skip(1));
        assertEquals('b', lookahead.read());
        assertEquals(0, lookahead.skip(1));
        assertEquals(-1, lookahead.read());
        lookahead.close();
        assertEquals('a', stream.read());
        assertEquals('b', stream.read());
        assertEquals('c', stream.read());
        assertEquals(-1, stream.read());
    }

}
