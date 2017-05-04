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
package org.apache.tika.config;

import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.math.BigInteger;
import java.net.URI;
import java.net.URL;
import java.util.HashMap;
import java.util.HashSet;

import static org.junit.Assert.*;

public class ParamTest {

    @Test
    public void testSaveAndLoad() throws Exception {

        Object objects [] =  {
                Integer.MAX_VALUE,
                2.5f,
                4000.57576,
                true,
                false,
                Long.MAX_VALUE,
                "Hello this is a boring string",
                new URL("http://apache.org"),
                new URI("tika://org.apache.tika.ner.parser?impl=xyz"),
                new BigInteger(Long.MAX_VALUE + "").add(new BigInteger(Long.MAX_VALUE + "")),
                new File("."),
        };

        for (Object object : objects) {
            String name = "name" + System.currentTimeMillis();
            Param<?> param = new Param<>(name, object);
            ByteArrayOutputStream stream = new ByteArrayOutputStream();
            param.save(stream);
            ByteArrayInputStream inStream = new ByteArrayInputStream(stream.toByteArray());
            stream.close();
            inStream.close();
            Param<?> loaded = Param.load(inStream);
            assertEquals(param.getName(), loaded.getName());
            assertEquals(param.getTypeString(), loaded.getTypeString());
            assertEquals(param.getType(), loaded.getType());
            assertEquals(param.getValue(), loaded.getValue());

            assertEquals(loaded.getValue(), object);
            assertEquals(loaded.getName(), name);
            assertEquals(loaded.getType(), object.getClass());
        }
    }

}