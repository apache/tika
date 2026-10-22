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
package org.apache.tika.config;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.Test;

import org.apache.tika.test.OperatorOnlyAudit;

/**
 * Every config setter on the tika-app classpath that looks like it reaches the host carries
 * {@code @OperatorOnly}, so per-request JSON cannot set it. A hit here is either a real hole
 * (annotate the setter) or a reviewed false positive (annotate it anyway, or list it below
 * with the reason).
 */
public class OperatorOnlyAuditTest {

    /** Config that is loaded only from the operator's files, never resolved per request. */
    private static final List<String> NOT_PER_REQUEST = List.of(
            "org.apache.tika.server.",
            "org.apache.tika.pipes.",
            "org.apache.tika.cli.",
            "org.apache.tika.eval.");

    private static final List<String> REVIEWED = List.of();

    @Test
    public void testSensitiveSettersAreOperatorOnly() throws Exception {
        List<String> violations =
                OperatorOnlyAudit.unannotatedSensitiveSetters(NOT_PER_REQUEST, REVIEWED);
        assertEquals(List.of(), violations,
                "setters that take a host-side value but allow per-request override");
    }
}
