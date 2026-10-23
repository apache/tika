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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.cxf.message.ExchangeImpl;
import org.apache.cxf.message.Message;
import org.apache.cxf.message.MessageImpl;
import org.junit.jupiter.api.Test;

public class TransferLimitLogTest {

    /** CXF maps a multipart 413 twice; the second mapping must not log again. */
    @Test
    public void testOncePerRequest() {
        Message first = request();
        assertTrue(TransferLimitLog.log(first, "too large"));
        assertFalse(TransferLimitLog.log(first, "too large"));
        assertTrue(TransferLimitLog.log(request(), "too large"));
    }

    private static Message request() {
        Message message = new MessageImpl();
        ExchangeImpl exchange = new ExchangeImpl();
        exchange.setInMessage(message);
        message.put(Message.HTTP_REQUEST_METHOD, "POST");
        message.put(Message.REQUEST_URI, "/meta/form");
        return message;
    }
}
