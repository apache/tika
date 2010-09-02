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
package org.apache.tika.language;

import java.io.IOException;

import junit.framework.TestCase;

public class ProfilingWriterTest extends TestCase {

    public void testProfilingWriter() throws IOException {
        ProfilingWriter writer = new ProfilingWriter();
        writer.write(" foo+BAR FooBar\n");
        writer.close();

        LanguageProfile profile = writer.getProfile();
        assertEquals(2, profile.getCount("_fo"));
        assertEquals(2, profile.getCount("foo"));
        assertEquals(1, profile.getCount("oo_"));
        assertEquals(1, profile.getCount("oob"));
        assertEquals(1, profile.getCount("oba"));
        assertEquals(1, profile.getCount("_ba"));
        assertEquals(2, profile.getCount("bar"));
        assertEquals(2, profile.getCount("ar_"));
    }

}
