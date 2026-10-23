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

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import org.apache.tika.annotation.TikaComponent;

/**
 * Per-request choice among the configured bindings, under {@code "inference"} in
 * {@code parse-context}: {@code enabled: false} runs none; {@code bindings} names the ones
 * that run, every enabled binding when absent. Names only, so it is wire-safe; a name not
 * in the configured list fails the request.
 */
@TikaComponent(name = "inference", spi = false)
public class InferenceSelection implements Serializable {

    private static final long serialVersionUID = 1L;

    private boolean enabled = true;
    private List<String> bindings = new ArrayList<>();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public List<String> getBindings() {
        return bindings;
    }

    public void setBindings(List<String> bindings) {
        this.bindings = bindings == null ? new ArrayList<>() : new ArrayList<>(bindings);
    }
}
