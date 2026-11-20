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
 * Thrown when writing a temp file would take a task past its
 * {@link org.apache.tika.io.TempBytesBudget}. It ends the task and reaches the top-level
 * caller as itself. Unchecked, so that the many {@code catch (IOException)} blocks
 * between a temp write and that caller cannot take it for a bad document and go on.
 *
 * @since Apache Tika 4.3
 */
public class TempLimitReachedException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public TempLimitReachedException(long maxBytes) {
        super("Temp file limit of " + maxBytes + " bytes reached; see temp-limits.maxTempBytes");
    }
}
