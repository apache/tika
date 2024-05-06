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

import java.io.IOException;

/*
 * Thrown when fetcher or similar has a maxLength set, but the
 * underlying file is too long.
 */
public class FileTooLongException extends IOException {

    public FileTooLongException(String msg) {
        super(msg);
    }

    public FileTooLongException(long length, long maxLength) {
        super(msg(length, maxLength));
    }

    private static String msg(long length, long maxLength) {
        return "File is "
                + length
                + " bytes, but "
                + maxLength
                + " is the maximum length allowed.  You can modify maxLength via "
                + "the setter on the fetcher.";
    }
}
