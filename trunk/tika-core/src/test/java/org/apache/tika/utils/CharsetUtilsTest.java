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
package org.apache.tika.utils;

import junit.framework.TestCase;

public class CharsetUtilsTest extends TestCase {

    public void testInvalidCharset() {
        assertFalse(CharsetUtils.isSupported(" utf-8"));
        assertFalse(CharsetUtils.isSupported("my charset name"));
        assertFalse(CharsetUtils.isSupported("charset1; charset2"));
        assertFalse(CharsetUtils.isSupported(null));
        assertFalse(CharsetUtils.isSupported(""));
    }
    
    public void testValidCharset() {
        assertTrue(CharsetUtils.isSupported("UTF-8"));
        assertFalse(CharsetUtils.isSupported("bogus"));
    }
    
    public void testCleaningCharsetName() {
        assertEquals("UTF-8", CharsetUtils.clean("utf-8"));
        assertEquals(null, CharsetUtils.clean(""));
        assertEquals(null, CharsetUtils.clean(null));
        assertEquals("US-ASCII", CharsetUtils.clean(" us-ascii  "));
        assertEquals("UTF-8", CharsetUtils.clean("\"utf-8\""));
        assertEquals("ISO-8859-1", CharsetUtils.clean("ISO-8859-1, latin1"));
    }
    
    public void testFunkyNames() {
        assertEquals(null, CharsetUtils.clean("none"));
        assertEquals(null, CharsetUtils.clean("no"));
        
        assertEquals("UTF-8", CharsetUtils.clean("utf-8>"));
        
        assertEquals("ISO-8859-1", CharsetUtils.clean("iso-8851-1"));
        assertEquals("ISO-8859-15", CharsetUtils.clean("8859-15"));
        
        assertEquals("windows-1251", CharsetUtils.clean("cp-1251"));
        assertEquals("windows-1251", CharsetUtils.clean("win1251"));
        assertEquals("windows-1251", CharsetUtils.clean("WIN-1251"));
        assertEquals("windows-1251", CharsetUtils.clean("win-1251"));
        assertEquals("windows-1252", CharsetUtils.clean("Windows"));
        
        assertEquals("KOI8-R", CharsetUtils.clean("koi8r"));
    }

}
