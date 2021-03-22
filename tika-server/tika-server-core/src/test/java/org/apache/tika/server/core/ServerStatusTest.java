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
package org.apache.tika.server.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.util.Map;
import java.util.Random;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.Test;

public class ServerStatusTest {

    @Test(expected = IllegalArgumentException.class)
    public void testBadId() throws Exception {
        ServerStatus status = new ServerStatus("", 0);
        status.complete(2);
    }

    @Test(timeout = 60000)
    public void testBasicMultiThreading() throws Exception {
        //make sure that synchronization is basically working
        int numThreads = 10;
        int filesToProcess = 20;
        ExecutorService service = Executors.newFixedThreadPool(numThreads);
        ExecutorCompletionService<Integer> completionService =
                new ExecutorCompletionService<>(service);
        ServerStatus serverStatus = new ServerStatus("", 0);
        for (int i = 0; i < numThreads; i++) {
            completionService.submit(new MockTask(serverStatus, filesToProcess));
        }
        int finished = 0;
        int totalProcessed = 0;
        while (finished < numThreads) {
            Future<Integer> future = completionService.take();
            if (future != null) {
                finished++;
                Integer completed = future.get();
                totalProcessed += completed;
            }
        }
        assertEquals(numThreads * filesToProcess, totalProcessed);
        assertEquals(0, serverStatus.getTasks().size());
        assertEquals(totalProcessed, serverStatus.getFilesProcessed());

    }

    private class MockTask implements Callable<Integer> {
        private final ServerStatus serverStatus;
        private final int filesToProcess;
        Random r = new Random();

        public MockTask(ServerStatus serverStatus, int filesToProcess) {
            this.serverStatus = serverStatus;
            this.filesToProcess = filesToProcess;
        }

        @Override
        public Integer call() throws Exception {
            int processed = 0;
            for (int i = 0; i < filesToProcess; i++) {
                sleepRandom(200);
                long taskId = serverStatus.start(ServerStatus.TASK.PARSE, null);
                sleepRandom(100);
                serverStatus.complete(taskId);
                processed++;
                serverStatus.getStatus();
                sleepRandom(10);
                serverStatus.setStatus(ServerStatus.STATUS.OPERATING);
                sleepRandom(20);
                Map<Long, TaskStatus> tasks = serverStatus.getTasks();
                assertNotNull(tasks);
            }
            return processed;
        }

        private void sleepRandom(int millis) throws InterruptedException {
            int sleep = r.nextInt(millis);
            Thread.sleep(sleep);
        }
    }
}
