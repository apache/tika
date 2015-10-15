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
package org.apache.tika.utils;

import static org.junit.Assert.*;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

import org.apache.tika.config.TikaConfig;
import org.apache.tika.parser.ParseContext;
import org.junit.Before;
import org.junit.Test;

public class ConcurrentUtilsTest {

    @Test
    public void testExecuteThread() throws Exception {
        ParseContext context = new ParseContext();
        Future result = ConcurrentUtils.execute(context, new Runnable() {
            
            @Override
            public void run() {
                //Do nothing
                
            }
        });
        
        assertNull(result.get());
    }
    
    @Test
    public void testExecuteExecutor() throws Exception {
        TikaConfig config = TikaConfig.getDefaultConfig();
        ParseContext context = new ParseContext();
        context.set(ExecutorService.class, config.getExecutorService());
        Future result = ConcurrentUtils.execute(context, new Runnable() {
            
            @Override
            public void run() {
                //Do nothing
                
            }
        });
        
        assertNull(result.get());
    }

}
