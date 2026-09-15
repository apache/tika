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
package org.apache.tika.config.loader;

import java.util.List;

import org.apache.tika.annotation.TikaComponent;
import org.apache.tika.exception.TikaConfigException;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.inference.Engine;
import org.apache.tika.parser.inference.InferenceBinding;
import org.apache.tika.parser.inference.InferenceTask;
import org.apache.tika.parser.inference.InferenceUnit;

/** A task that accepts only a {@link TestEngine} and records nothing. */
@TikaComponent(name = "test-task", spi = false)
public class TestTask implements InferenceTask {

    @Override
    public void validate(InferenceBinding binding, Engine engine) throws TikaConfigException {
        if (!(engine instanceof TestEngine)) {
            throw new TikaConfigException("test-task needs a test-engine");
        }
    }

    @Override
    public void run(InferenceBinding binding, List<InferenceUnit> units, Engine engine,
                    ParseContext context) {
    }
}
