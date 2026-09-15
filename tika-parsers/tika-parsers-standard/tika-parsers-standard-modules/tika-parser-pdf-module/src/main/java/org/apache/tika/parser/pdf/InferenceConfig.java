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
package org.apache.tika.parser.pdf;

import java.io.Serializable;
import java.util.List;

/** What the PDF parser releases to the inference bindings; per request like every PDF setting. */
public class InferenceConfig implements Serializable {

    private static final long serialVersionUID = 1L;

    /** {@code TEXT}: the extracted text. {@code PAGES}: every page rendered, when a binding wants pages. */
    public enum Input {
        TEXT, PAGES
    }

    private List<Input> input = List.of(Input.TEXT);

    public List<Input> getInput() {
        return input;
    }

    public void setInput(List<Input> input) {
        this.input = input == null ? List.of() : List.copyOf(input);
    }
}
