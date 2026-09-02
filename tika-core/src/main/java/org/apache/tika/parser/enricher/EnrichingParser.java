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
package org.apache.tika.parser.enricher;

/**
 * A parser that invokes content enrichers (e.g. OCR on its images or rendered pages).
 * The configured {@link CompositeContentEnricher} is injected at load time, the way
 * {@link org.apache.tika.parser.RenderingParser} receives its renderer.
 *
 * @since Apache Tika 4.1
 */
public interface EnrichingParser {

    void setContentEnrichers(CompositeContentEnricher contentEnrichers);
}
