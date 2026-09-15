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
package org.apache.tika.parser.mock;

import java.util.List;

import org.apache.tika.annotation.TikaComponent;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.inference.Engine;
import org.apache.tika.parser.inference.InferenceBinding;
import org.apache.tika.parser.inference.InferenceTask;
import org.apache.tika.parser.inference.InferenceUnit;

/**
 * Fixture for {@code "inference"} tasks: nameable as {@code mock-task}, marks each unit's
 * parent (or the unit itself) with the binding id and the number of units it saw at once.
 */
@TikaComponent(name = "mock-task", spi = false)
public class MockTask implements InferenceTask {

    public static final String MARKER_KEY = "mock-task";
    public static final String UNITS_KEY = "mock-task-units";

    @Override
    public void run(InferenceBinding binding, List<InferenceUnit> units, Engine engine,
                    ParseContext context) {
        for (InferenceUnit unit : units) {
            Metadata onto = unit.getParent() != null ? unit.getParent() : unit.getTarget();
            onto.set(MARKER_KEY, binding.getId());
            onto.set(UNITS_KEY, Integer.toString(units.size()));
        }
    }
}
