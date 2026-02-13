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
package org.apache.tika.parser.microsoft.libpst;

import java.io.Serializable;

import org.apache.tika.exception.TikaConfigException;
import org.apache.tika.utils.StringUtils;

public class LibPstParserConfig implements Serializable {

    private long timeoutSeconds = 600;
    /**
     * In initial tests, setting this to true resulted in more emails
     * being extracted. It did dramatically slow down processing time. :(
     */
    private boolean isDebug = true;

    /**
     * Should readpst also output msg files for processing.
     * In an initial test, not as many attachments were extracted from msg files.
     * Not yet clear if that is a POI limitation or a problem with libpst
     */
    private boolean processEmailAsMsg = true;

    private boolean includeDeleted = true;

    /**
     * max emails to process. Will process everything if this value is < 0
     */
    private int maxEmails = -1;

    /**
     * This should include the path up to but not including 'readpst', e.g. "C:\my_bin" where
     * readpst is at "C:\my_bin\readpst"
     */
    private String readPstPath = "";

    public long getTimeoutSeconds() {
        return timeoutSeconds;
    }

    public void setTimeoutSeconds(long timeoutSeconds) {
        this.timeoutSeconds = timeoutSeconds;
    }

    public boolean isDebug() {
        return isDebug;
    }

    public void setDebug(boolean debug) {
        isDebug = debug;
    }

    public boolean isProcessEmailAsMsg() {
        return processEmailAsMsg;
    }

    public void setProcessEmailAsMsg(boolean processEmailAsMsg) {
        this.processEmailAsMsg = processEmailAsMsg;
    }

    public boolean isIncludeDeleted() {
        return includeDeleted;
    }

    public void setIncludeDeleted(boolean includeDeleted) {
        this.includeDeleted = includeDeleted;
    }

    public int getMaxEmails() {
        return maxEmails;
    }

    public void setMaxEmails(int maxEmails) {
        this.maxEmails = maxEmails;
    }

    public String getReadPstPath() {
        return readPstPath;
    }

    public void setReadPstPath(String readPstPath) throws TikaConfigException {
        this.readPstPath = readPstPath;
    }

    /**
     * RuntimeConfig blocks modification of security-sensitive path fields at runtime.
     * When a config is obtained from ParseContext (i.e. user-provided at parse time),
     * it should be deserialized as a RuntimeConfig to prevent path injection.
     * <p>
     * This class is deserialized by ConfigDeserializer (in tika-serialization) which uses
     * Jackson to populate fields via setters. If the JSON contains any path fields, the
     * overridden setters will throw TikaConfigException.
     */
    public static class RuntimeConfig extends LibPstParserConfig {

        public RuntimeConfig() {
            super();
        }

        @Override
        public void setReadPstPath(String readPstPath) throws TikaConfigException {
            if (!StringUtils.isBlank(readPstPath)) {
                throw new TikaConfigException(
                        "Cannot modify readPstPath at runtime. " +
                                "Paths must be configured at parser initialization time.");
            }
        }
    }
}
