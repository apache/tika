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

package org.apache.tika.parser.wordperfect;

import static org.junit.Assert.fail;

import java.io.ByteArrayInputStream;
import java.io.EOFException;

import org.junit.Test;

public class WPInputStreamTest {
    //These test that we guarantee that a byte is read/skipped with the readWPX calls
    //but not with the regular read(), read(..), etc.

    @Test
    public void testReadByte() throws Exception {
        try (WPInputStream wpInputStream = emptyWPStream()) {
            wpInputStream.readWPByte();
            fail("should have thrown EOF");
        } catch (EOFException e) {

        }
    }


    @Test
    public void testReadShort() throws Exception {
        try (WPInputStream wpInputStream = emptyWPStream()) {
            wpInputStream.readWPShort();
            fail("should have thrown EOF");
        } catch (EOFException e) {

        }
    }


    @Test
    public void testReadChar() throws Exception {
        try (WPInputStream wpInputStream = emptyWPStream()) {
            wpInputStream.readWPChar();
            fail("should have thrown EOF");
        } catch (EOFException e) {

        }
    }

    @Test
    public void testReadHex() throws Exception {
        try (WPInputStream wpInputStream = emptyWPStream()) {
            wpInputStream.readWPHex();
            fail("should have thrown EOF");
        } catch (EOFException e) {

        }
    }

    @Test
    public void testReadHexString() throws Exception {
        try (WPInputStream wpInputStream = emptyWPStream()) {
            wpInputStream.readWPHexString(10);
            fail("should have thrown EOF");
        } catch (EOFException e) {

        }
    }

    @Test
    public void testReadLong() throws Exception {
        try (WPInputStream wpInputStream = emptyWPStream()) {
            wpInputStream.readWPLong();
            fail("should have thrown EOF");
        } catch (EOFException e) {

        }
    }


    @Test
    public void testReadString() throws Exception {
        try (WPInputStream wpInputStream = emptyWPStream()) {
            wpInputStream.readWPString(10);
            fail("should have thrown EOF");
        } catch (EOFException e) {

        }
    }

    @Test
    public void testReadArr() throws Exception {
        try (WPInputStream wpInputStream = emptyWPStream()) {
            byte[] buffer = new byte[10];
            wpInputStream.read(buffer);
        } catch (EOFException e) {
            fail("should not have thrown EOF");
        }
    }

    @Test
    public void testReadArrOffset() throws Exception {
        try (WPInputStream wpInputStream = emptyWPStream()) {
            byte[] buffer = new byte[10];
            wpInputStream.read(buffer, 0, 2);
        } catch (EOFException e) {
            fail("should not have thrown EOF");
        }
    }

    private WPInputStream emptyWPStream() {
        return new WPInputStream(new ByteArrayInputStream(new byte[0]));
    }
}
