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

public class LanguageProfileTest extends TestCase {

    public void testLanguageProfile() throws IOException {
        LanguageProfile foo = new LanguageProfile();
        assertEquals(0, foo.getCount("foo"));

        foo.add("foo");
        assertEquals(1, foo.getCount("foo"));

        foo.add("foo", 3);
        assertEquals(4, foo.getCount("foo"));

        LanguageProfile bar = new LanguageProfile();
        assertEquals(1.0, foo.distance(bar));

        bar.add("bar");
        assertEquals(Math.sqrt(2.0), foo.distance(bar));

        bar.add("bar", 3);
        assertEquals(Math.sqrt(2.0), foo.distance(bar));

        LanguageProfile foobar = new LanguageProfile();
        assertTrue(foo.distance(foobar) == bar.distance(foobar));

        foobar.add("foo");
        assertTrue( foo.distance(foobar) < bar.distance(foobar));

        foobar.add("bar");
        assertTrue(foo.distance(foobar) == bar.distance(foobar));
    }

}
