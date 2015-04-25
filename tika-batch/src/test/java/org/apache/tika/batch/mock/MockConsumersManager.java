package org.apache.tika.batch.mock;

import org.apache.tika.batch.ConsumersManager;

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

public class MockConsumersManager extends ConsumersManager {

    private final long HANG_MS = 30000;

    private final ConsumersManager wrapped;
    private final boolean hangOnInit;
    private final boolean hangOnClose;

    public MockConsumersManager(ConsumersManager wrapped, boolean hangOnInit,
                                boolean hangOnClose) {
        super(wrapped.getConsumers());
        this.wrapped = wrapped;
        this.hangOnInit = hangOnInit;
        this.hangOnClose = hangOnClose;
    }


    @Override
    public void init() {
        if (hangOnInit) {
            //interruptible light hang
            try {
                Thread.sleep(HANG_MS);
            } catch (InterruptedException e) {
                return;
            }
            return;
        }
        super.init();
    }

    @Override
    public void shutdown() {
        if (hangOnClose) {
            //interruptible light hang
            try {
                Thread.sleep(HANG_MS);
            } catch (InterruptedException e) {
                return;
            }
            return;
        }
        super.shutdown();
    }

    @Override
    public long getConsumersManagerMaxMillis() {
        return wrapped.getConsumersManagerMaxMillis();
    }

    @Override
    public void setConsumersManagerMaxMillis(long millis) {
        wrapped.setConsumersManagerMaxMillis(millis);
    }

}
