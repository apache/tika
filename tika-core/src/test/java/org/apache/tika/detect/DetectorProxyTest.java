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
package org.apache.tika.detect;

import static org.junit.Assert.*;

import java.io.IOException;

import org.apache.tika.config.LoadErrorHandler;
import org.apache.tika.mime.MediaType;
import org.junit.Test;

public class DetectorProxyTest 
{
    @Test
    public void testDetectorProxyExists() throws IOException 
    {
        Detector dummyDetector = new DetectorProxy("org.apache.tika.detect.DummyProxyDetector",
                LoadErrorHandler.IGNORE);
        
        MediaType result = dummyDetector.detect(null, null);
        
        assertEquals("Detector being proxied exists so result should not be null", 
                MediaType.TEXT_PLAIN, result );
        
    }
    
    @Test
    public void testParserProxyNotExists() throws IOException 
    {
        Detector dummyDetector = new DetectorProxy("org.apache.tika.detect.DoesNotExist",
                LoadErrorHandler.IGNORE);
        
        MediaType result = dummyDetector.detect(null, null);
        
        assertNull("Detector being proxied does not exists so result should be null", result );
        
    }

}
