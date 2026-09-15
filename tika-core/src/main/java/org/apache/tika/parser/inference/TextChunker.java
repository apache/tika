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
package org.apache.tika.parser.inference;

import java.util.List;

/**
 * Cuts a document's text into the pieces a {@link InputKind#TEXT} binding sends to its engine.
 * Configured per binding under {@code "chunker"}.
 *
 * @since Apache Tika 4.1
 */
public interface TextChunker {

    /** Half-open {@code [start, end)} character ranges of the chunks, in document order. */
    List<int[]> spans(String text);
}
