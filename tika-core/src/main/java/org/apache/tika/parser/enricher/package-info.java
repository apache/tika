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
/**
 * Engines a container parser invokes on bytes it has already parsed: text recognizers (OCR,
 * transcription) and annotators. Experimental in 4.1: the contract is one image per call,
 * written into the caller's body as it runs; 4.2 batches recognition per document. The intent
 * is additive: an engine that takes one image at a time keeps working unchanged, and one whose
 * service batches opts in by declaring a batch size and implementing the list call 4.2 adds.
 * Until then these interfaces may change in a minor release without a deprecation cycle. The
 * {@code "engines"} and {@code "text-recognizers"} configuration that names an engine is stable.
 */
package org.apache.tika.parser.enricher;
