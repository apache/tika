package org.apache.tika.util;

import static org.junit.Assert.assertEquals;

import org.apache.tika.TikaTest;
import org.junit.Test;

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
public class TikaExceptionFilterTest extends TikaTest {

    @Test
    public void simpleNPETest() {
        TikaExceptionFilter filter = new TikaExceptionFilter();
        Throwable t = null;
        try {
            getXML("null_pointer.xml");
        } catch (Throwable t2) {
            assertContains("Unexpected RuntimeException", t2.getMessage());
            t = filter.filter(t2);
        }
        assertEquals("another null pointer exception", t.getMessage());
    }

}
