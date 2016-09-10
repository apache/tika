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

import static org.junit.Assert.*;

import org.apache.tika.detect.Detector;
import org.apache.tika.parser.DummyProxyParser;
import org.apache.tika.parser.Parser;
import org.junit.Before;
import org.junit.Test;

public class ServiceLoaderTest {

    
    @Before
    public void setUp(){
        ServiceLoader.addService(new Object(), new DummyProxyParser(), 1);
    }
    @Test
    public void testDynamicClass() throws ClassNotFoundException {
        ServiceLoader.getDynamicServiceClass("org.apache.tika.parser.DummyProxyParser");
    }
    
    @Test
    public void testServiceClass() throws ClassNotFoundException {
        ClassLoader fakeClassLoader = new ClassLoader(null) {
        };
        ServiceLoader loader = new ServiceLoader(fakeClassLoader, LoadErrorHandler.IGNORE, true);
        loader.getServiceClass(Parser.class, "org.apache.tika.parser.DummyProxyParser");
    }
    
    @Test
    public void testNonDynamicServiceLoader() {
        ClassLoader fakeClassLoader = new ClassLoader(null) {
        };
        ServiceLoader loader = new ServiceLoader(fakeClassLoader);
        try {
            loader.getServiceClass(Parser.class, "org.apache.tika.parser.DummyProxyParser");
            fail("Non Dynamic Classloading.  Should throw Exception");
        } catch (ClassNotFoundException e) {
            //Should throw Exception
        }
    }
    
    @Test
    public void testNonMatchingInterface() {
        ClassLoader fakeClassLoader = new ClassLoader() {
        };
        ServiceLoader loader = new ServiceLoader(fakeClassLoader, LoadErrorHandler.IGNORE, true);
        try {
            loader.getServiceClass(Detector.class, "org.apache.tika.parser.DummyProxyParser");
            fail("Interface does not match Implementation.  Should throw Exception.");
        } catch (ClassNotFoundException e) {
            //Should throw Exception
        }
    }

}
