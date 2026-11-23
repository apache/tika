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
package org.apache.tika.pipes.core.extractor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.Closeable;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import org.apache.tika.exception.TempLimitReachedException;
import org.apache.tika.io.TempBytesBudget;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.pipes.api.emitter.EmitKey;

/** TIKA-5075: the files UNPACK stages on disk count against the task's temp budget. */
public class UnpackStagingTempBudgetTest {

    private static final int MAX_TEMP_BYTES = 16;

    private static AbstractUnpackHandler handler(boolean frictionless, TempBytesBudget budget)
            throws Exception {
        EmitKey emitKey = new EmitKey("e", "k");
        UnpackConfig unpackConfig = new UnpackConfig();
        AbstractUnpackHandler handler = frictionless
                ? new FrictionlessUnpackHandler(emitKey, unpackConfig)
                : new TempFileUnpackHandler(emitKey, unpackConfig);
        handler.setTempBudget(budget);
        return handler;
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    public void stagedFilesAreChargedAndGivenBack(boolean frictionless) throws Exception {
        TempBytesBudget budget = new TempBytesBudget(MAX_TEMP_BYTES);
        AbstractUnpackHandler handler = handler(frictionless, budget);
        try {
            handler.add(1, new Metadata(), new ByteArrayInputStream(new byte[6]));
            handler.add(2, new Metadata(), new ByteArrayInputStream(new byte[10]));
            assertEquals(MAX_TEMP_BYTES, budget.getReservedBytes());
        } finally {
            ((Closeable) handler).close();
        }
        assertEquals(0, budget.getReservedBytes());
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    public void aFileThatDoesNotFitFailsTheTask(boolean frictionless) throws Exception {
        TempBytesBudget budget = new TempBytesBudget(MAX_TEMP_BYTES);
        AbstractUnpackHandler handler = handler(frictionless, budget);
        try {
            handler.add(1, new Metadata(), new ByteArrayInputStream(new byte[6]));
            assertThrows(TempLimitReachedException.class, () -> handler.add(2, new Metadata(),
                    new ByteArrayInputStream(new byte[MAX_TEMP_BYTES])));
            assertTrue(budget.isLimitReached());
        } finally {
            ((Closeable) handler).close();
        }
    }
}
