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

import org.apache.cxf.jaxrs.utils.JAXRSUtils;
import org.apache.cxf.message.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * One line, no stack trace, per request refused or cut off by a transfer limit (413, 408,
 * 429, aborted response): its own logger, so these client-caused events can be routed or
 * silenced apart from server errors.
 */
public final class TransferLimitLog {

    private static final Logger LOG = LoggerFactory.getLogger(TransferLimitLog.class);
    private static final String LOGGED = TransferLimitLog.class.getName() + ".logged";

    private TransferLimitLog() {
    }

    static void log(String reason) {
        Message current = JAXRSUtils.getCurrentMessage();
        // while the response is written, the current message is the outbound one
        log(current == null || current.getExchange() == null
                ? current : current.getExchange().getInMessage(), reason);
    }

    /**
     * For a thread with no current CXF message: {@code request} is the inbound one, or null.
     * Once per request, as CXF may map one failure twice (multipart); returns whether it logged.
     */
    static boolean log(Message request, String reason) {
        if (!LOG.isInfoEnabled()) {
            return false;
        }
        if (request == null) {
            LOG.info("{}", reason);
            return true;
        }
        // the exchange is a ConcurrentHashMap, so this holds across threads too
        if (request.getExchange() != null && request.getExchange().put(LOGGED, Boolean.TRUE) != null) {
            return false;
        }
        LOG.info("{} {}: {}", request.get(Message.HTTP_REQUEST_METHOD),
                request.get(Message.REQUEST_URI), reason);
        return true;
    }
}
