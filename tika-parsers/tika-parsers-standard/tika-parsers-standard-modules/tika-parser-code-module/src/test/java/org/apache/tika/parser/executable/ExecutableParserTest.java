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
package org.apache.tika.parser.executable;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import org.apache.tika.TikaTest;
import org.apache.tika.metadata.MachineMetadata.Endian;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;

public class ExecutableParserTest extends TikaTest {

    @Test
    public void testWin32Parser() throws Exception {
        XMLResult r = getXML("testWindows-x86-32.exe");
        Metadata metadata = r.metadata;

        assertEquals("application/x-msdownload", metadata.get(Metadata.CONTENT_TYPE));
        assertEquals("2012-05-13T13:40:11Z", metadata.get(TikaCoreProperties.CREATED));

        assertEquals(ExecutableParser.MACHINE_x86_32, metadata.get(ExecutableParser.MACHINE_TYPE));
        assertEquals("Little", metadata.get(ExecutableParser.ENDIAN));
        assertEquals("32", metadata.get(ExecutableParser.ARCHITECTURE_BITS));
        assertEquals("Windows", metadata.get(ExecutableParser.PLATFORM));
        assertContains("<body />", r.xml); //no text yet

    }

    @Test
    public void testElfParser_x86_32() throws Exception {
        XMLResult r = getXML("testLinux-x86-32");
        Metadata metadata = r.metadata;
        assertEquals("application/x-executable", metadata.get(Metadata.CONTENT_TYPE));

        assertEquals(ExecutableParser.MACHINE_x86_32, metadata.get(ExecutableParser.MACHINE_TYPE));
        assertEquals("Little", metadata.get(ExecutableParser.ENDIAN));
        assertEquals("32", metadata.get(ExecutableParser.ARCHITECTURE_BITS));

//         assertEquals("Linux",
//               metadata.get(ExecutableParser.PLATFORM));

        assertContains("<body />", r.xml);

    }

    @Test
    public void testMachOParser_x86_64() throws Exception {
        XMLResult r = getXML("testMacOS-x86_64");
        Metadata metadata = r.metadata;
        assertEquals("application/x-mach-o-executable", metadata.get(Metadata.CONTENT_TYPE));

        assertEquals(Endian.LITTLE.getName(), metadata.get(ExecutableParser.ENDIAN));
        assertEquals(ExecutableParser.MACHINE_x86_64, metadata.get(ExecutableParser.MACHINE_TYPE));
        assertEquals("64", metadata.get(ExecutableParser.ARCHITECTURE_BITS));

        assertContains("<body />", r.xml);
    }

    @Test
    public void testMachOParser_arm64() throws Exception {
        XMLResult r = getXML("testMacOS-arm64");
        Metadata metadata = r.metadata;
        assertEquals("application/x-mach-o-executable", metadata.get(Metadata.CONTENT_TYPE));

        assertEquals(Endian.LITTLE.getName(), metadata.get(ExecutableParser.ENDIAN));
        assertEquals(ExecutableParser.MACHINE_ARM, metadata.get(ExecutableParser.MACHINE_TYPE));
        assertEquals("64", metadata.get(ExecutableParser.ARCHITECTURE_BITS));

        assertContains("<body />", r.xml);
    }

}
