/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.tika.config;

import static org.junit.Assert.assertEquals;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;

import org.junit.Test;

import org.apache.tika.exception.TikaException;

public class MockConfigTest {

    @Test
    public void testBasic() throws Exception {
        MockConfig config;
        try (InputStream is = getClass().getResourceAsStream("mockConfig.xml")) {
            config = new MockConfig(is);
        }
        assertEquals("hello-world", config.getMyString());
        assertEquals(2, config.mappings.size());
        assertEquals(3.14159, config.getMyDouble(), 0.1);
        assertEquals(2, config.getMyInt());
        assertEquals(2, config.getMyStrings().size());
        assertEquals("one", config.getMyStrings().get(0));
        assertEquals("two", config.getMyStrings().get(1));
    }


    public class MockConfig extends ConfigBase {

        private Map<String, String> mappings;
        private Map<String, Integer> mappedIntegers;
        private List<String> myStrings;
        private double myDouble;
        private int myInt;
        private String myString;
        private float myFloat;

        protected MockConfig(InputStream is) throws TikaException, IOException {
            configure("mockConfig", is);
        }

        public Map<String, String> getMappings() {
            return mappings;
        }

        public void setMappings(Map<String, String> mappings) {
            this.mappings = mappings;
        }

        public Map<String, Integer> getMappedIntegers() {
            return mappedIntegers;
        }

        public void setMappedIntegers(Map<String, Integer> mappedIntegers) {
            this.mappedIntegers = mappedIntegers;
        }

        public List<String> getMyStrings() {
            return myStrings;
        }

        public void setMyStrings(List<String> myStrings) {
            this.myStrings = myStrings;
        }

        public double getMyDouble() {
            return myDouble;
        }

        public void setMyDouble(double myDouble) {
            this.myDouble = myDouble;
        }

        public int getMyInt() {
            return myInt;
        }

        public void setMyInt(int myInt) {
            this.myInt = myInt;
        }

        public String getMyString() {
            return myString;
        }

        public void setMyString(String myString) {
            this.myString = myString;
        }

        public float getMyFloat() {
            return myFloat;
        }

        public void setMyFloat(float myFloat) {
            this.myFloat = myFloat;
        }
    }
}
