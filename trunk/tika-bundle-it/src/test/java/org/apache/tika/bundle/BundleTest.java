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
package org.apache.tika.bundle;

import java.io.ByteArrayInputStream;

import junit.framework.Assert;

import org.apache.tika.Tika;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.ops4j.pax.exam.junit.MavenConfiguredJUnit4TestRunner;

@RunWith(MavenConfiguredJUnit4TestRunner.class)
public class BundleTest {

    @Test
    public void testTikaBundle() throws Exception {
        Tika tika = new Tika();

        // Simple type detection
        Assert.assertEquals("text/plain", tika.detect("test.txt"));
        Assert.assertEquals("application/pdf", tika.detect("test.pdf"));

        // Simple text extrction
        byte[] data = "Hello, World!".getBytes("UTF-8");
        Assert.assertEquals(
                "Hello, World!",
                tika.parseToString(new ByteArrayInputStream(data)).trim());
    }

}
