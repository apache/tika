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
package org.apache.tika.parser.mp4;

import static org.junit.Assert.assertEquals;

import org.apache.tika.TikaTest;
import org.apache.tika.metadata.Metadata;
import org.junit.Test;

public class ISO6709ExtractorTest {

    @Test
    public void test() throws Exception {
        Metadata m = new Metadata();
        ISO6709Extractor ex = new ISO6709Extractor();
        ex.extract("+40.20361-075.00417/", m);
        assertCorrect(m);

        m = new Metadata();
        ex.extract("+4012.22-07500.25/", m);
        assertCorrect(m);

        m = new Metadata();
        ex.extract("+401213.1-0750015.1/", m);
        assertCorrect(m);

    }

    private void assertCorrect(Metadata m) {
        double lat = Double.parseDouble(m.get(Metadata.LATITUDE));
        double lng = Double.parseDouble(m.get(Metadata.LONGITUDE));
        assertEquals(40.20361, lat, 0.0001);
        assertEquals(-75.00417, lng, 0.0001);
    }
}
