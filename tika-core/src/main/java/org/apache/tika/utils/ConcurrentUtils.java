/**
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

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;

import org.apache.tika.parser.ParseContext;

/**
 * Utility Class for Concurrency in Tika
 *
 * @since Apache Tika 1.11
 */
public class ConcurrentUtils {
    
    /**
     * 
     * Execute a runnable using an ExecutorService from the ParseContext if possible.
     * Otherwise fallback to individual threads.
     * 
     * @param context
     * @param runnable
     * @return
     */
    public static Future execute(ParseContext context, Runnable runnable) {
        
        Future future = null;
        ExecutorService executorService = context.get(ExecutorService.class);
        if(executorService == null) {
            FutureTask task = new FutureTask<>(runnable, null);
            Thread thread = new Thread(task, "Tika Thread");
            thread.start();
            future = task;
        }
        else {
            future = executorService.submit(runnable);
        }
        
        return future;
    }
}
