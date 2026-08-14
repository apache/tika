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
package org.apache.tika.pipes.core.extractor;

/**
 * Running total of bytes {@link UnpackExtractor} has written out for the current request,
 * checked against {@link UnpackConfig#getMaxUnpackBytesOrUnlimited()}.
 * <p>
 * One instance per request: create it alongside the request's {@link org.apache.tika.extractor.UnpackHandler}
 * and bind it into the request's {@link org.apache.tika.parser.ParseContext}. Not thread-safe.
 */
public class UnpackedByteCount {

    private long value;

    public void add(long n) {
        value += n;
    }

    public long get() {
        return value;
    }
}
