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
package org.apache.tika.exception;

/**
 * Tika Config Exception is an exception to occur when there is an error
 * in Tika config file and/or one or more of the parsers failed to initialize
 * from that erroneous config.
 *
 * @since Apache Tika 1.14
 */
public class TikaConfigException extends TikaException {

    /**
     * Creates an instance of exception
     * @param msg message
     */
    public TikaConfigException(String msg) {
        super(msg);
    }

    public TikaConfigException(String msg, Throwable cause) {
        super(msg, cause);
    }
}
