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
 * Parsers should throw this exception when they encounter
 * a file format that they do not support.  This should only happen
 * when we're not able to differentiate versions by the mime.  For example,
 * At the time of this writing, "application/wordperfect" covers all versions
 * of the wordperfect format; however, the parser only handles 6.x.
 * <p/>
 * Whenever possible/convenient, it is better to distinguish file formats by mime
 * so that unsupported formats will be handled by the
 * {@link org.apache.tika.parser.EmptyParser}.
 * However, if we can't differentiate by mime or we need to rely on the parser
 * to distinguish the versions (in the case that magic can't distinguish),
 * this exception should be thrown.
 */
public class UnsupportedFormatException extends TikaException {

    public UnsupportedFormatException(String msg) {
        super(msg);
    }
}
