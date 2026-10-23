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
package org.apache.tika.parser.pages;

/**
 * Where a page's text comes from: the document's own text, OCR of the rendered page, both,
 * the per-page verdict, or nowhere. The {@code "text"} of {@link PagesConfig}.
 *
 * @since Apache Tika 4.1
 */
public enum TextPolicy {
    /** The document's own text only; never OCR. */
    EXTRACT,
    /** The document's own text, OCR where the verdict says the page needs it. */
    AUTO,
    /** Both, every page. */
    EXTRACT_AND_OCR,
    /** OCR only; the document's own text is not read. */
    OCR,
    /** No text from any source; pages are still rendered for annotators, inference and emission. */
    NONE;

    /** Whether a page may be offered to a text recognizer under this policy. */
    public boolean ocrs() {
        return this == AUTO || this == OCR || this == EXTRACT_AND_OCR;
    }
}
