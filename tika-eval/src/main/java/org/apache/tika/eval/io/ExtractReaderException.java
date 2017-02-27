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
package org.apache.tika.eval.io;

import java.io.IOException;

/**
 * Exception when trying to read extract
 */
public class ExtractReaderException extends IOException {

    public enum TYPE {
        //what do you see when you look at the extract file
        NO_EXTRACT_FILE,
        ZERO_BYTE_EXTRACT_FILE,
        IO_EXCEPTION,
        EXTRACT_PARSE_EXCEPTION,
        EXTRACT_FILE_TOO_SHORT,
        EXTRACT_FILE_TOO_LONG,
        INCORRECT_EXTRACT_FILE_SUFFIX;//extract file must have suffix of .json or .txt,
        // optionally followed by gzip, zip or bz2
    }

    private final TYPE type;

    public ExtractReaderException(TYPE exceptionType) {
        this.type = exceptionType;
    }

    public TYPE getType() {
        return type;
    }

}
