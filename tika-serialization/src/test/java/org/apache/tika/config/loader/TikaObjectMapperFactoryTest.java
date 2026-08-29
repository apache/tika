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
package org.apache.tika.config.loader;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import org.junit.jupiter.api.Test;

public class TikaObjectMapperFactoryTest {

    enum Strategy {
        NO_OCR, AUTO
    }

    static class Config {
        public Strategy strategy;
    }

    @Test
    public void testEnumValuesAreCaseInsensitive() throws Exception {
        ObjectMapper mapper = TikaObjectMapperFactory.getMapper();
        assertEquals(Strategy.NO_OCR,
                mapper.readValue("{\"strategy\":\"no_ocr\"}", Config.class).strategy);
        assertEquals(Strategy.NO_OCR,
                mapper.readValue("{\"strategy\":\"NO_OCR\"}", Config.class).strategy);
        assertEquals(Strategy.AUTO,
                mapper.readValue("{\"strategy\":\"Auto\"}", Config.class).strategy);
    }

    @Test
    public void testUnknownEnumValueStillFails() {
        ObjectMapper mapper = TikaObjectMapperFactory.getMapper();
        assertThrows(InvalidFormatException.class,
                () -> mapper.readValue("{\"strategy\":\"nope\"}", Config.class));
    }
}
