/*
 * Licensed to the Apache Software Foundation (ASF) under one or more contributor license
 * agreements. See the NOTICE file distributed with this work for additional information regarding
 * copyright ownership. The ASF licenses this file to You under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License. You may obtain a
 * copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package org.apache.tika.extractor.microsoft;

import java.io.IOException;
import java.io.OutputStream;
import java.util.concurrent.atomic.AtomicLong;
import org.apache.tika.extractor.EmbeddedStreamTranslator;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.mime.MediaType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PSTEmailStreamTranslator implements EmbeddedStreamTranslator {
    private static final String MIME_TYPE =
                    MediaType.application("x-tika-pst-mail-item").toString();

    private static final Logger LOG = LoggerFactory.getLogger(PSTEmailStreamTranslator.class);
    private static final AtomicLong EMAIL_ITEMS = new AtomicLong(0);
    private static final long LOG_EVERY_X_ITEMS = 100;

    @Override
    public boolean shouldTranslate(TikaInputStream tis, Metadata metadata) throws IOException {
        return MIME_TYPE.equals(metadata.get(TikaCoreProperties.CONTENT_TYPE_PARSER_OVERRIDE))
                        || MIME_TYPE.equals(metadata.get(Metadata.CONTENT_TYPE));
    }

    @Override
    public void translate(TikaInputStream tis, Metadata metadata, OutputStream os)
                    throws IOException {
        if (!shouldTranslate(tis, metadata)) {
            return;
        }
        if (EMAIL_ITEMS.getAndIncrement() % LOG_EVERY_X_ITEMS == 0) {
            LOG.warn("Translating pst email objects to .eml or .msg is not yet supported. "
                            + "Please open a ticket on our JIRA or a pull request on Github.");
        }
    }
}
