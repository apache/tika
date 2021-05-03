/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.tika.exception;

/**
 * Exception thrown by the AutoDetectParser when a file contains zero-bytes.
 */
public class ZeroByteFileException extends TikaException {


    /**
     * If this is in the {@link org.apache.tika.parser.ParseContext}, the
     * {@link org.apache.tika.parser.AutoDetectParser} and the
     * {@link org.apache.tika.parser.RecursiveParserWrapper} will
     * ignore embedded files with zero-byte length inputstreams
     */
    public static IgnoreZeroByteFileException IGNORE_ZERO_BYTE_FILE_EXCEPTION =
            new IgnoreZeroByteFileException();

    //If this is in the parse context, the AutoDetectParser and the
    //RecursiveParserWrapper should ignore zero byte files
    //and not throw a Zero}
    public ZeroByteFileException(String msg) {
        super(msg);
    }

    public static class IgnoreZeroByteFileException {
    }
}
