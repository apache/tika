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

import java.io.IOException;
import java.util.List;

import org.apache.tika.exception.TikaConfigException;
import org.apache.tika.exception.TikaException;
import org.apache.tika.parser.ParseContext;

/**
 * What a binding asks of its engine, named in the binding's {@code "tasks"}: one call per
 * unit for {@code embed}, all units in one call for a document-level task. The task owns
 * its output shape and writes it onto each unit's target (or parent).
 */
public interface InferenceTask {

    /** Rejects an engine this task cannot use; called once at config load. */
    default void validate(InferenceBinding binding, Engine engine) throws TikaConfigException {
    }

    void run(InferenceBinding binding, List<InferenceUnit> units, Engine engine,
             ParseContext context) throws IOException, TikaException;
}
