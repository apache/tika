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
package org.apache.tika.parser.chm;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import org.apache.tika.parser.chm.accessor.DirectoryListingEntry;
import org.junit.Before;
import org.junit.Test;

/**
 * Tests public methods of the DirectoryListingEntry class
 * 
 * @author olegt
 * 
 */
public class TestDirectoryListingEntry {
    private DirectoryListingEntry dle = null;

    @Before
    public void setUp() throws Exception {
        dle = new DirectoryListingEntry(TestParameters.nameLength,
                TestParameters.entryName, TestParameters.entryType,
                TestParameters.offset, TestParameters.length);
    }

    @Test
    public void testDefaultConstructor() {
        assertNotNull(dle);
    }

    @Test
    public void testParamConstructor() {
        assertEquals(TestParameters.nameLength, dle.getNameLength());
        assertEquals(TestParameters.entryName, dle.getName());
        assertEquals(TestParameters.entryType, dle.getEntryType());
        assertEquals(TestParameters.offset, dle.getOffset());
        assertEquals(TestParameters.length, dle.getLength());
    }

    @Test
    public void testToString() {
        assertNotNull(dle.toString());
    }

    @Test
    public void testGetNameLength() {
        assertEquals(TestParameters.nameLength, dle.getNameLength());
    }

    @Test
    public void testGetName() {
        assertEquals(TestParameters.entryName, dle.getName());
    }

    @Test
    public void testGetEntryType() {
        assertEquals(TestParameters.entryType, dle.getEntryType());
    }

    @Test
    public void testGetOffset() {
        assertEquals(TestParameters.offset, dle.getOffset());
    }

    @Test
    public void testGetLength() {
        assertEquals(TestParameters.length, dle.getLength());
    }
}
